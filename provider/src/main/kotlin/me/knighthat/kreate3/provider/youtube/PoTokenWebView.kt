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

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import com.metrolist.innertubex.extraction.potoken.parseIntegrityTokenData
import com.metrolist.innertubex.extraction.potoken.stringToU8
import com.metrolist.innertubex.extraction.potoken.u8ToBase64
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.knighthat.kreate3.provider.youtube.PoTokenWebView.Companion.create
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * A hidden WebView running BotGuard, Google's check that a browser is real, which mints PO tokens
 * bound to a text, like a visitor id or a video id. It has no network access of its own: the
 * challenge and the integrity token are fetched by [client] and handed to the page.
 *
 * Make one with [create], then [mint] as many tokens as needed until it [isExpired] or
 * [isDead]. Tokens are never logged.
 *
 * From NewPipe's and Metrolist's `PoTokenWebView` (GPL-3.0).
 */
@SuppressLint("SetJavaScriptEnabled")
internal class PoTokenWebView private constructor(
    context: Context,
    private val client: suspend () -> HttpClient,
    // Resumed exactly once, when the minter is made or making it failed
    private val created: Continuation<PoTokenWebView>,
) {

    private val webView = WebView(context)
    private val scope = MainScope()
    private val createdResumed = AtomicBoolean(false)
    private val pending = ConcurrentHashMap<String, Continuation<String>>()
    private val requestCount = AtomicLong()
    private val exceptionHandler = CoroutineExceptionHandler { _, e -> failCreation(e) }

    @Volatile
    private var closed = false

    @Volatile
    private var expiresAt: Instant = Instant.DISTANT_PAST

    /**
     * Whether its renderer died or stopped answering, so it can't mint anymore. The renderer's
     * death isn't always reported, a mint that times out counts too.
     */
    @Volatile
    var isDead: Boolean = false
        private set

    /** Whether its integrity token ran out, with some margin, so it can't mint anymore. */
    val isExpired: Boolean get() = Clock.System.now() >= expiresAt

    init {
        webView.settings.javaScriptEnabled = true
        webView.settings.userAgentString = USER_AGENT
        webView.settings.blockNetworkLoads = true
        webView.addJavascriptInterface(this, JS_INTERFACE)

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                val text = message.message()
                if (text.contains("Uncaught")) {
                    val error = "$text, source: ${message.sourceId()} (${message.lineNumber()})"
                    if (createdResumed.get()) {
                        // The page ran fine, so it came from Google's scripts: worth another try
                        Log.e(TAG, "Uncaught JavaScript error after BotGuard started")
                        die(PoTokenException(error))
                    } else {
                        Log.e(TAG, "This WebView can't run BotGuard")
                        val exception = PoTokenException(error, badWebView = true)
                        failCreation(exception)
                        failPending(exception)
                    }
                }
                return super.onConsoleMessage(message)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun onRenderProcessGone(
                view: WebView,
                detail: RenderProcessGoneDetail
            ): Boolean {
                // Killed for memory most of the time, a new one may work
                Log.e(TAG, "BotGuard's renderer is gone, crashed: ${detail.didCrash()}")
                val exception = PoTokenException("The WebView's renderer is gone")
                failCreation(exception)
                die(exception)
                // Keeps the app's process alive
                return true
            }
        }
    }

    private fun load() {
        scope.launch(exceptionHandler) {
            val html = withContext(Dispatchers.IO) {
                webView.context.assets.open(PAGE).bufferedReader().use { it.readText() }
            }
            // Starts as soon as the page is loaded
            val page = html.replaceFirst(
                "</script>",
                "\n$JS_INTERFACE.downloadAndRunBotGuard()</script>"
            )
            webView.loadDataWithBaseURL(ORIGIN, page, "text/html", "utf-8", null)
        }
    }

    /** Called by the page once it's loaded. */
    @JavascriptInterface
    fun downloadAndRunBotGuard() {
        askBotGuard(CREATE_URL, "[ \"$REQUEST_KEY\" ]") { answer ->
            val challenge = parseChallengeData(answer)
            webView.evaluateJavascript(
                """try {
                    runBotGuard($challenge).then(function (result) {
                        this.webPoSignalOutput = result.webPoSignalOutput
                        $JS_INTERFACE.onRunBotGuardResult(result.botguardResponse)
                    }, function (error) {
                        $JS_INTERFACE.onJsCreationError(error + "\n" + error.stack)
                    })
                } catch (error) {
                    $JS_INTERFACE.onJsCreationError(error + "\n" + error.stack)
                }""",
                null
            )
        }
    }

    /** Called by the page when BotGuard ran, with what it found. */
    @JavascriptInterface
    fun onRunBotGuardResult(botGuardResponse: String) {
        askBotGuard(GENERATE_IT_URL, "[ \"$REQUEST_KEY\", \"$botGuardResponse\" ]") { answer ->
            val integrity = parseIntegrityTokenData(answer)
            expiresAt = Clock.System.now() + integrity.lifetimeSeconds.seconds - EXPIRY_MARGIN
            webView.evaluateJavascript(
                """try {
                    this.integrityToken = ${integrity.tokenJavaScript}
                    createPoTokenMinter(webPoSignalOutput, integrityToken).then(function () {
                        $JS_INTERFACE.onMinterCreated()
                    }).catch(function (error) {
                        $JS_INTERFACE.onJsCreationError(error + "\n" + (error.stack || ''))
                    })
                } catch (error) {
                    $JS_INTERFACE.onJsCreationError(error + "\n" + error.stack)
                }""",
                null
            )
        }
    }

    /** Called by the page when it can't be made ready. */
    @JavascriptInterface
    fun onJsCreationError(error: String) {
        // An engine too old for the page fails to parse it
        failCreation(PoTokenException(error, badWebView = error.contains("SyntaxError")))
    }

    /** Called by the page when it's ready to mint. */
    @JavascriptInterface
    fun onMinterCreated() {
        if (createdResumed.compareAndSet(false, true)) created.resume(this)
    }

    /**
     * A PO token bound to [identifier], as YouTube takes it.
     *
     * @throws PoTokenException when it can't be minted, make another [PoTokenWebView] then
     */
    suspend fun mint(identifier: String): String {
        if (isDead || closed) throw PoTokenException("This BotGuard is gone, make another one")
        // Two mints for the same identifier at once each get their answer
        val request = "$identifier#${requestCount.incrementAndGet()}"
        return try {
            withTimeout(MINT_TIMEOUT) {
                withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine { continuation ->
                        pending[request] = continuation
                        webView.evaluateJavascript(
                            """(function () {
                                var request = "$request"
                                try {
                                    obtainPoToken(${stringToU8(identifier)}).then(function (token) {
                                        $JS_INTERFACE.onMinted(request, token.join(","))
                                    }).catch(function (error) {
                                        $JS_INTERFACE.onMintError(request, error + "\n" + (error.stack || ''))
                                    })
                                } catch (error) {
                                    $JS_INTERFACE.onMintError(request, error + "\n" + error.stack)
                                }
                            })()""",
                            null
                        )
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            // A renderer that doesn't answer is as good as dead
            isDead = true
            pending.remove(request)
            throw PoTokenException("Minting a PO token timed out")
        }
    }

    /** Called by the page with a token, its bytes split by commas. */
    @JavascriptInterface
    fun onMinted(request: String, token: String) {
        val continuation = pending.remove(request) ?: return
        val encoded = try {
            u8ToBase64(token)
        } catch (e: Exception) {
            continuation.resumeWithException(e)
            return
        }
        continuation.resume(encoded)
    }

    /** Called by the page when a token can't be minted. */
    @JavascriptInterface
    fun onMintError(request: String, error: String) {
        // The minter was made, so the engine is fine: Google's data was the problem
        pending.remove(request)?.resumeWithException(PoTokenException(error))
    }

    /** Sends [body] to a BotGuard endpoint and hands its answer to [handle] on the main thread. */
    private fun askBotGuard(url: String, body: String, handle: (String) -> Unit) {
        scope.launch(exceptionHandler) {
            val answer = withContext(Dispatchers.IO) {
                val response = client().post(url) {
                    header(HttpHeaders.UserAgent, USER_AGENT)
                    header(HttpHeaders.Accept, "application/json")
                    header("x-goog-api-key", API_KEY)
                    header("x-user-agent", "grpc-web-javascript/0.1")
                    setBody(TextContent(body, ContentType.parse("application/json+protobuf")))
                }
                response.bodyAsText().takeIf { response.status.value == 200 }
            }
            if (answer.isNullOrEmpty())
                throw PoTokenException("BotGuard didn't answer")
            handle(answer)
        }
    }

    private fun failCreation(error: Throwable) {
        close()
        // The creation may have timed out already
        if (createdResumed.compareAndSet(false, true))
            runCatching { created.resumeWithException(error) }
    }

    private fun die(error: PoTokenException) {
        isDead = true
        close()
        failPending(error)
    }

    private fun failPending(error: Throwable) {
        pending.keys.toList().forEach { request ->
            pending.remove(request)?.let { runCatching { it.resumeWithException(error) } }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        scope.cancel()
        // The page calls back on another thread, a WebView is only touched on the main one
        if (Looper.myLooper() == Looper.getMainLooper()) destroy()
        else Handler(Looper.getMainLooper()).post { destroy() }
    }

    @MainThread
    private fun destroy() {
        // A WebView whose renderer crashed may throw here
        runCatching {
            webView.loadUrl("about:blank")
            webView.onPause()
            webView.removeAllViews()
            webView.destroy()
        }.onFailure { Log.w(TAG, "Couldn't destroy BotGuard's WebView", it) }
    }

    companion object {
        private const val TAG = "PoTokenWebView"
        private const val PAGE = "po_token.html"
        private const val JS_INTERFACE = "PoTokenWebView"
        private const val ORIGIN = "https://www.youtube.com"
        private const val CREATE_URL = "https://www.youtube.com/api/jnn/v1/Create"
        private const val GENERATE_IT_URL = "https://www.youtube.com/api/jnn/v1/GenerateIT"

        // The website's, as NewPipe and Metrolist send them
        private const val API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        private const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"

        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/140.0.0.0 Safari/537.36"

        /** Taken off the integrity token's lifetime, so a token never expires while in use. */
        private val EXPIRY_MARGIN = 10.minutes

        /** Two requests to Google and running BotGuard, a few seconds when all is well. */
        private val CREATE_TIMEOUT = 45.seconds

        /** A live renderer mints in well under a second. */
        private val MINT_TIMEOUT = 15.seconds

        /**
         * A [PoTokenWebView] ready to [mint].
         *
         * @throws PoTokenException when BotGuard can't be run, see [PoTokenException.badWebView]
         */
        suspend fun create(context: Context, client: suspend () -> HttpClient): PoTokenWebView {
            var webView: PoTokenWebView? = null
            try {
                return withTimeout(CREATE_TIMEOUT) {
                    withContext(Dispatchers.Main) {
                        suspendCancellableCoroutine { continuation ->
                            webView =
                                PoTokenWebView(context, client, continuation).also { it.load() }
                        }
                    }
                }
            } catch (_: TimeoutCancellationException) {
                closeQuietly(webView)
                throw PoTokenException("Starting BotGuard timed out")
            } catch (e: CancellationException) {
                closeQuietly(webView)
                throw e
            }
        }

        private suspend fun closeQuietly(webView: PoTokenWebView?) {
            webView ?: return
            withContext(NonCancellable + Dispatchers.Main) {
                // A late callback must not resume the continuation that was given up on
                webView.createdResumed.set(true)
                webView.close()
            }
        }
    }
}
