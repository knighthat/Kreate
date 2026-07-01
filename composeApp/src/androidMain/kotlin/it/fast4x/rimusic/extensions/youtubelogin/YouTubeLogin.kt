package it.fast4x.rimusic.extensions.youtubelogin

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import app.kreate.android.R
import app.kreate.preferences.Preferences
import co.touchlab.kermit.Logger
import com.metrolist.innertube.YouTube
import it.fast4x.rimusic.LocalPlayerAwareWindowInsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.utils.Toaster

@OptIn(
    DelicateCoroutinesApi::class,
    ExperimentalMaterial3Api::class
)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLogin( onDone: () -> Unit ) {
    val scope = rememberCoroutineScope()
    var webView: WebView? = null

    // This section is ripped from Metrolist - Full credit to their team
    // Small changes were made in order to make it work with Kreate
    // https://github.com/mostafaalagamy/Metrolist/blob/main/app/src/main/kotlin/com/metrolist/music/ui/screens/LoginScreen.kt
    AndroidView(
        modifier = Modifier.windowInsetsPadding( LocalPlayerAwareWindowInsets.current )
                           .fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished( view: WebView, url: String? ) {
                        super.onPageFinished( view, url )

                        if( url?.startsWith("https://music.youtube.com") != true )
                            // Only extract credentials when user is redirected back to YTM page
                            return

                        val cookies = CookieManager.getInstance().getCookie( url )
                        Preferences.YOUTUBE_COOKIES.update( cookies )
                        YouTube.cookie = cookies
                        evaluateJavascript( "window.yt.config_.VISITOR_DATA" ) { result ->
                            val visitorData = if( result != "null" ) result.removeSurrounding("\"") else ""
                            Preferences.YOUTUBE_VISITOR_DATA.update( visitorData )
                            YouTube.visitorData = visitorData
                        }
                        evaluateJavascript( "window.yt.config_.DATASYNC_ID" ) { result ->
                            val datasyncId = if( result != "null" ) result.removeSurrounding("\"").substringBefore("||") else ""
                            Preferences.YOUTUBE_SYNC_ID.update( datasyncId )
                            YouTube.dataSyncId = datasyncId
                        }
                        CoroutineScope(Dispatchers.IO).launch {
                            YouTube.accountInfo()
                                   .onFailure { err ->
                                       Logger.e( "", err, "YouTubeLogin" )
                                       Toaster.e( R.string.error_failed_to_acquire_account_info )
                                   }
                                   .onSuccess {
                                       withContext( Dispatchers.Main ) {
                                           Preferences.YOUTUBE_ACCOUNT_NAME.update( it.name )
                                           Preferences.YOUTUBE_ACCOUNT_EMAIL.update( it.email.orEmpty() )
                                           Preferences.YOUTUBE_SELF_CHANNEL_HANDLE.update( it.channelHandle.orEmpty() )
                                           Preferences.YOUTUBE_ACCOUNT_AVATAR.update( it.thumbnailUrl.orEmpty() )
                                       }
                                   }
                        }

                        onDone()
                    }
                }
                settings.apply {
                    javaScriptEnabled = true
                    setSupportZoom(true)
                    builtInZoomControls = true
                    displayZoomControls = false
                }
                webView = this
                loadUrl("https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com")
            }
        }
    )

    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
    }
}

