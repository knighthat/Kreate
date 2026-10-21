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

import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.suspendCancellableCoroutine
import me.knighthat.kreate3.constant.ProxyType
import me.knighthat.kreate3.provider.SourceProxy
import kotlin.coroutines.resume

/**
 * How the sign in page reaches Google, set by [useInWebViews].
 */
internal sealed interface SignInProxy {

    /** Directly, neither YouTube nor the app has a proxy. */
    data object Direct : SignInProxy

    /** Through YouTube's [proxy], the page signs in to it when it asks. */
    data class Applied(val proxy: SourceProxy.Ready) : SignInProxy

    /** Not at all: the page would skip YouTube's proxy, [reason] tells the user why. */
    data class Unavailable(val reason: String) : SignInProxy
}

/**
 * Sends the requests of every WebView of the app through [proxy], and waits until they do. The
 * only WebView that goes online is the sign in page, PO tokens are made without network access.
 * Undo it with [clearWebViewProxy].
 */
internal suspend fun useInWebViews(proxy: SourceProxy): SignInProxy = when (proxy) {
    SourceProxy.Off -> SignInProxy.Direct

    SourceProxy.Incomplete ->
        SignInProxy.Unavailable("Set the host and port of the proxy first")

    is SourceProxy.Ready -> when {
        !WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) ->
            SignInProxy.Unavailable("This device's WebView can't use a proxy, the sign in page would skip it")

        // Chromium has no sign in for SOCKS proxies
        proxy.type == ProxyType.SOCKS && proxy.username.isNotEmpty() ->
            SignInProxy.Unavailable("The sign in page can't sign in to a SOCKS5 proxy. Sign in through an HTTP proxy, or a SOCKS5 one without a username")

        else -> {
            val scheme = if (proxy.type == ProxyType.SOCKS) "socks5" else "http"
            val host = if (':' in proxy.host) "[${proxy.host}]" else proxy.host
            val config = ProxyConfig.Builder()
                .addProxyRule("$scheme://$host:${proxy.port}")
                .build()
            suspendCancellableCoroutine<SignInProxy> { continuation ->
                ProxyController.getInstance().setProxyOverride(config, Runnable::run) {
                    continuation.resume(SignInProxy.Applied(proxy))
                }
            }
        }
    }
}

/** WebViews go online directly again, after [useInWebViews]. */
internal fun clearWebViewProxy() {
    if (!WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) return
    ProxyController.getInstance().clearProxyOverride(Runnable::run) {}
}
