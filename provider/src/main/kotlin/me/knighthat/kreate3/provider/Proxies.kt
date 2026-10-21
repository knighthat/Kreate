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

package me.knighthat.kreate3.provider

import kotlinx.coroutines.runBlocking
import me.knighthat.kreate3.constant.ProxyType
import me.knighthat.kreate3.preferences.Preference
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.Secret
import me.knighthat.kreate3.preferences.Secrets
import okhttp3.ConnectionPool
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.net.Authenticator as JavaAuthenticator

/**
 * The proxy the requests of a source go through, as the user set it up.
 */
sealed interface SourceProxy {

    /** Requests go out directly. */
    data object Off : SourceProxy

    /** The proxy is on but its host or port is missing, nothing may be sent. */
    data object Incomplete : SourceProxy

    /**
     * @param username empty for a proxy that needs no sign in
     */
    data class Ready(
        val type: ProxyType,
        val host: String,
        val port: Int,
        val username: String,
        val password: String,
    ) : SourceProxy {

        // The sign in stays out of logs
        override fun toString(): String = "Ready(type=$type, host=$host, port=$port)"
    }
}

/**
 * The proxy of a source set up by these settings, read now. While it is off, the source goes
 * through [networkProxy], so its own proxy overrides the app's.
 */
internal suspend fun sourceProxy(
    enabled: Preference<Boolean>,
    type: Preference<ProxyType>,
    host: Preference<String>,
    port: Preference<Int>,
    username: Secret,
    password: Secret,
): SourceProxy = proxyOf(enabled, type, host, port, username, password) ?: networkProxy()

/**
 * The proxy of the app now, see [Preferences.NETWORK_PROXY_ENABLED]: every request goes through
 * it, except those of a source with a proxy of its own.
 */
suspend fun networkProxy(): SourceProxy =
    proxyOf(
        Preferences.NETWORK_PROXY_ENABLED,
        Preferences.NETWORK_PROXY_TYPE,
        Preferences.NETWORK_PROXY_HOST,
        Preferences.NETWORK_PROXY_PORT,
        Secrets.NETWORK_PROXY_USERNAME,
        Secrets.NETWORK_PROXY_PASSWORD,
    ) ?: SourceProxy.Off

/** The proxy set up by these settings, `null` while it is off. */
private suspend fun proxyOf(
    enabled: Preference<Boolean>,
    type: Preference<ProxyType>,
    host: Preference<String>,
    port: Preference<Int>,
    username: Secret,
    password: Secret,
): SourceProxy? {
    if (!enabled.value) return null

    val address = host.value.trim()
    val number = port.value
    if (address.isEmpty() || number !in 1..65535) return SourceProxy.Incomplete
    return SourceProxy.Ready(type.value, address, number, username.current(), password.current())
}

/**
 * Sends the requests of the client through [proxy]. [SourceProxy.Incomplete] fails every request
 * as a network error, before anything is sent.
 *
 * Connections are pooled by host, so a client with another proxy needs a pool of its own, or it
 * would go on with the connections of the old one.
 *
 * @param source tells the sources apart, each has one proxy at a time
 */
internal fun OkHttpClient.Builder.proxy(source: String, proxy: SourceProxy): OkHttpClient.Builder {
    SocksCredentials.set(source, proxy.socksSignIn())

    when (proxy) {
        SourceProxy.Off -> {}

        SourceProxy.Incomplete -> addInterceptor { throw IOException(NOT_SET_UP) }

        is SourceProxy.Ready -> {
            proxy(proxy.toProxy())
            proxyAuthenticator { _, response -> proxy.signIn(response) }
        }
    }
    return this
}

/**
 * Like the other [proxy], for a client that is built once and kept: [current] is read before
 * each request, on OkHttp's threads, so a change applies to the next request. The client gets a
 * pool of its own, emptied of its idle connections when the proxy changes.
 *
 * @param source tells the sources apart, each has one proxy at a time
 */
