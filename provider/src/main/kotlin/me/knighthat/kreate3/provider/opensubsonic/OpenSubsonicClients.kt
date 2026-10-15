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

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * The HTTP client used for the OpenSubsonic server, and only for it, so trusting a self-signed
 * certificate never reaches other requests. It has no logging, the sign in may be in addresses.
 *
 * The client is built again whenever what is trusted changes, with new connections and a new
 * TLS session cache, so an old connection or a resumed session can't skip the new rules.
 */
internal class OpenSubsonicClients(
    private val base: OkHttpClient,
    private val settings: OpenSubsonicSettings,
) {

    private data class Trust(val allowSelfSigned: Boolean, val pin: CertificatePin?)

    private class Clients(val trust: Trust, val okHttp: OkHttpClient, val ktor: HttpClient)

    private var clients: Clients? = null

    /**
     * The client for what is trusted now. A replaced client is not closed, requests on it may
     * still run, and closing would stop the dispatcher it shares with [base].
     */
    fun current(): HttpClient = clients().ktor

    /** The OkHttp client under [current], for what reads the server without Ktor. */
    fun currentOkHttp(): OkHttpClient = clients().okHttp

    @Synchronized
    private fun clients(): Clients {
        val wanted = Trust(settings.allowSelfSigned, settings.pinnedCertificate)
        clients?.takeIf { it.trust == wanted }?.let { return it }

        val okHttp = build(wanted)
        return Clients(wanted, okHttp, HttpClient(OkHttp) { engine { preconfigured = okHttp } })
            .also { clients = it }
    }

    private fun build(trust: Trust): OkHttpClient {
        val okHttp = base.newBuilder()
            .connectionPool(ConnectionPool())
            .apply {
                if (!trust.allowSelfSigned) return@apply

                val trustManager = SelfSignedTrustManager(
                    system = systemTrustManager(),
                    pinned = { settings.pinnedCertificate },
                    onFirstUse = settings::pin,
                )
                val context = SSLContext.getInstance("TLS")
                context.init(null, arrayOf(trustManager), null)
                sslSocketFactory(context.socketFactory, trustManager)
            }
            .build()

        return okHttp
    }

    private fun systemTrustManager(): X509TrustManager =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply { init(null as KeyStore?) }
            .trustManagers
            .filterIsInstance<X509TrustManager>()
            .first()
}
