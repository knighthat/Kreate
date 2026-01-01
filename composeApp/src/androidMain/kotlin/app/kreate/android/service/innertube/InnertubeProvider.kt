package app.kreate.android.service.innertube

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import app.kreate.android.Preferences
import app.kreate.android.service.NetworkService
import io.ktor.client.HttpClient
import me.knighthat.innertube.Innertube
import timber.log.Timber


class InnertubeProvider: Innertube.KtorProvider {

    companion object {
        val COOKIE_MAP by derivedStateOf {
            if( Preferences.YOUTUBE_COOKIES.value.isBlank() )
                return@derivedStateOf emptyMap()

            runCatching {
                Preferences.YOUTUBE_COOKIES
                           .value
                           .split( ';' )
                           .associate {
                               val (k, v) = it.split('=', limit = 2)
                               k.trim() to v.trim()
                           }
            }.onFailure {
                it.printStackTrace()
                Timber.tag( "InnertubeProvider" ).e( "Cookie parser failed!" )
            }.getOrElse { emptyMap() }
        }
    }

    override val client: HttpClient
        get() = NetworkService.client

    override val cookies: String by Preferences.YOUTUBE_COOKIES
    override val dataSyncId: String by Preferences.YOUTUBE_SYNC_ID
    override val visitorData: String by Preferences.YOUTUBE_VISITOR_DATA
}