@file:kotlin.OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
@file:androidx.media3.common.util.UnstableApi

package app.kreate.di

import android.content.Context
import android.net.ConnectivityManager
import androidx.compose.runtime.getValue
import androidx.compose.ui.util.fastFilter
import androidx.core.content.getSystemService
import androidx.media3.common.C
import androidx.media3.datasource.DataSpec
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.utils.CharUtils
import app.kreate.android.utils.ConnectivityUtils
import app.kreate.android.utils.innertube.CURRENT_LOCALE
import app.kreate.database.models.Format
import co.touchlab.kermit.Logger
import com.grack.nanojson.JsonWriter
import com.metrolist.innertube.YouTube
import com.metrolist.music.utils.YTPlayerUtils
import com.metrolist.music.utils.cipher.CipherDeobfuscator
import io.ktor.client.HttpClient
import io.ktor.client.request.head
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess
import io.ktor.http.parseQueryString
import io.ktor.util.network.UnresolvedAddressException
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.enums.AudioQualityFormat
import it.fast4x.rimusic.service.LoginRequiredException
import it.fast4x.rimusic.service.MissingDecipherKeyException
import it.fast4x.rimusic.service.NoInternetException
import it.fast4x.rimusic.service.PlayableFormatNotFoundException
import it.fast4x.rimusic.service.UnplayableException
import it.fast4x.rimusic.utils.isNetworkAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.json.Json
import me.knighthat.innertube.Endpoints
import me.knighthat.innertube.Innertube
import me.knighthat.innertube.response.PlayerResponse
import me.knighthat.utils.Toaster
import org.koin.core.scope.Scope
import org.koin.java.KoinJavaComponent.get
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicReference
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds


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

/**
 * Acts as a lock to keep [upsertSongFormat] from starting before
 * [upsertSongInfo] finishes.
 */
private var databaseWorker: Job = Job()

//<editor-fold desc="Database handlers">
/**
 * Reach out to [Endpoints.NEXT] endpoint for song's information.
 *
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
private fun upsertSongInfo( context: Context, videoId: String ) {       // Use this to prevent suspension of thread while waiting for response from YT
    // Skip adding if it's just added in previous call
    if( videoId == justInserted.load() || !isNetworkAvailable( context ) )
        return

    logger.v { "fetching and upserting $videoId's information to the database" }

    databaseWorker = CoroutineScope(Dispatchers.IO ).launch {
        Innertube.songBasicInfo( videoId, CURRENT_LOCALE )
            .onSuccess{
                logger.v { "$videoId's information successfully found and parsed" }

                Database.upsert( it )

                logger.d { "$videoId's information successfully upserted to the database" }
            }
            .onFailure {
                logger.e( "failed to upsert $videoId's information to database", it )
                Toaster.e( R.string.error_failed_to_fetch_songs_info )
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
            ?.fastFilter {
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
            if ( connectionMetered && Preferences.IS_CONNECTION_METERED.value )
                sortedAudioFormats[sortedAudioFormats.size / 2]
            else
                sortedAudioFormats.last()
    }.also {
        logger.d { "extracted format ${it.itag}" }
    }
}

@Throws(MissingDecipherKeyException::class)
private fun extractStreamUrl( videoId: String, format: PlayerResponse.StreamingData.Format ): String =
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
        val playabilityStatus = requireNotNull( response.playabilityStatus ) {
            "playabilityStatus is null!"
        }
        when( playabilityStatus.status ) {
            "OK"                -> logger.d { "playabilityStatus is OK" }
            "LOGIN_REQUIRED"    -> throw LoginRequiredException(playabilityStatus.reason)
            else                -> throw UnplayableException(playabilityStatus.reason)
        }
        //</editor-fold>
        //<editor-fold desc="Extract and validate stream url">
        val format = extractFormat( response.streamingData, audioQuality, isConnectionMetered )
        val streamUrl = extractStreamUrl( songId, format )
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
                if( !ConnectivityUtils.isAvailable.value )
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
        if( YouTube.visitorData == null )
            YouTube.visitorData()
                   .onFailure { err ->
                       logger.e( "", err )
                   }
                   .onSuccess {
                       YouTube.visitorData = it
                       logger.d { "Fetched visitorData: $it" }
                   }

        val connManager = get<Context>(Context::class.java).getSystemService<ConnectivityManager>()!!
        val audioQuality by Preferences.AUDIO_QUALITY

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

fun Scope.resolveInnertubeMedia( dataSpec: DataSpec ): DataSpec {
    val songId = requireNotNull( dataSpec.key ) {
        // This requires all online media to have cache key
        // for caching purpose.
        "Online media doesn't contain cache Key"
    }
    upsertSongInfo( get(), songId )

    val cache = getPlayableUrl( songId )
    val length = cache.format.contentLength ?: C.LENGTH_UNSET.toLong()
    return dataSpec.buildUpon()
                   .setUri(cache.streamUrl)
                   .setLength( length )
                   .build()
}

/**
 * Remove cached url of [songId].
 *
 * @return `true` if song's url was cached, and is deleted, `false` otherwise.
 */
fun clearCachedStreamUrlOf( songId: String ): Boolean =
    cachedStreamUrl.remove( songId ) != null

private data class StreamCache(
    val cpn: String,
    val contentLength: Long,
    val playableUrl: String,
    val expiredTimeMillis: Long
)