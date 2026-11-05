/*
 * Kreate
 * Copyright (C) 2026 KnightHat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.knighthat.kreate3.provider.opensubsonic

import java.net.Socket
import java.security.MessageDigest
import java.security.cert.CertPathValidator
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

/**
 * A self-signed certificate the user trusts for [host].
 *
 * @property fingerprint SHA-256 of the certificate, as uppercase hex pairs split by `:`
 */
data class CertificatePin(val host: String, val fingerprint: String)

/**
 * The server presented a self-signed certificate other than the one trusted for it. The
 * connection is refused until the user trusts the new one.
 */
class CertificateChangedException(
    val host: String,
    val fingerprint: String,
    val pinnedFingerprint: String,
) : CertificateException("The certificate of $host changed")

/**
 * A self-signed certificate a server presented that nobody trusted yet, for the user to look at.
 *
 * @property subject who the certificate names, like `CN=music.example.com`
 * @property notBefore start of its validity, epoch milliseconds
 * @property notAfter end of its validity, epoch milliseconds
 * @property fingerprint SHA-256 of the certificate, see [sha256]
 */
data class SelfSignedCertificate(
    val host: String,
    val subject: String,
    val notBefore: Long,
    val notAfter: Long,
    val fingerprint: String,
)

/**
 * The server presented a self-signed certificate that isn't trusted, because none was trusted for
 * it yet or self-signed certificates aren't allowed. The connection is refused until the user
 * trusts [certificate].
 */
class UntrustedSelfSignedException(
    val certificate: SelfSignedCertificate,
) : CertificateException("The certificate of ${certificate.host} is self-signed")

/**
 * SHA-256 of the certificate, as uppercase hex pairs split by `:`.
 */
fun X509Certificate.sha256(): String =
    MessageDigest.getInstance("SHA-256").digest(encoded).joinToString(":") { "%02X".format(it) }

/**
 * Whether the certificate signs itself, like the root of a private CA.
 */
internal fun X509Certificate.isSelfSigned(): Boolean =
    subjectX500Principal == issuerX500Principal && runCatching { verify(publicKey) }.isSuccess

/**
 * Trusts what [system] trusts and, while [allowed], a chain that ends in the self-signed
 * certificate the user trusted for the host ([pinned]). Any other chain ending in a self-signed
 * certificate is refused with [UntrustedSelfSignedException] carrying its details, so the user can
 * decide; when another certificate was trusted for the host it is [CertificateChangedException].
 * Nothing is remembered here, only the user's answer is.
 *
 * The chain is still checked against the remembered certificate (signatures and dates), and
 * the host name is checked afterwards by the HTTP client as usual. A chain that does not end in
 * a self-signed certificate, like a public certificate that failed for another reason, is
 * refused as [system] refused it.
 */
internal class SelfSignedTrustManager(
    private val system: X509TrustManager,
    private val allowed: () -> Boolean,
    private val pinned: () -> CertificatePin?,
) : X509ExtendedTrustManager() {

    override fun checkServerTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        socket: Socket?
    ) {
        val host = (socket as? SSLSocket)?.handshakeSession?.peerHost
        check(chain, authType, host) {
            if (system is X509ExtendedTrustManager) {
                system.checkServerTrusted(
                    chain,
                    authType,
                    socket
                )
            } else {
                system.checkServerTrusted(chain, authType)
            }
        }
    }

    override fun checkServerTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        engine: SSLEngine?
    ) {
        check(chain, authType, engine?.peerHost) {
            if (system is X509ExtendedTrustManager) {
                system.checkServerTrusted(
                    chain,
                    authType,
                    engine
                )
            } else {
                system.checkServerTrusted(chain, authType)
            }
        }
    }

    /** Without the host the certificate can't be matched to a remembered one, only [system] decides. */
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) =
        system.checkServerTrusted(chain, authType)

    override fun checkClientTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        socket: Socket?
    ) =
        throw CertificateException("Client certificates are not checked")

    override fun checkClientTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        engine: SSLEngine?
    ) =
        throw CertificateException("Client certificates are not checked")

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
        throw CertificateException("Client certificates are not checked")

    override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers

    /**
     * Trusts [chain] when [systemCheck] does, otherwise when it ends in the self-signed
     * certificate the user trusted for [host].
     */
    fun check(
        chain: Array<X509Certificate>,
        authType: String,
        host: String?,
        systemCheck: () -> Unit
    ) {
        try {
            systemCheck()
            return
        } catch (refused: CertificateException) {
            if (host.isNullOrEmpty() || chain.isEmpty()) throw refused
            val anchor = chain.lastOrNull { it.isSelfSigned() } ?: throw refused

            // The chain must lead to the self-signed certificate before it is offered
            try {
                validate(chain, anchor)
            } catch (e: Exception) {
                throw CertificateException(
                    "The chain doesn't lead to its self-signed certificate",
                    e
                )
            }

            val fingerprint = anchor.sha256()
            val pin = pinned()?.takeIf { it.host.equals(host, ignoreCase = true) }
            when {
                allowed() && pin?.fingerprint == fingerprint -> return
                allowed() && pin != null ->
                    throw CertificateChangedException(host, fingerprint, pin.fingerprint)

                else -> throw UntrustedSelfSignedException(
                    SelfSignedCertificate(
                        host = host,
                        subject = anchor.subjectX500Principal.name,
                        notBefore = anchor.notBefore.time,
                        notAfter = anchor.notAfter.time,
                        fingerprint = fingerprint,
                    )
                )
            }
        }
    }

    private fun validate(chain: Array<X509Certificate>, anchor: X509Certificate) {
        anchor.checkValidity()
        val path = chain.takeWhile { it != anchor }
        if (path.isEmpty()) return

        val parameters = PKIXParameters(setOf(TrustAnchor(anchor, null)))
        // A private CA rarely runs a revocation service
        parameters.isRevocationEnabled = false
        CertPathValidator.getInstance("PKIX")
            .validate(CertificateFactory.getInstance("X.509").generateCertPath(path), parameters)
    }
}
