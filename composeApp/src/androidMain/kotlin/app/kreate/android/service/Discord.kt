package app.kreate.android.service

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import app.kreate.android.BuildConfig
import app.kreate.android.Preferences
import app.kreate.android.utils.DiscordLogger
import io.ktor.client.call.body
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.cleanPrefix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray
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
import me.knighthat.discord.Discord as DiscordLib


// TODO: Localize strings
@RequiresApi(Build.VERSION_CODES.M)
class Discord(private val context: Context) {

    companion object {
        private const val APPLICATION_ID = "1370148610158759966"
        private const val TEMP_FILE_HOST = "https://litterbox.catbox.moe/resources/internals/api.php"
        private const val MAX_DIMENSION = 1024                           // Per Discord's guidelines
        private const val MAX_FILE_SIZE_BYTES = 2L * 1024 * 1024     // 2 MB in bytes
    }

    private val templateActivity by lazy {
        Activity(
            name = BuildConfig.APP_NAME,
            type = Type.LISTENING,
            createdAt = System.currentTimeMillis(),
            applicationId = APPLICATION_ID,
            buttons = listOf( getAppButton )
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
            DiscordLib.login {
                val activity = templateActivity.copy(
                    state = "Browsing",
                    details = "Music your way",
                    detailsUrl = getAppButton.url
                )

                Identify(
                    token = token,
                    properties = Identify.Properties("Android", "discord-kotlin", "discord-kotlin"),
                    intents = 0,
                    presence = Presence(null, listOf( activity ), Status.ONLINE, false)
                )
            }
        }.invokeOnCompletion { err ->
            err?.printStackTrace()
            err?.message?.also( Toaster::e )
        }.dispose()

    @OptIn(ExperimentalSerializationApi::class)
    private suspend fun uploadArtwork( artworkUri: Uri? ): Result<Uri> =
        runCatching {
            val uploadableUri = ImageProcessor.compressArtwork(
                context,
                artworkUri,
                MAX_DIMENSION,
                MAX_DIMENSION,
                MAX_FILE_SIZE_BYTES
            )!!
            if( uploadableUri.scheme!!.startsWith( "http" ) )
                return@runCatching uploadableUri
            
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
                          .toUri()
        }

    private suspend fun getDiscordAssetUri( imageUrl: String, token: String ): String? {
        if ( imageUrl.startsWith( "mp:" ) ) return imageUrl

        return runCatching {
            val postUrl = "https://discord.com/api/v9/applications/${APPLICATION_ID}/external-assets"
            NetworkService.client
                          .post( postUrl ) {
                              header( HttpHeaders.Authorization, token )
                              // For some reasons, this is required.
                              // "java.lang.ClassCastException: kotlinx.serialization.json.JsonObject cannot be cast to io.ktor.http.content.OutgoingContent ]
                              // will be thrown otherwise
                              header( HttpHeaders.ContentType, ContentType.Application.Json )

                              setBody(
                                  // Use this to ensure syntax
                                  // {"urls":[imageUrl]}
                                  buildJsonObject {
                                      putJsonArray( "urls" ) { add( imageUrl ) }
                                  }
                              )
                          }
                          .body<JsonArray>()
                          .firstOrNull()
                          ?.jsonObject["external_asset_path"]
                          ?.jsonPrimitive
                          ?.content
                          ?.let { "mp:$it" }
        }.onFailure {
            it.printStackTrace()
            it.message?.also( Toaster::e )
        }.getOrNull()
    }

    @Contract("_,null->null")
    private suspend fun getImageUrl( token: String, artworkUri: Uri? ): String? =
        uploadArtwork( artworkUri ).fold(
            onSuccess = { getDiscordAssetUri( it.toString(), token ) },
            onFailure = {
                it.printStackTrace()
                it.message?.also( Toaster::e )

                getAppLogoUrl( token )
            }
        )

    private suspend fun getAppLogoUrl( token: String ): String? =
        if ( ::smallImage.isInitialized )
            smallImage
        else
            getDiscordAssetUri( "https://i.ibb.co/3mLGkPwY/app-logo.png", token )
                ?.also { smallImage = it }

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

        CoroutineScope( Dispatchers.IO ).launch {
            val token by Preferences.DISCORD_ACCESS_TOKEN
            val metadata = mediaItem.mediaMetadata

            val title = metadata.title.toString().let( ::cleanPrefix )
            val timestamp = Activity.Timestamp(
                start = timeStart,
                end = timeStart + (metadata.durationMs ?: 0L)
            )
            val artists = metadata.artist?.toString()?.let( ::cleanPrefix )
            val artistUrl: String? = Database.artistTable
                                             .findBySongId( mediaItem.mediaId )
                                             .firstOrNull()
                                             ?.firstOrNull()
                                             ?.let { "${Constants.YOUTUBE_MUSIC_URL}/channel/${it.id}" }
            val album = metadata.albumTitle?.toString()?.let( ::cleanPrefix )
            val assets = Activity.Assets(
                largeImage = getImageUrl( token, metadata.artworkUri ),
                largeText = null,
                largeUrl = metadata.artworkUri.toString(),
                smallImage = getAppLogoUrl( token ),
                smallText = null,
                smallUrl = getAppButton.url
            )
            val buttons = listOf(
                getAppButton,
                Activity.Button(
                    label = "Listen to $title",
                    url = "${Constants.YOUTUBE_MUSIC_URL}/watch?v=${mediaItem.mediaId}"
                )
            )

            val activity = Activity(
                name = title,
                type = Type.LISTENING,
                createdAt = timeStart,
                timestamp = timestamp,
                applicationId = APPLICATION_ID,
                details = artists,
                detailsUrl = artistUrl,
                state = album,
                assets = assets,
                buttons = buttons
            )

            DiscordLib.updatePresence {
                Presence(null, listOf( activity ), Status.ONLINE, false)
            }
        }
    }
}