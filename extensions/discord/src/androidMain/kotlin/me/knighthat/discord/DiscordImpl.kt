package me.knighthat.discord

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
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
import kizzy.gateway.DiscordWebSocket
import kizzy.gateway.DiscordWebSocketImpl
import kizzy.gateway.entities.presence.Activity
import kizzy.gateway.entities.presence.Assets
import kizzy.gateway.entities.presence.Presence
import kizzy.gateway.entities.presence.Timestamps
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray
import me.knighthat.utils.ImageProcessor
import me.knighthat.utils.isLocalFile
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndUpdate
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract


@ExperimentalAtomicApi
class DiscordImpl : Discord, KoinComponent {

    companion object {
        private const val LOGGING_TAG = "DiscordRPC"
        private const val APPLICATION_ID = "1370148610158759966"
        private const val TEMP_FILE_HOST = "https://litterbox.catbox.moe/resources/internals/api.php"
        private const val MAX_DIMENSION = 1024                           // Per Discord's guidelines
        private const val MAX_FILE_SIZE_BYTES = 2L * 1024 * 1024     // 2 MB in bytes
        private const val KREATE_IMAGE_URL = "https://i.ibb.co/v4CzX3kT/discord-rpc-kreate.jpg"
        private const val API_VERSION = "10"

        private val cachedExternalUrls = ConcurrentHashMap<String, String>()
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineName(LOGGING_TAG))
    }

    private val client: HttpClient by inject()
    private val context: Context by inject()
    private val logger = Logger.withTag( LOGGING_TAG )
    private val _session = AtomicReference<DiscordWebSocket?>(null)
    private val _token = MutableStateFlow<String?>(null)
    private val _isActive = AtomicBoolean(false)

    @Volatile
    private lateinit var smallImage: String

    init { onTokenChanged() }

    //<editor-fold defaultstate="collapsed" desc="External image handler">
    private suspend fun uploadLocalArtwork( artworkUri: Uri): Result<String> =
        runCatching {
            logger.v { "Uploading local artwork \"$artworkUri\" to online bucket" }

            val uploadableUri = ImageProcessor.compressArtwork(
                context,
                artworkUri,
                MAX_DIMENSION,
                MAX_DIMENSION,
                MAX_FILE_SIZE_BYTES
            )

            logger.d {
                if( artworkUri !== uploadableUri )
                    "Upload compressed version $uploadableUri"
                else
                    "No compression needed"
            }

            val formData = formData {
                val (mimeType, fileData) = with( context.contentResolver ) {
                    getType( uploadableUri )!! to openInputStream( uploadableUri )!!.readBytes()
                }
                val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType( mimeType )

                append("reqtype", "fileupload")
                append("time", "1h")
                append("fileToUpload", fileData, Headers.build {
                    append( HttpHeaders.ContentDisposition, "filename=\"${System.currentTimeMillis()}.$extension\"" )
                    append( HttpHeaders.ContentType, mimeType )
                })
            }

            client.submitFormWithBinaryData( TEMP_FILE_HOST, formData )
                .bodyAsText()
        }.onSuccess {
            logger.d { "Local artwork uploaded successfully" }
        }.onFailure {
            logger.e( it ) { "Error occurs while uploading local artwork" }
        }

    private suspend fun submitArtworkUrlToDiscord( imageUrl: String, applicationId: String ): Result<String> =
        runCatching {
            Logger.v { "Posting $imageUrl to get external url" }

            if ( imageUrl.startsWith( "mp:" ) ) {
                Logger.w { "imageUrl already an external url" }
                return@runCatching imageUrl
            }

            @SuppressLint("UseKtx")         // Lib not available
            val scheme = Uri.parse( imageUrl ).scheme
            require(
                scheme.equals( "http", true )
                        || scheme.equals( "https", true )
            ) { "Only \"http\" and \"https\" are supported!" }

            val postUrl = "https://discord.com/api/v$API_VERSION/applications/$applicationId/external-assets"
            val response = client.post( postUrl ) {
                header( HttpHeaders.Authorization, _token.value )
                // For some reasons, this is required.
                // "java.lang.ClassCastException: kotlinx.serialization.json.JsonObject cannot be cast to io.ktor.http.content.OutgoingContent"
                // will be thrown otherwise
                header( HttpHeaders.ContentType, ContentType.Application.Json )

                setBody(
                    // Use this to ensure syntax
                    // {"urls":[imageUrl]}
                    buildJsonObject {
                        putJsonArray( "urls" ) { add( imageUrl ) }
                    }
                )
            }.body<JsonArray>()

            response.firstNotNullOf { it.jsonObject["external_asset_path"] }
                .jsonPrimitive
                .content
                .let { "mp:$it" }
        }.onSuccess {
            Logger.d { "External url: $it" }
        }.onFailure {
            Logger.e( it ) { "Error occurs while posting imageUrl for external url" }
        }


    @OptIn(ExperimentalContracts::class)
    private suspend fun getImageUrl( artworkUri: Uri? ): String? {
        contract {
            returns( null ) implies( artworkUri == null )
        }
        if( artworkUri == null || artworkUri.toString().isBlank() )
            return if( ::smallImage.isInitialized ) smallImage else null

        logger.v { "Getting external url for artwork $artworkUri" }

        val artworkCacheKey = artworkUri.toString()
        if( cachedExternalUrls.containsKey( artworkCacheKey ) ) {
            logger.d { "artwork is cached" }
            return cachedExternalUrls[artworkCacheKey]
        }

        val artworkUri =
            if( artworkUri.isLocalFile() )
                uploadLocalArtwork( artworkUri ).getOrNull()
                                                .toString()
            else
                artworkUri.toString()

        return submitArtworkUrlToDiscord( artworkUri, APPLICATION_ID )
            .fold(
                onSuccess = {
                    logger.v { "Discord assigns $it as image url" }

                    cachedExternalUrls[artworkCacheKey] = it

                    it
                },
                onFailure = {
                    logger.e( it ) { "Upload image to Discord failed" }
                    getAppLogoUrl()
                }
            )
    }

    private suspend fun getAppLogoUrl(): String? =
        if ( ::smallImage.isInitialized ) {
            logger.v { "Small image is cached" }

            smallImage
        } else
            submitArtworkUrlToDiscord( KREATE_IMAGE_URL, APPLICATION_ID )
                .onFailure {
                    logger.e( it ) { "Failed to upload small image" }
                }
                .getOrNull()
                ?.also {
                    smallImage = it

                    logger.d { "Small image: $it" }
                }
    //</editor-fold>

    private fun onTokenChanged() = scope.launch {
        _token.collectLatest { token ->
            logout()

            logger.v { "Starting new session..." }
            if( token.isNullOrBlank() ) {
                logger.e { "Cannot start session with null or empty token" }
                return@collectLatest
            }

            try {
                val session = DiscordWebSocketImpl(token, DiscordLogger)
                _session.store( session )

                session.connect()
            } catch( e: Exception ) {
                logger.e( e ) { "Session closed unexpectedly!" }
            }
        }
    }

    private suspend fun makeAssets( largeImage: Uri?, smallImage: Uri? ): Assets {
        val largeImage = getImageUrl( largeImage ) ?: getAppLogoUrl()
        val smallImage = if( largeImage == getAppLogoUrl() && smallImage == null )
            null
        else
            getImageUrl( smallImage ) ?: getAppLogoUrl()

        return Assets(largeImage, smallImage)
    }

    override fun login( token: String ) {
        val isSimilarToken = _token.value == token
        if( isSimilarToken && _isActive.load() ) {
            logger.w { "Not log in with the same token." }
            return
        }

        _token.value = token
    }

    override suspend fun logout(): Boolean {
        logger.v { "Closing connection to Discord" }

        try {
            val existingConnection = _session.fetchAndUpdate { null }
            existingConnection?.close()

            return existingConnection != null
        } catch( e: Exception ) {
            logger.e( e ) { "Failed to close connection" }

            return false
        }
    }

    override suspend fun listening( song: ListeningActivity ) {
        val session = _session.load()
        if( session == null || !session.isWebSocketConnected() ) {
            logger.v { "Session not available" }
            return
        }

        try {
            val assets = makeAssets( song.thumbnailUrl, song.artistThumbnailUrl )
            val activity = Activity(
                name = "Kreate",
                state = song.artistName,
                details = song.songName,
                type = Type.LISTENING,
                timestamps = Timestamps(song.timeStart + song.duration, song.timeStart),
                assets = assets,
                applicationId = APPLICATION_ID,
                url = "https://github.com/knighthat/Kreate"
            )
            val presence = Presence(listOf(activity), false)
            session.sendActivity( presence )
        } catch( e: Exception ) {
            logger.e( e ) { "Send listening activity failed!" }
        }
    }

    override suspend fun pause( song: ListeningActivity ) {
        val session = _session.load()
        if( session == null || !session.isWebSocketConnected() ) {
            logger.v { "Session not available" }
            return
        }

        try {
            val assets = makeAssets( song.thumbnailUrl, song.artistThumbnailUrl )
            val activity = Activity(
                name = "Kreate",
                state = "Pausing",
                details = song.songName,
                type = Type.LISTENING,
                timestamps = Timestamps(null, song.timeStart),
                assets = assets,
                applicationId = APPLICATION_ID,
                url = "https://github.com/knighthat/Kreate"
            )
            val presence = Presence(listOf(activity), true, System.currentTimeMillis())
            session.sendActivity( presence )
        } catch( e: Exception ) {
            logger.e( e ) { "Send pause activity failed!" }
        }
    }

    override suspend fun reset() {
        val session = _session.load()
        if( session == null || !session.isWebSocketConnected() ) {
            logger.v { "Session not available" }
            return
        }

        try {
            val assets = Assets(
                largeImage = getAppLogoUrl(),
                smallImage = null
            )
            val now = System.currentTimeMillis()
            val activity = Activity(
                name = "Kreate",
                details = "Music your way",
                state = "Browsing",
                type = Type.LISTENING,
                timestamps = Timestamps(null, now),
                assets = assets,
                applicationId = APPLICATION_ID
            )
            val presence = Presence(listOf(activity), true, now)
            session.sendActivity( presence )
        } catch( e: Exception ) {
            logger.e( e ) { "Reset activity failed!" }
        }
    }
}