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
package me.knighthat.kreate3.ui.screen.settings.network.youtube

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import me.knighthat.kreate3.provider.youtube.YouTubeChannel
import me.knighthat.kreate3.provider.youtube.youTubeProxy
import me.knighthat.kreate3.ui.screen.detail.DetailScaffold
import org.koin.androidx.compose.koinViewModel

/** Google's sign in page, which lands on YouTube Music once the user is signed in. */
private const val SIGN_IN_URL =
    "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true" +
            "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue" +
            "%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F"

private const val YOUTUBE_MUSIC = "https://music.youtube.com"

/** Forgets every cookie and page storage of the app's web views. */
private fun clearWebData() {
    CookieManager.getInstance().apply {
        removeAllCookies(null)
        flush()
    }
    WebStorage.getInstance().deleteAllData()
}

/**
 * Google's sign in page in a web view. The user signs in there, the app never sees the password:
 * once the page lands on YouTube Music signed in, its cookies are kept as the session and the web
 * view forgets them. Leaving before that keeps nothing. An account with several channels then
 * picks the one to act as.
 *
 * The page goes through YouTube's proxy while it shows, so Google sees the sign in from where the
 * requests come from after it. When it can't, the page doesn't load at all.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeSignInScreen(
    onBack: () -> Unit,
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: YouTubeAccountViewModel = koinViewModel(),
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    // The page waits for YouTube's proxy, it never loads without it
    var proxy by remember { mutableStateOf<SignInProxy?>(null) }
    // The session is kept, what is left is choosing a channel
    var signedIn by remember { mutableStateOf(false) }
    var channels by remember { mutableStateOf<List<YouTubeChannel>?>(null) }
    BackHandler(enabled = !signedIn) { webView?.takeIf { it.canGoBack() }?.goBack() ?: onBack() }

    val finish = { channel: YouTubeChannel? ->
        viewModel.finishSignIn(channel)
        onSignedIn()
    }
    LaunchedEffect(signedIn) {
        if (!signedIn) return@LaunchedEffect
        val found = viewModel.channelsAfterSignIn()
        // An account with a single channel has nothing to choose
        if (found.size > 1) channels = found else finish(null)
    }

    LaunchedEffect(Unit) { proxy = useInWebViews(youTubeProxy()) }
    DisposableEffect(Unit) {
        onDispose {
            webView?.destroy()
            // What the page left behind, signed in or half way, is not kept here
            clearWebData()
            // Even while it is still being set, the clearing comes after it
            clearWebViewProxy()
        }
    }

    channels?.let { found ->
        YouTubeChannelPicker(
            channels = found,
            pageId = "",
            onPick = { finish(it) },
            // Without a choice the account's own channel is used
            onDismiss = { finish(null) },
            dismissLabel = "Skip",
        )
    }

    DetailScaffold("Sign in to YouTube", onBack, modifier) { innerPadding ->
        val current = proxy
        if (signedIn || current == null || current is SignInProxy.Unavailable) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
            ) {
                if (current is SignInProxy.Unavailable)
                    Text(current.reason, textAlign = TextAlign.Center)
                else CircularProgressIndicator()
            }
            return@DetailScaffold
        }
        val proxySignIn =
            (current as? SignInProxy.Applied)?.proxy?.takeIf { it.username.isNotEmpty() }
        AndroidView(
            factory = { context ->
                // Signs in to the proxy once, a second ask means it refused
                var proxyAsked = false
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        // YouTube Music finishes loading more than once, it signs in once
                        override fun onPageFinished(view: WebView, url: String?) {
                            if (signedIn || url?.toUri()?.host != YOUTUBE_MUSIC.toUri().host) return
                            val cookies =
                                CookieManager.getInstance().getCookie(YOUTUBE_MUSIC) ?: return
                            if (viewModel.signIn(cookies)) signedIn = true
                        }

                        override fun onReceivedHttpAuthRequest(
                            view: WebView,
                            handler: HttpAuthHandler,
                            host: String,
                            realm: String?,
                        ) {
                            if (
                                proxySignIn == null || proxyAsked ||
                                !host.equals(proxySignIn.host, ignoreCase = true)
                            ) return super.onReceivedHttpAuthRequest(view, handler, host, realm)
                            proxyAsked = true
                            handler.proceed(proxySignIn.username, proxySignIn.password)
                        }
                    }
                    // Starts from a clean slate, without a session of an earlier attempt
                    CookieManager.getInstance().removeAllCookies { loadUrl(SIGN_IN_URL) }
                    webView = this
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}
