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

import android.content.Context
import android.util.Log
import android.webkit.CookieManager
import com.metrolist.innertubex.extraction.PoTokenResult
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.TokenProviderCapabilities
import com.metrolist.innertubex.extraction.strategy.PoTokenProviderKind
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

/**
 * PO tokens for InnerTubeX, minted by BotGuard in a [PoTokenWebView]: the website's clients, like
 * `WEB_REMIX`, need one to stream. Each visitor id gets its own WebView, kept while it can mint.
 *
 * A token for the player request is bound to the visitor id and minted once per WebView, a
 * token for the stream is bound to the video id and minted for each song. When no token can be
 * made, InnerTubeX goes on with the clients that need none.
 *
 * From NewPipe's and Metrolist's `PoTokenGenerator` (GPL-3.0).
 */
internal class WebPoTokenProvider(context: Context, private val client: HttpClient) :
    TokenProvider {

    private val context = context.applicationContext
    private val lock = Mutex()

    /** Whether the device has a WebView, some have none or a broken one. */
    private val hasWebView by lazy { runCatching { CookieManager.getInstance() }.isSuccess }

    /** Set when the WebView turned out unable to run BotGuard, nothing is tried after it. */
    @Volatile
    private var badWebView = false

    /** The WebView minting now, with the visitor id it mints for and that id's token. */
    private class Minter(val webView: PoTokenWebView, val visitorData: String, val token: String)

    private var minter: Minter? = null

    override val capabilities = TokenProviderCapabilities(
        providers = setOf(PoTokenProviderKind.WEB_BOTGUARD),
        usesWebView = true,
    )

    override suspend fun getPoToken(
        videoId: String,
        visitorData: String,
        cookie: String?,
    ): PoTokenResult? {
        if (!hasWebView || badWebView) return null
        return try {
            withTimeout(TIMEOUT) { tokens(videoId, visitorData, retry = true) }
        } catch (_: TimeoutCancellationException) {
            // Its process may have been killed and left the WebView hanging
            Log.w(TAG, "Making a PO token timed out")
            close()
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: PoTokenException) {
            if (e.badWebView) badWebView = true
            Log.w(TAG, "Couldn't make a PO token: ${e.message?.lineSequence()?.firstOrNull()}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't make a PO token", e)
            null
        }
    }

    private suspend fun tokens(
        videoId: String,
        visitorData: String,
        retry: Boolean
    ): PoTokenResult {
        val (current, isNew) = lock.withLock {
            val current = minter?.takeIf {
                it.visitorData == visitorData && !it.webView.isExpired && !it.webView.isDead
            }
            if (current != null) return@withLock current to false

            minter?.webView?.let { withContext(Dispatchers.Main) { it.close() } }
            minter = null
            val webView = PoTokenWebView.create(context, client)
            // The visitor id's token must be the first one a WebView mints
            val token = try {
                webView.mint(visitorData)
            } catch (e: Exception) {
                webView.close()
                throw e
            }
            Minter(webView, visitorData, token).also { minter = it } to true
        }

        val streamToken = try {
            current.webView.mint(videoId)
        } catch (e: PoTokenException) {
            // A WebView left in the background may have lost its page, a new one may work
            if (isNew || !retry) throw e
            lock.withLock { if (minter === current) minter = null }
            current.webView.close()
            return tokens(videoId, visitorData, retry = false)
        }
        return PoTokenResult(
            playerRequestToken = current.token,
            streamingDataToken = streamToken,
            visitorData = visitorData,
        )
    }

    override suspend fun close() {
        lock.withLock {
            minter?.webView?.let { withContext(Dispatchers.Main) { it.close() } }
            minter = null
        }
    }

    private companion object {
        const val TAG = "WebPoTokenProvider"

        /**
         * Starting BotGuard takes a few seconds when all is well. Past this, InnerTubeX goes on
         * without a token instead of making the user wait.
         */
        val TIMEOUT = 8.seconds
    }
}
