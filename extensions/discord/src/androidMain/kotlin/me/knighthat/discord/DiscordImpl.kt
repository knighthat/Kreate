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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.putJsonArray
import me.knighthat.exception.SessionNotAvailableException
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
    private val lock = Mutex()
    private val smallImage by lazy(::getAppLogoUrl)
    private val _session = AtomicReference<DiscordWebSocket?>(null)
    private val _token = MutableStateFlow<String?>(null)
    private val _isActive = AtomicBoolean(false)

    @Volatile
    private var previousPresence: Presence? = null
    @Volatile
    private var state: State = State.BROWSING

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
            return smallImage

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
            .onSuccess {
                logger.v { "Discord assigns $it as image url" }
                cachedExternalUrls[artworkCacheKey] = it
            }
            .onFailure {
                logger.e( it ) { "Upload image to Discord failed" }
            }
            .getOrDefault( smallImage )
    }

    /**
     * This function shouldn't be called anywhere other than initialization of [smallImage]
     */
    private fun getAppLogoUrl(): String? = runBlocking {
        submitArtworkUrlToDiscord( KREATE_IMAGE_URL, APPLICATION_ID )
            .onSuccess {
                logger.d { "Small image: $it" }
            }
            .onFailure {
                logger.e( it ) { "Failed to upload app logo!" }
            }
            .getOrNull()
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
                val session = lock.withLock {
                    DiscordWebSocketImpl(token, DiscordLogger)
                        .also( _session::store )
                }

                session.connect()
            } catch( e: Exception ) {
                logger.e( e ) { "Session closed unexpectedly!" }
            }
        }
    }

    private suspend fun makeAssets( largeImage: Uri?, smallImage: Uri? ): Assets {
        val largeImage = getImageUrl( largeImage )
        val smallImage = if( largeImage == this.smallImage && smallImage == null )
            null
        else
            getImageUrl( smallImage )

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
            // Obtaining the lock here does 2 main things:
            // - Prevent new update from being sent to Discord
            // - Wait for all update to finish before disconnecting
            val existingConnection = lock.withLock {
                _session.fetchAndUpdate { null }
                        ?.also(DiscordWebSocket::close )
            }

            previousPresence = null
            state = State.BROWSING

            return existingConnection != null
        } catch( e: Exception ) {
            logger.e( e ) { "Failed to close connection" }

            return false
        }
    }

    override suspend fun listening( song: ListeningActivity ) {
        try {
            lock.withLock {
                /**
                 * At first, we only check if websocket is established.
                 * This is quick and reliable enough for the program
                 * to start setting up other parts such as uploading artwork,
                 * making [Activity], etc. These don't need websocket connection to work.
                 */
                val session = _session.load() ?: throw SessionNotAvailableException()
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

                // Update listening state can be triggered due to many factors:
                // changing song, seeking to new position, skipping, etc.
                // So we only check whether the request is duplicated.
                if( presence == previousPresence ) {
                    logger.w { "Duplicate listening activity detected. Skipping..." }
                    return@withLock
                }

                // Now, before sending this request away, we must validate session.
                if( session.isWebSocketConnected() ) {
                    session.sendActivity( presence )
                    state = State.PLAYING       // Only update state if activity sent successfully
                    previousPresence = presence
                } else
                    throw SessionNotAvailableException()
            }
        } catch ( e: Exception ) {
            if( e is SessionNotAvailableException )
                logger.w { "Session not available!" }
            else
                logger.e( e ) { "Send listening activity failed!" }
        }
    }

    override suspend fun pause( song: ListeningActivity ) {
        try {
            lock.withLock {
                /**
                 * At first, we only check if websocket is established.
                 * This is quick and reliable enough for the program
                 * to start setting up other parts such as uploading artwork,
                 * making [Activity], etc. These don't need websocket connection to work.
                 */
                val session = _session.load() ?: throw SessionNotAvailableException()
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

                // For pausing, only enact if it's not previously
                val previousState = previousPresence?.activities?.firstOrNull()?.state
                if( previousState == "Pausing" ) {
                    logger.w { "Duplicate pausing activity detected. Skipping..." }
                    return@withLock
                }

                // Now, before sending this request away, we must validate session.
                if( session.isWebSocketConnected() ) {
                    session.sendActivity( presence )
                    state = State.PAUSING       // Only update state if activity sent successfully
                    previousPresence = presence
                } else
                    throw SessionNotAvailableException()
            }
        } catch( e: Exception ) {
            if( e is SessionNotAvailableException )
                logger.w { "Session not available!" }
            else
                logger.e( e ) { "Send pause activity failed!" }
        }
    }

    override suspend fun reset() {
        try {
            lock.withLock {
                /**
                 * At first, we only check if websocket is established.
                 * This is quick and reliable enough for the program
                 * to start setting up other parts such as uploading artwork,
                 * making [Activity], etc. These don't need websocket connection to work.
                 */
                val session = _session.load() ?: throw SessionNotAvailableException()
                val assets = Assets(
                    largeImage = smallImage,
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

                // Similarly to pausing, resetting presence requires
                // user not in the state already.
                val previousState = previousPresence?.activities?.firstOrNull()?.state
                if( previousState == "Browsing") {
                    logger.w { "Duplicate browsing activity detected. Skipping..." }
                    return@withLock
                }

                // Now, before sending this request away, we must validate session.
                if( session.isWebSocketConnected() ) {
                    session.sendActivity( presence )
                    state = State.BROWSING       // Only update state if activity sent successfully
                    previousPresence = presence
                } else
                    throw SessionNotAvailableException()
            }
        } catch( e: Exception ) {
            if( e is SessionNotAvailableException )
                logger.w { "Session not available!" }
            else
                logger.e( e ) { "Reset activity failed!" }
        }
    }

    enum class State {

        BROWSING, PAUSING, PLAYING;
    }
}