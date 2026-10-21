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

import me.knighthat.kreate3.constant.ProxyType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.URI

class ProxiesTest {

    private val uri = URI("https://lyrics.test/")

    private var proxy: SourceProxy = SourceProxy.Off
    private var sent = 0

    private val client = OkHttpClient.Builder()
        .proxy("test") { proxy }
        // Answers in place of the network, so only what the proxy's interceptor let through
        // counts as sent
        .addInterceptor { chain ->
            sent++
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body("".toResponseBody())
                .build()
        }
        .build()

    private fun send() =
        client.newCall(Request.Builder().url(uri.toString()).build()).execute().close()

    private fun selected(): List<Proxy> = client.proxySelector.select(uri)

    @Test
    fun `nothing is sent while the proxy isn't set up`() {
        proxy = SourceProxy.Incomplete

        assertThrows(IOException::class.java) { send() }
        assertEquals(0, sent)
    }

    @Test
    fun `the proxy read before a request is the one connected through`() {
        proxy = SourceProxy.Ready(ProxyType.SOCKS, "proxy.test", 1080, "", "")
        send()

        assertEquals(
            listOf(Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved("proxy.test", 1080))),
            selected(),
        )
    }

    @Test
    fun `a changed proxy applies to the next request`() {
        proxy = SourceProxy.Ready(ProxyType.HTTP, "proxy.test", 8080, "", "")
        send()
        proxy = SourceProxy.Ready(ProxyType.HTTP, "proxy.test", 8081, "", "")
        send()

        assertEquals(
            listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.test", 8081))),
            selected(),
        )
    }

    @Test
    fun `without a proxy the device's is used`() {
        proxy = SourceProxy.Ready(ProxyType.HTTP, "proxy.test", 8080, "", "")
        send()
        proxy = SourceProxy.Off
        send()

        assertEquals(ProxySelector.getDefault().select(uri), selected())
        assertEquals(2, sent)
    }
}
