package it.fast4x.rimusic.extensions.discord

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.kreate.android.Preferences
import app.kreate.android.R
import co.touchlab.kermit.Logger
import it.fast4x.rimusic.LocalPlayerAwareWindowInsets
import me.knighthat.innertube.UserAgents
import me.knighthat.utils.Toaster

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscordLoginAndGetToken( onDone: () -> Unit ) {
    var webView: WebView? = null

    // This section is ripped from Metrolist - Full credit to their team
    // Small changes were made in order to make it work with Kreate
    // https://github.com/mostafaalagamy/Metrolist/blob/main/app/src/main/kotlin/com/metrolist/music/ui/screens/settings/DiscordLoginScreen.kt
    AndroidView(
        modifier = Modifier.windowInsetsPadding( LocalPlayerAwareWindowInsets.current )
                           .fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.setSupportZoom(true)
                settings.builtInZoomControls = true
                settings.userAgentString = UserAgents.CHROME_WINDOWS

                CookieManager.getInstance().apply {
                    removeAllCookies(null)
                    flush()
                }

                WebStorage.getInstance().deleteAllData()

                addJavascriptInterface(object {
                    @JavascriptInterface
                    @Suppress("unused")     // To stop IDE from complaining
                    fun onRetrieveToken( token: String ) {
                        Preferences.DISCORD_ACCESS_TOKEN.value = token
                        onDone()
                    }

                    @JavascriptInterface
                    @Suppress("unused")     // To stop IDE from complaining
                    fun onFailure( message: String ) {
                        if( message != "null" )
                            Logger.e( tag = "Discord" ) { message }

                        Toaster.e( R.string.error_failed_to_extract_discord_acess_token )

                        onDone()
                    }

                }, "Android")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        if ( url.contains("/channels/@me") || url.contains("/app") ) {
                            // Source: https://gist.github.com/MarvNC/e601f3603df22f36ebd3102c501116c6
                            view.evaluateJavascript(
                                """
                                (function() {
                                    try {
                                        const iframe = document.createElement('iframe');
                                        const token = JSON.parse(document.body.appendChild(iframe).contentWindow.localStorage.token);

                                        if (token) {
                                            Android.onRetrieveToken(token);
                                        } else {
                                            Android.onFailure("null");
                                        }
                                    } catch (err) {
                                        Android.onFailure(err.message);
                                    }
                                })();
                                """.trimIndent(), null
                            )
                        }
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean = false
                }

                webView = this
                loadUrl( "https://discord.com/login" )
            }
        }
    )

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }
}
