package app.kreate.android.service.innertube

import android.content.Context
import app.kreate.android.Preferences
import co.touchlab.kermit.Logger
import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.InnerTubeLogEvent
import com.metrolist.innertubex.InnerTubeLogLevel
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.ExtractedStream
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.StreamResolveException
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.TokenProviderCapabilities
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.extraction.strategy.PoTokenProviderKind
import com.metrolist.innertubex.models.AccountInfo
import com.metrolist.innertubex.models.YouTubeClient
import com.metrolist.innertubex.models.response.AccountMenuResponse
import com.metrolist.music.utils.potoken.PoTokenGenerator
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import it.fast4x.rimusic.enums.AudioQualityFormat
import it.fast4x.rimusic.service.LoginRequiredException
import it.fast4x.rimusic.service.PlayableFormatNotFoundException
import it.fast4x.rimusic.service.UnplayableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import com.metrolist.innertubex.extraction.PoTokenResult as InnerTubePoToken


/**
 * Kreate's gateway to `innertubex`. It owns the library's session
 * (cookie, visitor data, data sync id) and the stream extractor.
 */
object InnerTubeX: KoinComponent {

    private const val LOGGING_TAG = "InnerTubeX"
    private const val LIBRARY_BROWSE_ID = "FEmusic_library_landing"

    private val logger = Logger.withTag( LOGGING_TAG )

    private val libraryLogger = com.metrolist.innertubex.InnerTubeLogger { event: InnerTubeLogEvent ->
        // Library guarantees sensitive values are never put in the event
        val message = "${event.message} ${event.details}"
        when( event.level ) {
            InnerTubeLogLevel.DEBUG -> logger.d { message }
            InnerTubeLogLevel.INFO  -> logger.i { message }
            InnerTubeLogLevel.WARN  -> logger.w { message }
            InnerTubeLogLevel.ERROR -> logger.e { message }
        }
    }

