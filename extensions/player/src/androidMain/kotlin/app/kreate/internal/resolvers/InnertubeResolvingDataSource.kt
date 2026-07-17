@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.kreate.internal.resolvers

import android.content.Context
import android.net.ConnectivityManager
import androidx.core.content.getSystemService
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import app.kreate.database.Database
import app.kreate.database.models.Format
import app.kreate.database.upsert
import app.kreate.di.CHUNK_LENGTH
import app.kreate.exceptions.LoginRequiredException
import app.kreate.exceptions.MissingDecipherKeyException
import app.kreate.exceptions.NoInternetException
import app.kreate.exceptions.PlayableFormatNotFoundException
import app.kreate.exceptions.UnplayableException
import app.kreate.gateway.innertube.YouTube
import app.kreate.gateway.innertube.responses.PlayerResponse
import app.kreate.utils.CharUtils
import app.kreate.utils.Toaster
import app.kreate.utils.getNetworkMonitor
import co.touchlab.kermit.Logger
import com.grack.nanojson.JsonWriter
import com.metrolist.music.utils.YTPlayerUtils
import com.metrolist.music.utils.cipher.CipherDeobfuscator
import io.ktor.client.HttpClient
import io.ktor.client.request.head
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess
import io.ktor.http.parseQueryString
import io.ktor.util.network.UnresolvedAddressException
import it.fast4x.rimusic.enums.AudioQualityFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.json.Json
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.error_failed_to_fetch_songs_info
import org.koin.java.KoinJavaComponent.get
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicReference
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import com.metrolist.innertube.YouTube as MetrolistYouTube


private const val METHOD_ANDROID = 1
private const val METHOD_IOS = 2

/**
 * Store id of song just added to the database.
 * This is created to reduce load to Room
 */
private val justInserted = AtomicReference("")
private val cachedStreamUrl = ConcurrentHashMap<String, YTPlayerUtils.PlaybackData>()
private val logger = Logger.withTag("dataspec")
private val jsonParser =
    Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        useArrayPolymorphism = true
        explicitNulls = false
    }
private val scope: CoroutineScope
    get() = get( CoroutineScope::class.java )

/**
 * Acts as a lock to keep [upsertSongFormat] from starting before
 * [upsertSongInfo] finishes.
 */
private var databaseWorker: Job = Job()

//<editor-fold desc="Database handlers">
/**
 * Info includes:
 * - Titles
 * - Artist(s)
 * - Album
 * - Thumbnails
 * - Duration
 *
 * ### If song IS already inside database
 *
 * It'll replace unmodified columns with fetched data
 *
 * ### If song IS NOT already inside database
 *
 * New record will be created and insert into database
 *
 */
private fun upsertSongInfo( videoId: String ) {       // Use this to prevent suspension of thread while waiting for response from YT
    // Skip adding if it's just added in previous call
    if( videoId == justInserted.load() || !getNetworkMonitor().value )
        return

    logger.v { "fetching and upserting $videoId's information to the database" }

    databaseWorker = scope.launch {
        get<YouTube>(YouTube::class.java)
            .getSongBasicInfo( videoId )
            .onSuccess{
                logger.v { "$videoId's information successfully found and parsed" }

                Database.upsert( it )

                logger.d { "$videoId's information successfully upserted to the database" }
            }
            .onFailure {
                logger.e( "failed to upsert $videoId's information to database", it )
                Toaster.e(Res.string.error_failed_to_fetch_songs_info )
            }
    }

    // Must not modify [JustInserted] to [upsertSongFormat] let execute later
}

/**
 * Upsert provided format to the database
 */
