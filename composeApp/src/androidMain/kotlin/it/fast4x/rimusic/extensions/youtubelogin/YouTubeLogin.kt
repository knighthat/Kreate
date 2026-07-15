package it.fast4x.rimusic.extensions.youtubelogin

import android.annotation.SuppressLint
import android.webkit.CookieManager
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
import androidx.datastore.preferences.core.edit
import androidx.room.concurrent.AtomicBoolean
import app.kreate.android.LocalPlayerAwareWindowInsets
import app.kreate.android.R
import app.kreate.di.PrefType
import app.kreate.di.Storage
import app.kreate.gateway.innertube.YouTube
import app.kreate.preferences.Preferences
import app.kreate.util.IS_DEBUG
import co.touchlab.kermit.Logger
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import me.knighthat.utils.Toaster
import org.koin.java.KoinJavaComponent.get
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes


@OptIn(
    DelicateCoroutinesApi::class,
    ExperimentalMaterial3Api::class
)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLogin( onDone: () -> Unit ) {
    var webView: WebView? = null

    AndroidView(
        modifier = Modifier.windowInsetsPadding( LocalPlayerAwareWindowInsets.current )
                           .fillMaxSize(),
        factory = { context ->
            val logger = Logger.withTag( "YouTubeLogin" )
            val credentials: Storage = get(Storage::class.java, PrefType.CREDENTIALS)
            val isRunning = AtomicBoolean(false)

            //<editor-fold desc="Clear cookies, LocalStorage, SessionStorage, etc.">
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies {}
            cookieManager.flush()

            WebStorage.getInstance().deleteAllData()
            //</editor-fold>

            WebView(context).apply {
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished( view: WebView, url: String? ) {
                        super.onPageFinished( view, url )

                        if( url?.startsWith("https://music.youtube.com") != true )
                            // Only extract credentials when user is redirected back to YTM page
                            return
                        // This function will be called twice (for whatever reason)
                        // so this check prevent things from being overridden while
                        // it's running in another thread
                        if( isRunning.get() ) return

                        // `evaluateJavascript` requires Main thread
                        MainScope().launch {
                            isRunning.set( true )

                            // Clear all credentials to prevent stale data
                            credentials.edit {
                                it[Preferences.Key.YOUTUBE_COOKIES] = ""
                                it[Preferences.Key.YOUTUBE_VISITOR_DATA] = ""
                                it[Preferences.Key.YOUTUBE_SYNC_ID] = ""
                                it[Preferences.Key.YOUTUBE_ACCOUNT_NAME] = ""
                                it[Preferences.Key.YOUTUBE_ACCOUNT_EMAIL] = ""
                                it[Preferences.Key.YOUTUBE_SELF_CHANNEL_HANDLE] = ""
                                it[Preferences.Key.YOUTUBE_ACCOUNT_AVATAR] = ""
                            }

                            val cookies: String = CookieManager.getInstance().getCookie( url )
                            if( IS_DEBUG )
                                // Sensitive data, must not leak in production
                                logger.d { "Cookies: $cookies" }
                            var visitorData: String? = null
                            var dataSyncId: String? = null

                            evaluateJavascript( "window.yt.config_.VISITOR_DATA" ) { result ->
                                visitorData = if( result != "null" ) result.removeSurrounding("\"") else ""
                                if( IS_DEBUG )
                                    // Sensitive data, must not leak in production
                                    logger.d { "visitorData: $visitorData" }
                            }
                            evaluateJavascript( "window.yt.config_.DATASYNC_ID" ) { result ->
                                dataSyncId = if( result != "null" ) result.removeSurrounding("\"").substringBefore("||") else ""
                                if( IS_DEBUG )
                                    // Sensitive data, must not leak in production
                                    logger.d { "dataSyncId: $dataSyncId" }
                            }

                            withTimeout( 1.minutes ) {
                                while( true ) {
                                    if( visitorData == null || dataSyncId == null ) {
                                        delay( 100.milliseconds )
                                        continue
                                    }
                                    if( cookies.isBlank() || visitorData.isBlank() || dataSyncId.isBlank() ) {
                                        logger.e { "Extracted data is empty!" }
                                        return@withTimeout
                                    }
                                    // Guarantee all data are written before releasing the thread
                                    credentials.edit {
                                        it[Preferences.Key.YOUTUBE_COOKIES] = cookies
                                        it[Preferences.Key.YOUTUBE_VISITOR_DATA] = visitorData
                                        it[Preferences.Key.YOUTUBE_SYNC_ID] = dataSyncId
                                    }

                                    break
                                }

                                withContext( Dispatchers.IO ) {
                                    get<YouTube>(YouTube::class.java)
                                        .account
                                        .getAccountDetails()
                                        .onFailure { err ->
                                            logger.e( "", err )
                                            Toaster.e( R.string.error_failed_to_acquire_account_info )
                                        }
                                        .onSuccess {
                                            credentials.edit { prefs ->
                                                prefs[Preferences.Key.YOUTUBE_ACCOUNT_NAME] = it.name
                                                prefs[Preferences.Key.YOUTUBE_ACCOUNT_EMAIL] = it.email.orEmpty()
                                                prefs[Preferences.Key.YOUTUBE_SELF_CHANNEL_HANDLE] = it.channelHandle.orEmpty()
                                                prefs[Preferences.Key.YOUTUBE_ACCOUNT_AVATAR] = it.thumbnailUrl.lastOrNull()?.url.orEmpty()
                                            }
                                        }
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