    private val httpClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            engine {
                preconfigured = get<OkHttpClient>().newBuilder()
                                                   .followRedirects( false )
                                                   .followSslRedirects( false )
                                                   .build()
            }
            install( HttpTimeout ) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 15_000
            }
            install( ContentNegotiation ) {
                json( Json { ignoreUnknownKeys = true } )
            }
            install( ContentEncoding ) {
                gzip( 1f )
                deflate( 0.9f )
            }
            followRedirects = false
        }
    }

    val innerTube: InnerTube by lazy {
        InnerTube( httpClient, logger = libraryLogger ).also {
            it.applyAccount()
        }
    }

    private val extractor: InnerTubeExtractor by lazy {
        val cipher = YouTubeCipherService( httpClient, logger = libraryLogger )
        InnerTubeExtractor(
            configParser = YtConfigParserImpl(
                httpClient,
                innerTube,
                RemotePlayerConfigStore( httpClient, PlayerConfigRepository.disabled(), libraryLogger ),
                libraryLogger,
                cipher
            ),
            cipherService = cipher,
            innerTube = innerTube,
            tokenProvider = WebViewTokenProvider,
            logger = libraryLogger
        )
    }

    /**
     * Copy account's credentials from [Preferences] to the library's session.
     * Must be called whenever user logs in or out.
     */
    fun InnerTube.applyAccount() {
        cookie = Preferences.YOUTUBE_COOKIES.value.takeIf( String::isNotBlank )
        visitorData = Preferences.YOUTUBE_VISITOR_DATA.value.takeIf( String::isNotBlank )
        dataSyncId = Preferences.YOUTUBE_SYNC_ID.value.takeIf( String::isNotBlank )
    }

    /**
     * Resolve a playable audio stream of [videoId].
     *
     * HLS, SABR and bounded-range streams are excluded because the player
     * only fetches plain progressive urls.
     */
    @Throws(LoginRequiredException::class, UnplayableException::class, PlayableFormatNotFoundException::class)
    suspend fun extractStream( videoId: String, audioQuality: AudioQualityFormat ): ExtractedStream {
        val hints = ContentHints().withStreamCapabilities(
            allowHls = false,
            allowSabr = false,
            allowBoundedRange = false
        )
        val quality = when( audioQuality ) {
            AudioQualityFormat.Auto -> AudioQuality.AUTO
            AudioQualityFormat.High -> AudioQuality.HIGH
            AudioQualityFormat.Low  -> AudioQuality.LOW
        }

        try {
            return extractor.extract( videoId, hints, audioQuality = quality )
                            ?: throw PlayableFormatNotFoundException()
        } catch( e: StreamResolveException ) {
            throw when( e.reason ) {
                StreamResolveException.Reason.AGE_RESTRICTED    -> LoginRequiredException( e.message, e )
                StreamResolveException.Reason.NETWORK           -> e.cause ?: e
                else                                            -> UnplayableException( e.message, e )
            }
        }
    }

    suspend fun accountInfo(): Result<AccountInfo> = runCatching {
        innerTube.accountMenu( YouTubeClient.WEB_REMIX )
                 .body<AccountMenuResponse>()
                 .actions
                 .firstNotNullOfOrNull { it.openPopupAction }
                 ?.popup
                 ?.multiPageMenuRenderer
                 ?.header
                 ?.activeAccountHeaderRenderer
                 ?.toAccountInfo()
                 ?: throw NoSuchElementException( "Account info is missing from response" )
    }

    /**
     * Fetch items (albums, artists and playlists) from user's YouTube Music library.
     */
    suspend fun libraryItems(): Result<List<LibraryItem>> = runCatching {
        innerTube.browse( YouTubeClient.WEB_REMIX, browseId = LIBRARY_BROWSE_ID, setLogin = true )
                 .body<JsonElement>()
                 .findAll( "musicTwoRowItemRenderer" )
                 .mapNotNull( ::parseLibraryItem )
                 .toList()
    }

    //<editor-fold desc="Library parser">
    sealed interface LibraryItem {
        val id: String
        val title: String
        val thumbnail: String?

        data class Album(
            override val id: String,
            override val title: String,
            override val thumbnail: String?,
            val year: String?,
            val artists: List<String>,
            val playlistId: String?
        ): LibraryItem {
            val shareLink: String
                get() = playlistId?.let { "https://music.youtube.com/playlist?list=$it" }
                        ?: "https://music.youtube.com/browse/$id"
        }

        data class Artist(
            override val id: String,
            override val title: String,
            override val thumbnail: String?
        ): LibraryItem

        data class Playlist(
            override val id: String,
            override val title: String,
            override val thumbnail: String?,
            val songCountText: String?
        ): LibraryItem
    }

    private fun JsonElement.findAll( key: String ): Sequence<JsonObject> = sequence {
        when( this@findAll ) {
            is JsonObject -> forEach { (k, v) ->
                if( k == key && v is JsonObject )
                    yield( v )
                else
                    yieldAll( v.findAll( key ) )
            }
            is JsonArray -> forEach { yieldAll( it.findAll( key ) ) }
            else -> {}
        }
    }

    private fun JsonElement?.obj( vararg path: String ): JsonObject? =
        path.fold( this as? JsonObject ) { acc, key -> acc?.get( key ) as? JsonObject }

    private fun JsonObject.texts( key: String ): List<String> =
        (this[key].obj()?.get( "runs" ) as? JsonArray)
            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
            .orEmpty()

    private fun parseLibraryItem( renderer: JsonObject ): LibraryItem? {
        val browseEndpoint = renderer.obj( "navigationEndpoint", "browseEndpoint" ) ?: return null
        val browseId = browseEndpoint["browseId"]?.jsonPrimitive?.contentOrNull ?: return null
        val pageType = browseEndpoint.obj( "browseEndpointContextSupportedConfigs", "browseEndpointContextMusicConfig" )
                                    ?.get( "pageType" )?.jsonPrimitive?.contentOrNull
        val titles = renderer.texts( "title" )
        val title = titles.firstOrNull() ?: return null
        val subtitles = renderer.texts( "subtitle" )
        val thumbnail = renderer.obj( "thumbnailRenderer", "musicThumbnailRenderer", "thumbnail" )
                                ?.get( "thumbnails" )?.let { it as? JsonArray }
                                ?.lastOrNull()?.jsonObject?.get( "url" )?.jsonPrimitive?.contentOrNull

        return when( pageType ) {
            "MUSIC_PAGE_TYPE_ALBUM", "MUSIC_PAGE_TYPE_AUDIOBOOK" ->
                LibraryItem.Album(
                    id = browseId,
                    title = title,
                    thumbnail = thumbnail,
                    year = subtitles.lastOrNull()?.takeIf { it.toIntOrNull() != null },
                    // Subtitle looks like: [type, " • ", artist, " • ", year]
                    artists = subtitles.drop( 1 ).filter { it.isNotBlank() && it != " • " && it.toIntOrNull() == null },
                    playlistId = renderer["thumbnailOverlay"]
                                         ?.findAll( "watchPlaylistEndpoint" )
                                         ?.firstOrNull()
                                         ?.get( "playlistId" )?.jsonPrimitive?.contentOrNull
                )
            "MUSIC_PAGE_TYPE_PLAYLIST" ->
                LibraryItem.Playlist(
                    id = browseId.removePrefix( "VL" ),
                    title = title,
                    thumbnail = thumbnail,
                    songCountText = subtitles.lastOrNull()
                )
            // Podcast hosts use USER_CHANNEL
            "MUSIC_PAGE_TYPE_ARTIST", "MUSIC_PAGE_TYPE_USER_CHANNEL" ->
                LibraryItem.Artist(
                    id = browseId,
                    title = titles.lastOrNull() ?: title,
                    thumbnail = thumbnail
                )
            else -> null
        }
    }
    //</editor-fold>

    /**
     * Bridge to Kreate's WebView-based PO token generator.
     */
    private object WebViewTokenProvider: TokenProvider {

        private val generator by lazy { PoTokenGenerator( get<Context>() ) }

        override val capabilities = TokenProviderCapabilities(
            providers = setOf( PoTokenProviderKind.WEB_BOTGUARD ),
            usesWebView = true
        )

        override suspend fun getPoToken( videoId: String, visitorData: String, cookie: String? ): InnerTubePoToken? =
            try {
                withContext( Dispatchers.IO ) {
                    generator.getWebClientPoToken( videoId, visitorData )
                }?.let {
                    InnerTubePoToken( it.playerRequestPoToken, it.streamingDataPoToken, visitorData )
                }
            } catch( e: CancellationException ) {
                throw e
            } catch( e: Exception ) {
                logger.w( e ) { "Failed to generate PO token" }
                null
            }
    }
}