private fun upsertSongFormat( videoId: String, format: PlayerResponse.StreamingData.Format ) {
    // Skip adding if it's just added in previous call
    if( videoId == justInserted.load() ) return

    logger.v { "upserting format ${format.itag} of song $videoId to the database" }

    CoroutineScope(Dispatchers.IO ).launch {
        // Wait until this job is finish to make sure song's info
        // is in the database before continuing
        databaseWorker.join()

        Database.asyncTransaction {
            formatTable.upsert(
                Format(
                    videoId,
                    format.itag.toInt(),
                    format.mimeType,
                    format.bitrate.toLong(),
                    format.contentLength?.toLong(),
                    format.lastModified.toLong(),
                    format.loudnessDb
                )
            )

            logger.d { "$videoId is successfully upserted to the database" }

            // Format must be added successfully before setting variable
            justInserted.store( videoId )
        }
    }
}
//</editor-fold>
//<editor-fold desc="Extractors">
@Throws(PlayableFormatNotFoundException::class)
private fun extractFormat(
    streamingData: PlayerResponse.StreamingData?,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean
): PlayerResponse.StreamingData.Format {
    logger.v { "extracting format with quality $audioQualityFormat and metered connection: $connectionMetered" }

    val sortedAudioFormats =
        streamingData?.adaptiveFormats
                     ?.filter {
                         it.mimeType.startsWith( "audio" )
                     }
                     ?.sortedBy(PlayerResponse.StreamingData.Format::bitrate )
                     .orEmpty()
    if( sortedAudioFormats.isEmpty() )
        throw PlayableFormatNotFoundException()

    return when( audioQualityFormat ) {
        AudioQualityFormat.High -> sortedAudioFormats.last()
        AudioQualityFormat.Low -> sortedAudioFormats.first()
        AudioQualityFormat.Auto ->
            if ( connectionMetered && app.kreate.preferences.Preferences.IS_CONNECTION_METERED.value )
                sortedAudioFormats[sortedAudioFormats.size / 2]
            else
                sortedAudioFormats.last()
    }.also {
        logger.d { "extracted format ${it.itag}" }
    }
}

@Throws(MissingDecipherKeyException::class)
private fun extractStreamUrl( format: PlayerResponse.StreamingData.Format ): String =
    format.signatureCipher?.let { signatureCipher ->
        logger.v { "deobfuscating signature $signatureCipher" }

        val (s, sp, url) = with( parseQueryString( signatureCipher ) ) {
            val signature = this["s"] ?: throw MissingDecipherKeyException("s")
            val signatureParam = this["sp"] ?: throw MissingDecipherKeyException("sp")
            val signatureUrl = this["url"] ?: throw MissingDecipherKeyException("url")
            Triple(
                signature,
                signatureParam,
                URLBuilder(signatureUrl)
            )
        }
        url.toString()
    } ?: format.url!!
//</editor-fold>
//<editor-fold desc="Validators">
private suspend fun validateStreamUrl( streamUrl: String ): Boolean =
    get<HttpClient>(HttpClient::class.java)
        .head( "$streamUrl&range=0-$CHUNK_LENGTH" )
        .status
        .also {
            if( it.isSuccess() )
                logger.d { "Stream url validated successfully" }
            else
                logger.w { "Stream url validation returns code ${it.value} - ${it.description}" }
        }
        .isSuccess()
