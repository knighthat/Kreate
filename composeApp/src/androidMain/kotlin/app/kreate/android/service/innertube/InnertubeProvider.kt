package app.kreate.android.service.innertube

import app.kreate.preferences.Preferences
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import me.knighthat.innertube.Innertube
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject


class InnertubeProvider: Innertube.KtorProvider, KoinComponent {

    companion object {
        val COOKIE_MAP: Map<String, String>
            get() = with( Preferences.YOUTUBE_COOKIES.value ) {
                runCatching {
                    split( ';' ).associate {
                        val (k, v) = it.split('=', limit = 2)
                        k.trim() to v.trim()
                    }
                }.onFailure { err ->
                    Logger.e( err, "InnertubeProvider" ) { "Cookie parser failed!" }
                }.getOrElse { emptyMap() }
            }
    }

    override val client: HttpClient by inject()
    override val cookies: String
        get() = Preferences.YOUTUBE_COOKIES.value
    override val dataSyncId: String
        get() = Preferences.YOUTUBE_SYNC_ID.value
    override val visitorData: String
        get() = Preferences.YOUTUBE_VISITOR_DATA.value
}