fun OkHttpClient.Builder.proxy(
    source: String,
    current: suspend () -> SourceProxy,
): OkHttpClient.Builder {
    val pool = ConnectionPool()
    val selected = AtomicReference<SourceProxy>(SourceProxy.Off)

    return connectionPool(pool)
        .addInterceptor { chain ->
            val proxy = runBlocking { current() }
            if (selected.getAndSet(proxy) != proxy) {
                SocksCredentials.set(source, proxy.socksSignIn())
                // Connections in use go on until their request is done
                pool.evictAll()
            }
            if (proxy == SourceProxy.Incomplete) throw IOException(NOT_SET_UP)

            chain.proceed(chain.request())
        }
        .proxySelector(object : ProxySelector() {
            // Without a proxy of its own, the device's proxy is used, like by the other clients
            private val system: ProxySelector? get() = getDefault()

            override fun select(uri: URI?): List<Proxy> =
                (selected.get() as? SourceProxy.Ready)?.let { listOf(it.toProxy()) }
                    ?: system?.select(uri)
                    ?: listOf(Proxy.NO_PROXY)

            override fun connectFailed(uri: URI?, address: SocketAddress?, e: IOException?) {
                if (selected.get() !is SourceProxy.Ready) system?.connectFailed(uri, address, e)
            }
        })
        .proxyAuthenticator { _, response -> (selected.get() as? SourceProxy.Ready)?.signIn(response) }
}

private const val NOT_SET_UP = "The proxy isn't set up"

private const val PROXY_AUTHORIZATION = "Proxy-Authorization"

// Unresolved, so the proxy looks up the hosts and the device only looks up the proxy
private fun SourceProxy.Ready.toProxy(): Proxy = Proxy(
    if (type == ProxyType.SOCKS) Proxy.Type.SOCKS else Proxy.Type.HTTP,
    InetSocketAddress.createUnresolved(host, port),
)

/** The request again, signed in to this HTTP proxy, `null` when there is nothing to sign in with. */
private fun SourceProxy.Ready.signIn(response: Response): Request? = when {
    type != ProxyType.HTTP || username.isEmpty() -> null
    // Asked before every tunnel, and again when the proxy refused: once is enough
    response.request.header(PROXY_AUTHORIZATION) != null -> null
    else -> response.request.newBuilder()
        .header(PROXY_AUTHORIZATION, Credentials.basic(username, password))
        .build()
}

/** The proxy [SocksCredentials] signs in to, `null` when this isn't a SOCKS proxy with a sign in. */
private fun SourceProxy.socksSignIn(): SourceProxy.Ready? =
    (this as? SourceProxy.Ready)?.takeIf { it.type == ProxyType.SOCKS && it.username.isNotEmpty() }

/**
 * Signs in to SOCKS proxies. OkHttp signs in to HTTP proxies itself, but a SOCKS socket asks the
 * Java [JavaAuthenticator] of the whole process, with the address of the proxy. Two sources with
 * the same proxy address share its sign in, the last one set.
 */
private object SocksCredentials : JavaAuthenticator() {

    private val proxies = ConcurrentHashMap<String, SourceProxy.Ready>()
    private val installed = AtomicBoolean(false)

    /** [proxy] signs in the SOCKS proxy of [source] from now on, `null` for none. */
    fun set(source: String, proxy: SourceProxy.Ready?) {
        if (proxy == null) {
            proxies.remove(source)
            return
        }
        proxies[source] = proxy
        if (installed.compareAndSet(false, true)) setDefault(this)
    }

    override fun getPasswordAuthentication(): PasswordAuthentication? {
        if (requestorType != RequestorType.SERVER) return null
        if (requestingProtocol?.startsWith("SOCKS") != true) return null

        val proxy = proxies.values.firstOrNull {
            it.port == requestingPort && it.host.equals(requestingHost, ignoreCase = true)
        } ?: return null
        return PasswordAuthentication(proxy.username, proxy.password.toCharArray())
    }
}