//</editor-fold>
//<editor-fold desc="Get response">
@OptIn(ExperimentalSerializationApi::class)
private suspend fun makeStreamCache(
    songId: String,
    isConnectionMetered: Boolean,
    audioQuality: AudioQualityFormat,
    method: Int = METHOD_ANDROID
): StreamCache {
    logger.v { "Getting online stream url for \"$songId\" with method $method" }
    logger.d { "Is connection metered: $isConnectionMetered" }
    logger.d { "Audio format: $audioQuality" }

    val cpn = CharUtils.randomString( 12 )
    try {
        //<editor-fold desc="Getting response">
        val jsonResponse = Any()
        val serializerClass = Class.forName("me.knighthat.internal.response.PlayerResponseImpl$\$serializer")
        val serializerInstance = serializerClass.getDeclaredField("INSTANCE").get(null) as KSerializer<*>
        val response = jsonParser.decodeFromString(
            serializerInstance, JsonWriter.string( jsonResponse )
        ) as PlayerResponse
        //</editor-fold>
        //<editor-fold desc="Verify playability">
        val playabilityStatus = response.playabilityStatus
        when( playabilityStatus.status ) {
            "OK"                -> logger.d { "playabilityStatus is OK" }
            "LOGIN_REQUIRED"    -> throw LoginRequiredException(playabilityStatus.reason)
            else                -> throw UnplayableException(playabilityStatus.reason)
        }
        //</editor-fold>
        //<editor-fold desc="Extract and validate stream url">
        val format = extractFormat( response.streamingData, audioQuality, isConnectionMetered )
        val streamUrl = extractStreamUrl( format )
        val validateResult = validateStreamUrl( streamUrl )
        //</editor-fold>

        return if( validateResult ) {
            upsertSongFormat( songId, format )

            val contentLength = format.contentLength?.toLong() ?: CHUNK_LENGTH
            val playableUrl = response.streamingData?.expiresInSeconds?.toLong() ?: 1.hours.inWholeMilliseconds
            StreamCache(cpn, contentLength, streamUrl, playableUrl)
        } else
            // Try again with IOS setup
            makeStreamCache( songId, isConnectionMetered, audioQuality, METHOD_IOS )
    } catch( e: Exception ) {
        if( method == METHOD_ANDROID )
            return makeStreamCache( songId, isConnectionMetered, audioQuality, METHOD_IOS )

        when( e ) {
            is UnknownHostException,
            is UnresolvedAddressException -> {
                // Make sure it's not a temporary network fluctuation
                if( !getNetworkMonitor().value )
                    throw NoInternetException(e)
            }

            // Only show this exception because this needs update
            // Other errors might be because of unsuccessful stream extraction
            is MissingFieldException -> {
                e.message?.also( Toaster::e )
                logger.e( "", e )
            }
        }

        throw e
    }
}

private fun getPlayableUrl( songId: String ): YTPlayerUtils.PlaybackData = runBlocking( Dispatchers.IO ) {
    logger.v { "Processing $songId" }

    if( !CipherDeobfuscator.isInitialized() )
        CipherDeobfuscator.initialize( get(Context::class.java) )

    val cache: YTPlayerUtils.PlaybackData
    if( cachedStreamUrl.contains(songId) ) {
        cache = cachedStreamUrl[songId]!!
        // Handle expired url with 30secs offset
        val remainingSeconds = cache.streamExpiresInSeconds.seconds - 30.seconds
        if( remainingSeconds.inWholeMilliseconds <= System.currentTimeMillis() ) {
            logger.d { "Cached stream url of $songId expired" }

            cachedStreamUrl.remove( songId )
            return@runBlocking getPlayableUrl( songId )
        } else
            logger.d { "Stream url of $songId is cached" }
    } else {
        MetrolistYouTube.acquireVisitorData()

        val connManager = get<Context>(Context::class.java).getSystemService<ConnectivityManager>()!!
        val audioQuality = app.kreate.preferences.Preferences.AUDIO_QUALITY.value

        cache = YTPlayerUtils.playerResponseForPlayback(
            videoId = songId,
            playlistId = null,
            audioQuality = audioQuality,
            connectivityManager = connManager
        ).getOrThrow()
        cachedStreamUrl[songId] = cache
    }

    cache
}
//</editor-fold>

internal fun resolveInnertubeMedia(dataSpec: DataSpec ): DataSpec {
    val songId = requireNotNull( dataSpec.key ) {
        // This requires all online media to have cache key
        // for caching purpose.
        "Online media doesn't contain cache Key"
    }
    upsertSongInfo( songId )

    val cache = getPlayableUrl( songId )
    val length = cache.format.contentLength ?: C.LENGTH_UNSET.toLong()
    return dataSpec.buildUpon()
                   .setUri(cache.streamUrl)
                   .setLength( length )
                   .build()
}

private data class StreamCache(
    val cpn: String,
    val contentLength: Long,
    val playableUrl: String,
    val expiredTimeMillis: Long
)