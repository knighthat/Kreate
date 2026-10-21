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

package me.knighthat.kreate3.provider.youtube

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import me.knighthat.kreate3.constant.ProxyType
import me.knighthat.kreate3.provider.HttpClientFactory
import me.knighthat.kreate3.provider.SourceProxy
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.Proxy

class YouTubeClientsTest {

    private val base = OkHttpClient()
    private val factory = HttpClientFactory { HttpClient(OkHttp) { engine { preconfigured = it } } }
    private var proxy: SourceProxy = SourceProxy.Off
    private val clients = YouTubeClients(base, factory) { proxy }

    private fun ready(port: Int) = SourceProxy.Ready(ProxyType.HTTP, "proxy.test", port, "", "")

    @Test
    fun `the same proxy keeps the same clients`() = runBlocking {
        proxy = ready(8080)
        val first = clients.current()
        val firstOkHttp = clients.currentOkHttp()
        proxy = ready(8080)
        assertSame(first, clients.current())
        assertSame(firstOkHttp, clients.currentOkHttp())
    }

    @Test
    fun `another proxy gets new clients with connections of their own`() = runBlocking {
        proxy = ready(8080)
        val first = clients.currentOkHttp()
        proxy = ready(8081)
        val second = clients.currentOkHttp()

        assertNotSame(first, second)
        assertNotSame(first.connectionPool, second.connectionPool)
        assertNotSame(base.connectionPool, second.connectionPool)
        assertEquals(
            Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved("proxy.test", 8081)),
            second.proxy,
        )
    }

    @Test
    fun `without a proxy the app's connections are shared`() = runBlocking {
        val okHttp = clients.currentOkHttp()
        assertSame(base.connectionPool, okHttp.connectionPool)
        assertNull(okHttp.proxy)
    }

    @Test
    fun `the hosts of YouTube are told apart from others`() {
        listOf(
            "music.youtube.com",
            "youtube.com",
            "rr3---sn-abc.googlevideo.com",
            "i.ytimg.com",
            "yt3.ggpht.com",
            "lh3.googleusercontent.com",
        ).forEach { assertTrue(it, isYouTubeHost(it)) }
        listOf("lrclib.net", "notyoutube.com", "youtube.com.example.org", "music.example.com")
            .forEach { assertFalse(it, isYouTubeHost(it)) }
    }
}
