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
 * Trusts what [system] trusts and, beside it, a chain that ends in a self-signed certificate
 * remembered for the host. The first self-signed certificate a host presents is remembered
 * through [onFirstUse]; a different one later throws [CertificateChangedException].
 *
 * The chain is still checked against the remembered certificate (signatures and dates), and
 * the host name is checked afterwards by the HTTP client as usual. A chain that does not end in
 * a self-signed certificate, like a public certificate that failed for another reason, is
 * refused as [system] refused it.
 */
internal class SelfSignedTrustManager(
    private val system: X509TrustManager,
    private val pinned: () -> CertificatePin?,
    private val onFirstUse: (CertificatePin) -> Unit,
) : X509ExtendedTrustManager() {

    override fun checkServerTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        socket: Socket?
    ) {
        val host = (socket as? SSLSocket)?.handshakeSession?.peerHost
        check(chain, authType, host) {
            if (system is X509ExtendedTrustManager) system.checkServerTrusted(
                chain,
                authType,
                socket
            )
            else system.checkServerTrusted(chain, authType)
        }
    }

    override fun checkServerTrusted(
        chain: Array<X509Certificate>,
        authType: String,
        engine: SSLEngine?
    ) {
        check(chain, authType, engine?.peerHost) {
            if (system is X509ExtendedTrustManager) system.checkServerTrusted(
                chain,
                authType,
                engine
            )
            else system.checkServerTrusted(chain, authType)
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
     * certificate remembered for [host], remembering it if [host] has none yet.
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

            // The chain must lead to the self-signed certificate before it is remembered
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
                pin == null -> onFirstUse(CertificatePin(host, fingerprint))
                pin.fingerprint != fingerprint ->
                    throw CertificateChangedException(host, fingerprint, pin.fingerprint)
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
