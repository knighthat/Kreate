package app.kreate.android.service

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import app.kreate.android.BuildConfig
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.utils.ConnectivityUtils
import app.kreate.android.utils.DiscordLogger
import app.kreate.android.utils.isLocalFile
import app.kreate.database.models.Album
import app.kreate.database.models.Artist
import app.kreate.util.cleanPrefix
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.service.modern.isLocal
import it.fast4x.rimusic.utils.thumbnail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import me.knighthat.discord.Status
import me.knighthat.discord.Type
import me.knighthat.discord.payload.Activity
import me.knighthat.discord.payload.Identify
import me.knighthat.discord.payload.Presence
import me.knighthat.innertube.Constants
import me.knighthat.logging.Logger
import me.knighthat.utils.ImageProcessor
import me.knighthat.utils.Repository
import me.knighthat.utils.Toaster
import org.jetbrains.annotations.Contract
import timber.log.Timber
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Named
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.time.Duration.Companion.seconds
import me.knighthat.discord.Discord as DiscordLib


// TODO: Localize strings
class Discord @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:Named("plain") private val preferences: SharedPreferences,
    @param:Named("private") private val privatePreferences: SharedPreferences
) {

    companion object {
        private const val APPLICATION_ID = "1370148610158759966"
        private const val TEMP_FILE_HOST = "https://litterbox.catbox.moe/resources/internals/api.php"
        private const val MAX_DIMENSION = 1024                           // Per Discord's guidelines
        private const val MAX_FILE_SIZE_BYTES = 2L * 1024 * 1024     // 2 MB in bytes
        private const val KREATE_IMAGE_URL = "https://i.ibb.co/bgZZ7bFx/discord-rpc-kreate.png"

        private val cachedExternalUrls = ConcurrentHashMap<String, String>()

        const val LOGGING_TAG = "discord-integration"
    }

    private val templateActivity by lazy {
        Activity(
            name = BuildConfig.APP_NAME,
            type = Type.LISTENING,
            createdAt = System.currentTimeMillis(),
            applicationId = APPLICATION_ID,
            buttons = listOf( getAppButton ),
            state = "Browsing",
            details = "Music your way",
        )
    }
    private val getAppButton by lazy {
        Activity.Button(
            label = "Get ${BuildConfig.APP_NAME}",
            url = "${Repository.REPO_URL}/tree/main?tab=readme-ov-file#-installation"
        )
    }

    @Volatile
    private lateinit var loginListener: SharedPreferences.OnSharedPreferenceChangeListener
    @Volatile
    private lateinit var tokenListener: SharedPreferences.OnSharedPreferenceChangeListener
    @Volatile
    private lateinit var smallImage: String

    @Volatile
    private var updateActivityJob: Job? = null
    @Volatile
    private var reconnectJob: Job? = null

    private fun login( token: String ) =
        CoroutineScope( Dispatchers.IO ).launch {
            try {
                DiscordLib.login {
                    this.token = token
                    this.properties = Identify.Properties("Android", "discord-kotlin", Build.DEVICE)
                    this.intents = 0

                    this.initPresence( templateActivity.copy(detailsUrl = getAppButton.url) )
                }
            } catch ( _: UnknownHostException ) {
                delay( 1.seconds )

                if( ConnectivityUtils.isAvailable.value )
                    register()
            } catch ( e: Exception ) {
                Timber.tag( "discord" ).e( e )
                e.message?.also( Toaster::e )
            }
        }

    //<editor-fold defaultstate="collapsed" desc="External image handler">
    private suspend fun uploadArtwork( artworkUri: Uri ): Result<String> =
        runCatching {
            Timber.tag( LOGGING_TAG ).v( "Uploading local artwork \"$artworkUri\" to online bucket" )

            val uploadableUri = ImageProcessor.compressArtwork(
                context,
                artworkUri,
                MAX_DIMENSION,
                MAX_DIMENSION,
                MAX_FILE_SIZE_BYTES
            )

            Timber.tag( LOGGING_TAG ).d(
                if( artworkUri !== uploadableUri ) "Upload compressed version $uploadableUri" else "No compression needed"
            )

            val formData = formData {
                val (mimeType, fileData) = with( context.contentResolver ) {
                    getType( uploadableUri )!! to openInputStream( uploadableUri )!!.readBytes()
                }

                append("reqtype", "fileupload")
                append("time", "1h")
                append("fileToUpload", fileData, Headers.build {
                    append( HttpHeaders.ContentDisposition, "filename=\"${System.currentTimeMillis()}\"" )
                    append( HttpHeaders.ContentType, mimeType )
                })
            }

            NetworkService.client
                          .submitFormWithBinaryData( TEMP_FILE_HOST, formData )
                          .bodyAsText()
        }.onSuccess {
            Timber.tag( LOGGING_TAG ).d( "Local artwork uploaded successfully" )
        }.onFailure {
            Timber.tag( LOGGING_TAG ).e( it, "Error occurs while uploading local artwork" )
        }

    @Contract("_,null->null")
    @OptIn(ExperimentalContracts::class)
    private suspend fun getImageUrl( artworkUri: Uri? ): String? {
        contract {
            returns( null ) implies( artworkUri == null )
        }
        artworkUri ?: return null

        Timber.tag( LOGGING_TAG ).v( "Getting external url for artwork $artworkUri" )

        val artworkCacheKey = artworkUri.toString()
        if( cachedExternalUrls.containsKey( artworkCacheKey ) ) {
            Timber.tag( LOGGING_TAG ).d( "artwork is cached" )
            return cachedExternalUrls[artworkCacheKey]
        }

        val artworkUri =
            if( artworkUri.isLocalFile() )
                uploadArtwork( artworkUri ).getOrNull()
                                           .toString()
            else
                artworkUri.toString()

        return DiscordLib.getExternalImageUrl( artworkUri, APPLICATION_ID )
                         .fold(
                             onSuccess = {
                                 cachedExternalUrls[artworkCacheKey] = it

                                 it
                             },
                             onFailure = {
                                 Toaster.e( R.string.error_failed_to_update_discord_activity )

                                 getAppLogoUrl()
                             }
                         )
    }

    private suspend fun getAppLogoUrl(): String? =
        if ( ::smallImage.isInitialized ) {
            Timber.tag( LOGGING_TAG ).v( "Small image is cached" )

            smallImage
        } else
            DiscordLib.getExternalImageUrl( KREATE_IMAGE_URL, APPLICATION_ID )
                      .onFailure {
                          Timber.tag( LOGGING_TAG ).e( it, "Failed to upload small image" )
                          it.message?.also( Toaster::e )
                      }
                      .getOrNull()
                      ?.also {
                          smallImage = it

                          Timber.tag( LOGGING_TAG ).d( "Small image: $it" )
                      }
    //</editor-fold>

    //<editor-fold desc="Activity processor">
    private suspend fun buildAssets(
        mediaItem: MediaItem,
        artist: Artist?,
        listenToUrl: String?,
        artistUrl: String?
    ): Activity.Assets {
        val thumbnailUrl = mediaItem.mediaMetadata.artworkUri?.thumbnail( MAX_DIMENSION )
        val largeImage = thumbnailUrl?.let { getImageUrl(it) } ?: getAppLogoUrl()

        val smallImage = artist?.thumbnailUrl?.let {
            val sized = it.toUri().thumbnail( MAX_DIMENSION )
            getImageUrl( sized )
        } ?: getAppLogoUrl()

        return Activity.Assets(largeImage, null, listenToUrl, smallImage, null, artistUrl ?: getAppButton.url)
    }

    private fun buildButtons( title: String, listenToUrl: String? ): List<Activity.Button> =
        buildList {
            add( getAppButton )
            if( listenToUrl != null )
                Activity.Button("Listen to $title", listenToUrl)
                        .also( ::add )
        }

    private suspend fun makeActivity( mediaItem: MediaItem, timeStart: Long ): Activity {
        Timber.tag( LOGGING_TAG ).v( "Making new activity from media item ${mediaItem.mediaId} at $timeStart" )

        val metadata = mediaItem.mediaMetadata
        val isLocal = mediaItem.isLocal

        //<editor-fold defaultstate="collapsed" desc="Artists">
        val artists: Artist? = Database.artistTable
                                       .findBySongId( mediaItem.mediaId )
                                       .firstOrNull()
                                       ?.firstOrNull()
        val artistsText = metadata.artist?.toString()?.let( ::cleanPrefix )
            ?: artists?.cleanName()
        // https://music.youtube.com/channel/[channelId]
        val artistUrl = artists?.let { "${Constants.YOUTUBE_MUSIC_URL}/channel/${it.id}" }
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="Album">
        val album: Album? = Database.albumTable
                                    .findBySongId( mediaItem.mediaId )
                                    .firstOrNull()
        val alumTitle = metadata.albumTitle?.toString()?.let( ::cleanPrefix )
            ?: album?.cleanTitle()
        //</editor-fold>
        val title = metadata.title.toString().let( ::cleanPrefix )
        val timestamp = Activity.Timestamp(timeStart, timeStart + (metadata.durationMs ?: 0L))
        val listenToUrl = if( !isLocal ) "${Constants.YOUTUBE_MUSIC_URL}/watch?v=${mediaItem.mediaId}" else null
        val assets = buildAssets( mediaItem, artists, listenToUrl, artistUrl )
        val buttons = buildButtons( title, listenToUrl )

        return Activity(
            name = title,
            type = Type.LISTENING,
            createdAt = timeStart,
            timestamps = timestamp,
            applicationId = APPLICATION_ID,
            details = artistsText,
            detailsUrl = artistUrl,
            state = alumTitle,
            assets = assets,
            buttons = buttons
        )
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Listeners">
    private fun registerLoginListener( loginKey: String, tokenKey: String ) {
        if( ::loginListener.isInitialized ) return

        loginListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if( key != loginKey )
                return@OnSharedPreferenceChangeListener
            if( !prefs.getBoolean( loginKey, false ) ) {
                Timber.tag( LOGGING_TAG ).v( "disabling DiscordRPC" )
                DiscordLib.logout()

                return@OnSharedPreferenceChangeListener
            } else
                Timber.tag( LOGGING_TAG ).v( "enabling DiscordRPC" )

            val token = privatePreferences.getString( tokenKey, null )
            if( !token.isNullOrBlank() )
                login( token )
        }
        preferences.registerOnSharedPreferenceChangeListener( loginListener )
    }

    private fun registerTokenListener( tokenKey: String ) {
        if( ::tokenListener.isInitialized ) return

        tokenListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if( key != tokenKey ) return@OnSharedPreferenceChangeListener

            Timber.tag( LOGGING_TAG ).v( "access token's changed" )

            // When access token's changed, all previous connection must be dropped.
            // If new token is present, attempt to open a new connection with new token.
            DiscordLib.logout()

            val token = prefs.getString( key, null )
            if( !token.isNullOrBlank() )
                login( token )
        }
        privatePreferences.registerOnSharedPreferenceChangeListener( tokenListener )
    }

    private fun registerNetworkListener( loginKey: String, tokenKey: String ) {
        reconnectJob?.cancel()

        reconnectJob = CoroutineScope(Dispatchers.Unconfined).launch {
            var isConnectionLost = false

            @OptIn(FlowPreview::class)
            ConnectivityUtils.isAvailable
                             .distinctUntilChanged { a, b -> a == b}
                             .debounce( 2.seconds )
                             .collectLatest {
                                 if( !it ) {
                                     isConnectionLost = true
                                     return@collectLatest
                                 } else if( !isConnectionLost || !preferences.getBoolean( loginKey, false ) )
                                     // When connectivity becomes unavailable, socket will be
                                     // closed immediately, so it's not handled here.
                                     return@collectLatest

                                 isConnectionLost = false

                                 val token = privatePreferences.getString( tokenKey, null )
                                 if( !token.isNullOrBlank() )
                                     login( token )
                             }
        }
    }
    //</editor-fold>

    fun register() {
        DiscordLib.setClient( NetworkService.client )
        Logger.handler = DiscordLogger()

        val loginKey = Preferences.DISCORD_LOGIN.key
        val tokenKey = Preferences.DISCORD_ACCESS_TOKEN.key

        registerLoginListener( loginKey, tokenKey )
        registerTokenListener( tokenKey )
        registerNetworkListener( loginKey, tokenKey )

        if( DiscordLib.isReady() || !Preferences.isLoggedInToDiscord() )
            return

        // This string should never be null when it's here
        // If default value is returned, something's done wrong
        val token = privatePreferences.getString( tokenKey, null )!!
        login( token )
    }

    fun release() {
        preferences.unregisterOnSharedPreferenceChangeListener( loginListener )
        privatePreferences.unregisterOnSharedPreferenceChangeListener( tokenListener )
        reconnectJob?.cancel()

        DiscordLib.logout()
    }

    //<editor-fold defaultstate="collapsed" desc="Activity handler">
    fun updateMediaItem( mediaItem: MediaItem, timeStart: Long ) {
        if( !DiscordLib.isReady() ) return

        updateActivityJob?.cancel()

        updateActivityJob = CoroutineScope( Dispatchers.IO ).launch {
            delay( 1000 )

            Timber.tag( LOGGING_TAG ).v( "Update activity to new media item" )

            val activity = makeActivity( mediaItem, timeStart )
            DiscordLib.updatePresence {
                Presence(null, listOf( activity ), Status.ONLINE, false)
            }
        }
    }

    fun stop() {
        if( !DiscordLib.isReady() ) return

        Timber.tag( LOGGING_TAG ).v( "Sending stop activity to Discord" )

        CoroutineScope( Dispatchers.IO ).launch {
            DiscordLib.updatePresence {
                Presence(null, listOf( templateActivity ), Status.ONLINE, false)
            }
        }
    }

    fun pause( mediaItem: MediaItem, timeStart: Long ) {
        if( !DiscordLib.isReady() ) return

        updateActivityJob?.cancel()

        updateActivityJob = CoroutineScope( Dispatchers.IO ).launch {
            delay( 1000 )

            Timber.tag( LOGGING_TAG ).v( "Sending pause activity to Discord" )

            val generated = makeActivity( mediaItem, timeStart )

            val activity = templateActivity.copy(
                createdAt = generated.createdAt,
                timestamps = Activity.Timestamp(timeStart, null),
                // "title - artist" if artist is not null, "title" otherwise
                details = generated.name + generated.details?.let { " - $it" },
                state = "⏸ Paused",
                assets = generated.assets,
                buttons = generated.buttons
            )
            DiscordLib.updatePresence {
                Presence(null, listOf( activity ), Status.ONLINE, false)
            }
        }
    }
    //</editor-fold>
}