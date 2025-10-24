package app.kreate.android.service

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import app.kreate.android.BuildConfig
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.utils.ConnectivityUtils
import app.kreate.android.utils.DiscordLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.cleanPrefix
import it.fast4x.rimusic.models.Artist
import it.fast4x.rimusic.utils.thumbnail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import javax.inject.Inject
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.time.Duration.Companion.seconds
import me.knighthat.discord.Discord as DiscordLib


// TODO: Localize strings
@RequiresApi(Build.VERSION_CODES.M)
class Discord @Inject constructor(
    @param:ApplicationContext private val context: Context
) {

    companion object {
        private const val APPLICATION_ID = "1370148610158759966"
        private const val TEMP_FILE_HOST = "https://litterbox.catbox.moe/resources/internals/api.php"
        private const val MAX_DIMENSION = 1024                           // Per Discord's guidelines
        private const val MAX_FILE_SIZE_BYTES = 2L * 1024 * 1024     // 2 MB in bytes
        private const val KREATE_IMAGE_URL = "https://i.ibb.co/bgZZ7bFx/discord-rpc-kreate.png"

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

    private lateinit var smallImage: String

    init {
        Preferences.preferences.registerOnSharedPreferenceChangeListener { prefs, key ->
            val loginPrefKey = Preferences.DISCORD_LOGIN.key
            val atPrefKey = Preferences.DISCORD_ACCESS_TOKEN.key

            when( key ) {
                loginPrefKey -> {
                    if ( !prefs.getBoolean( loginPrefKey, false ) ) {
                        release()
                        return@registerOnSharedPreferenceChangeListener
                    }

                    val token = prefs.getString( atPrefKey, "" )
                    if( !token.isNullOrBlank() )
                        login( token )
                }

                atPrefKey ->
                    try {
                        release()

                        val token = prefs.getString( atPrefKey, "" )
                        if( !token.isNullOrBlank() )
                            login( token )
                    } catch( e: Exception ) {
                        e.printStackTrace()
                        e.message?.also( Toaster::e )
                    }
            }
        }
    }

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

        val scheme = artworkUri.scheme?.lowercase().orEmpty()
        val artworkUri =
            if( scheme == ContentResolver.SCHEME_FILE || scheme == ContentResolver.SCHEME_CONTENT )
                uploadArtwork( artworkUri ).getOrNull()
                                           ?.let( String::toUri )
            else
                artworkUri

        return DiscordLib.getExternalImageUrl( artworkUri.toString(), APPLICATION_ID )
                         .fold(
                             onSuccess = { it },
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

    private suspend fun makeActivity( mediaItem: MediaItem, timeStart: Long ): Activity {
        Timber.tag( LOGGING_TAG ).v( "Making new activity from media item ${mediaItem.mediaId} at $timeStart" )

        val metadata = mediaItem.mediaMetadata

        val title = metadata.title.toString().let( ::cleanPrefix )
        val timestamp = Activity.Timestamp(
            start = timeStart,
            end = timeStart + (metadata.durationMs ?: 0L)
        )
        val artistsText = metadata.artist?.toString()?.let( ::cleanPrefix )
        val artists: Artist? = Database.artistTable
                                       .findBySongId( mediaItem.mediaId )
                                       .firstOrNull()
                                       ?.firstOrNull()
        // https://music.youtube.com/channel/[channelId]
        val artistUrl = artists?.let { "${Constants.YOUTUBE_MUSIC_URL}/channel/${it.id}" }
        val album = metadata.albumTitle?.toString()?.let( ::cleanPrefix )
        val listenToUrl = "${Constants.YOUTUBE_MUSIC_URL}/watch?v=${mediaItem.mediaId}"
        val assets = Activity.Assets(
            // [thumbnail] call only modifies youtube's thumbnail urls
            largeImage = getImageUrl( metadata.artworkUri.thumbnail(MAX_DIMENSION) ),
            largeText = null,
            largeUrl = listenToUrl,
            smallImage = artists?.thumbnailUrl
                                .thumbnail( MAX_DIMENSION )
                                ?.let {
                                    try {
                                        it.toUri()
                                    } catch ( _: Exception ) {
                                        null
                                    }
                                }
                                ?.let {
                                    Timber.tag( LOGGING_TAG ).v( "Using artist thumbnail as small image" )
                                    getImageUrl( it )
                                }
                                ?: getAppLogoUrl(),
            smallText = null,
            smallUrl = artistUrl ?: getAppButton.url
        )
        val buttons = listOf(
            getAppButton,
            Activity.Button(
                label = "Listen to $title",
                url = listenToUrl
            )
        )

        return Activity(
            name = title,
            type = Type.LISTENING,
            createdAt = timeStart,
            timestamps = timestamp,
            applicationId = APPLICATION_ID,
            details = artistsText,
            detailsUrl = artistUrl,
            state = album,
            assets = assets,
            buttons = buttons
        )
    }

    fun register() {
        val token by Preferences.DISCORD_ACCESS_TOKEN
        if( DiscordLib.isReady()
            || !Preferences.DISCORD_LOGIN.value
            || token.isBlank()
        ) return

        DiscordLib.setClient( NetworkService.client )
        Logger.handler = DiscordLogger()

        login( token )
    }

    fun release() = DiscordLib.logout()

    fun updateMediaItem( mediaItem: MediaItem, timeStart: Long ) {
        if( !DiscordLib.isReady() ) return

        Timber.tag( LOGGING_TAG ).v( "Update activity to new media item" )

        CoroutineScope( Dispatchers.IO ).launch {
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

        Timber.tag( LOGGING_TAG ).v( "Sending pause activity to Discord" )

        CoroutineScope( Dispatchers.IO ).launch {
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
}