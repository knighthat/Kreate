package app.kreate.android.service

import android.content.ContentResolver
import android.content.Context
import androidx.compose.ui.util.fastFilter
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.di.PlayerModule
import app.kreate.android.utils.CharUtils
import app.kreate.android.utils.innertube.CURRENT_LOCALE
import com.grack.nanojson.JsonWriter
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.head
import io.ktor.http.URLBuilder
import io.ktor.http.parseQueryString
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.enums.AudioQualityFormat
import it.fast4x.rimusic.isConnectionMeteredEnabled
import it.fast4x.rimusic.models.Format
import it.fast4x.rimusic.service.LoginRequiredException
import it.fast4x.rimusic.service.UnknownException
import it.fast4x.rimusic.service.UnplayableException
import it.fast4x.rimusic.service.modern.PlayerServiceModern
import it.fast4x.rimusic.utils.isConnectionMetered
import it.fast4x.rimusic.utils.isNetworkAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.json.Json
import me.knighthat.impl.DownloadHelperImpl
import me.knighthat.innertube.Endpoints
import me.knighthat.innertube.response.PlayerResponse
import me.knighthat.utils.Toaster
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.services.youtube.YoutubeStreamHelper
import timber.log.Timber
import javax.security.auth.login.LoginException
import kotlin.time.Duration.Companion.seconds
import me.knighthat.innertube.Innertube as NewInnertube
import me.knighthat.innertube.request.body.Context as InnertubeContext

private const val LOG_TAG = "dataspec"
private const val CHUNK_LENGTH = 128 * 1024L     // 128Kb

/**
 * Acts as a lock to keep [upsertSongFormat] from starting before
 * [upsertSongInfo] finishes.
 */
private var databaseWorker: Job = Job()

/**
 * Store id of song just added to the database.
 * This is created to reduce load to Room
 */
@set:Synchronized
private var justInserted: String = ""

private val jsonParser =
    Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        useArrayPolymorphism = true
        explicitNulls = false
    }

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
    if( videoId == justInserted || !isNetworkAvailable( context ) ) return

    Timber.tag( LOG_TAG ).v( "fetching and upserting $videoId's information to the database" )

    databaseWorker = CoroutineScope(Dispatchers.IO ).launch {
        NewInnertube.songBasicInfo( videoId, CURRENT_LOCALE )
                    .onSuccess{
                        Timber.tag( LOG_TAG ).v( "$videoId's information successfully found and parsed" )

                        Database.upsert( it )

                        Timber.tag( LOG_TAG ).d( "$videoId's information successfully upserted to the database" )
                    }
                    .onFailure {
                        Timber.tag( LOG_TAG ).e( it, "failed to upsert $videoId's information to database" )

                        val message= it.message ?: context.getString( R.string.failed_to_fetch_original_property )
                        Toaster.e( message )
                    }
    }

    // Must not modify [JustInserted] to [upsertSongFormat] let execute later
}

/**
 * Upsert provided format to the database
 */
private fun upsertSongFormat( videoId: String, format: PlayerResponse.StreamingData.Format ) {
    // Skip adding if it's just added in previous call
    if( videoId == justInserted ) return

    Timber.tag( LOG_TAG ).v( "upserting format ${format.itag} of song $videoId to the database" )

    CoroutineScope(Dispatchers.IO ).launch {
        // Wait until this job is finish to make sure song's info
        // is in the database before continuing
        databaseWorker.join()

        Database.asyncTransaction {
            formatTable.insertIgnore(Format(
                videoId,
                format.itag.toInt(),
                format.mimeType,
                format.bitrate.toLong(),
                format.contentLength?.toLong(),
                format.lastModified.toLong(),
                format.loudnessDb
            ))

            Timber.tag( LOG_TAG ).d( "$videoId is successfully upserted to the database" )

            // Format must be added successfully before setting variable
            justInserted = videoId
        }
    }
}

@UnstableApi
private fun upstreamDatasourceFactory( context: Context ): DataSource.Factory =
    DefaultDataSource.Factory( context, KtorHttpDatasource.Factory(NetworkService.client ) )

//<editor-fold defaultstate="collapsed" desc="Extractors">
private fun extractFormat(
    streamingData: PlayerResponse.StreamingData?,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean
): PlayerResponse.StreamingData.Format {
    Timber.tag( LOG_TAG ).v( "extracting format with quality $audioQualityFormat and metered connection: $connectionMetered")

    val sortedAudioFormats =
        streamingData?.adaptiveFormats
                     ?.fastFilter {
                         it.mimeType.startsWith( "audio" )
                     }
                     ?.sortedBy(PlayerResponse.StreamingData.Format::bitrate )
                     .orEmpty()
    check( sortedAudioFormats.isNotEmpty() )

    return when( audioQualityFormat ) {
        AudioQualityFormat.High -> sortedAudioFormats.last()
        AudioQualityFormat.Medium -> sortedAudioFormats[sortedAudioFormats.size / 2]
        AudioQualityFormat.Low -> sortedAudioFormats.first()
        AudioQualityFormat.Auto ->
            if (connectionMetered && isConnectionMeteredEnabled())
                sortedAudioFormats[sortedAudioFormats.size / 2]
            else
                sortedAudioFormats.last()
    }.also {
        Timber.tag( LOG_TAG ).d( "extracted format ${it.itag}" )
    }
}

private fun extractStreamUrl( videoId: String, format: PlayerResponse.StreamingData.Format ): String =
    format.signatureCipher?.let { signatureCipher ->
        Timber.tag( LOG_TAG ).v( "deobfuscating signature $signatureCipher" )

        val (s, sp, url) = with( parseQueryString( signatureCipher ) ) {
            Triple(
                requireNotNull( this["s"] ) { "missing signature cipher" },
                requireNotNull( this["sp"] ) { "missing signature parameter" },
                requireNotNull(
                    this["url"]?.let( ::URLBuilder )
                ) { "missing url from signatureCipher" }
            )
        }
        url.parameters[sp] = YoutubeJavaScriptPlayerManager.deobfuscateSignature( videoId, s )
        url.toString()
    } ?: format.url!!

private fun getSignatureTimestampOrNull( videoId: String ): Int? =
    runCatching {
        YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
    }
    .onSuccess { Timber.tag( LOG_TAG ).d( "Signature timestamp obtained: $it" ) }
    .onFailure { Timber.tag( LOG_TAG ).e( it, "Failed to get signature timestamp" ) }
    .getOrNull()

private suspend fun validateStreamUrl( streamUrl: String ): Boolean =
    NetworkService.client
                  .head( streamUrl ) {
                      Timber.tag( LOG_TAG ).v( "Validating `streamUrl`..." )

                      expectSuccess = false
                  }
                  .status
                  .value
                  .also {
                      Timber.tag( LOG_TAG ).d( "`streamUrl` returns code $it" )
                  } == 200

@UnstableApi
private fun checkPlayabilityStatus( playabilityStatus: PlayerResponse.PlayabilityStatus ) =
    when( playabilityStatus.status ) {
        "OK"                -> { Timber.tag( LOG_TAG ).d( "`playabilityStatus` is OK" ) }
        "LOGIN_REQUIRED"    -> throw LoginRequiredException(playabilityStatus.reason)
        "UNPLAYABLE"        -> throw UnplayableException(playabilityStatus.reason)
        else                -> throw UnknownException(playabilityStatus.reason)
    }

private fun getPlayerResponseFromNewPipe(
    index: Int,
    songId: String,
    cpn: String
): PlayerResponse {
    fun parsePlayerResponseViaReflection( jsonObject: com.grack.nanojson.JsonObject ): PlayerResponse {
        val serializerClass = Class.forName("me.knighthat.internal.response.PlayerResponseImpl$\$serializer")
        val serializerInstance = serializerClass.getDeclaredField("INSTANCE").get(null) as KSerializer<*>

        return jsonParser.decodeFromString(
            serializerInstance, JsonWriter.string( jsonObject )
        ) as PlayerResponse
    }

    val (gl, hl) = with( CURRENT_LOCALE ) {
        ContentCountry(regionCode) to Localization(languageCode)
    }
    val jsonResponse = if( index == CONTEXTS.lastIndex + 1 )
        YoutubeStreamHelper.getAndroidReelPlayerResponse( gl, hl, songId, cpn )
    else
        YoutubeStreamHelper.getIosPlayerResponse( gl, hl, songId, cpn, null )

    return parsePlayerResponseViaReflection( jsonResponse )
}

@OptIn(ExperimentalSerializationApi::class)
@UnstableApi
private suspend fun getPlayerResponse(
    songId: String,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean
): Cache {
    var cache: Cache? = null

    val cpn = CharUtils.randomString( 12 )
    val signatureTimestamp = getSignatureTimestampOrNull( songId )
    var lastException: Throwable? = null

    var index = 0
    while( index < CONTEXTS.size + 2 ) {
        try {
            val response = if( index > CONTEXTS.lastIndex )
                getPlayerResponseFromNewPipe( index, songId, cpn )
            else
                NewInnertube.player( songId, CONTEXTS[index], CURRENT_LOCALE, signatureTimestamp, CONTEXTS[index].client.visitorData )
                            .getOrThrow()
            checkPlayabilityStatus(
                requireNotNull( response.playabilityStatus )
            )

            val format = extractFormat( response.streamingData, audioQualityFormat, connectionMetered )
            val streamUrl = extractStreamUrl( songId, format )

            if( validateStreamUrl( streamUrl ) ) {
                // This variable must be set to [null] here
                // Otherwise, error will be thrown as soon as the while-loop ends
                lastException = null

                format.also {
                    upsertSongFormat( songId, it )
                }

                cache = Cache(
                    cpn,
                    format.contentLength?.toLong() ?: CHUNK_LENGTH,
                    streamUrl,
                    response.streamingData?.expiresInSeconds?.toLong() ?: 0L
                )

                break
            }
        } catch ( e: Exception ) {
            when( e ) {
                // Only show this exception because this needs update
                // Other errors might be because of unsuccessful stream extraction
                is MissingFieldException -> e.message?.also( Toaster::e )

                // Must be placed last because most exceptions above are lumped
                // into this exception in the end. And the message is vague
                is UnplayableException,
                is LoginException,
                is NullPointerException,            // When a component of cipherSignature wasn't found
                is CancellationException -> e.message?.also { Timber.tag( LOG_TAG ).i( it ) }

                else -> Timber.tag( LOG_TAG ).e( e, "getPlayerResponse returns error" )
            }

            lastException = e
        }

        // **IMPORTANT**: Missing this causes infinite loop
        index++
    }

    if( lastException != null ) throw lastException

    return requireNotNull( cache ) {
        "`streamUrl` is verified but `cache` is still null"
    }
}
//</editor-fold>

private val CONTEXTS = arrayOf(
    InnertubeContext.WEB_REMIX_DEFAULT,
    InnertubeContext.ANDROID_VR_DEFAULT,
    InnertubeContext.IOS_DEFAULT,
    InnertubeContext.TVHTML5_EMBEDDED_PLAYER_DEFAULT,
    InnertubeContext.ANDROID_DEFAULT,
    InnertubeContext.WEB_DEFAULT
)

private val cachedStreamUrl = mutableMapOf<String, Cache>()

@ExperimentalSerializationApi
@UnstableApi
fun DataSpec.process(
    videoId: String,
    audioQualityFormat: AudioQualityFormat,
    connectionMetered: Boolean
): DataSpec = runBlocking( Dispatchers.IO ) {
    Timber.tag( LOG_TAG ).v( "processing $videoId at quality $audioQualityFormat with connection metered: $connectionMetered" )

    val cache: Cache
    if( cachedStreamUrl.contains( videoId ) ) {
        Timber.tag( LOG_TAG ).d( "Found $videoId in cachedStreamUrl" )

        cache = cachedStreamUrl[videoId]!!

        // Handle expired url with 30secs offset
        if( cache.expiredTimeMillis - 30.seconds.inWholeMilliseconds <= System.currentTimeMillis() ) {
            Timber.tag( LOG_TAG ).d( "url for $videoId has expired!" )

            cachedStreamUrl.remove( videoId )

            return@runBlocking process( videoId, audioQualityFormat, connectionMetered )
        }
    } else {
        Timber.tag( LOG_TAG ).d( "url for $videoId isn't stored! Fetching new url" )

        cachedStreamUrl[videoId] = getPlayerResponse( videoId, audioQualityFormat, connectionMetered )
        cache = cachedStreamUrl[videoId]!!
    }

    val startPosition = position + uriPositionOffset
    YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated( videoId, cache.playableUrl )
                                  .toUri()
                                  .buildUpon()
                                  .appendQueryParameter( "range", "$startPosition-${cache.contentLength}" )
                                  .appendQueryParameter( "cpn", cache.cpn )
                                  .build()
                                  .let( ::withUri )
                                  .subrange( startPosition, C.LENGTH_UNSET.toLong() )
}

//<editor-fold defaultstate="collapsed" desc="Data source factories">
@OptIn(ExperimentalSerializationApi::class)
@UnstableApi
fun PlayerServiceModern.createDataSourceFactory( context: Context ): DataSource.Factory =
    ResolvingDataSource.Factory(
        CacheDataSource.Factory()
                       .setCache( downloadCache )
                       .setUpstreamDataSourceFactory(
                           CacheDataSource.Factory()
                                          .setCache( cache )
                                          .setUpstreamDataSourceFactory(
                                              upstreamDatasourceFactory( context )
                                          )
                                          .setCacheWriteDataSinkFactory(
                                              CacheDataSink.Factory()
                                                           .setCache( cache )
                                                           .setFragmentSize( CHUNK_LENGTH )
                                          )
                                          .setFlags( FLAG_IGNORE_CACHE_ON_ERROR )
                       )
                       .setCacheWriteDataSinkFactory( null )
                       .setFlags( FLAG_IGNORE_CACHE_ON_ERROR )
    ) { dataSpec ->
        Database.asyncTransaction {
            runBlocking( Dispatchers.Main ) {
                player.currentMediaItem
            }?.also( ::insertIgnore )
        }

        val videoId = dataSpec.uri.toString().substringAfter("watch?v=")

        fun isCached() =
            cache.isCached( videoId, dataSpec.position, CHUNK_LENGTH )
        fun isDownloaded() =
            downloadCache.isCached( videoId, dataSpec.position, CHUNK_LENGTH )

        val isLocal = dataSpec.uri.scheme == ContentResolver.SCHEME_CONTENT || dataSpec.uri.scheme == ContentResolver.SCHEME_FILE

        if( !isLocal )
            upsertSongInfo( applicationContext, videoId )

        return@Factory if( isLocal || isCached() || isDownloaded() ){
            Timber.tag( LOG_TAG ).d( "$videoId exists in cache, proceeding to use from cache" )
            // No need to fetch online for already cached data
            dataSpec.subrange( dataSpec.uriPositionOffset, C.LENGTH_UNSET.toLong() )
        }else
            dataSpec.process( videoId, audioQualityFormat, applicationContext.isConnectionMetered() )
    }

@ExperimentalSerializationApi
@UnstableApi
fun PlayerModule.createDataSourceFactory(
    context: Context,
    cache: androidx.media3.datasource.cache.Cache,
    downloadCache: androidx.media3.datasource.cache.Cache
) = ResolvingDataSource.Factory(
    CacheDataSource.Factory()
        .setCache( downloadCache )
        .setUpstreamDataSourceFactory(
            CacheDataSource.Factory()
                .setCache( cache )
                .setUpstreamDataSourceFactory(
                    upstreamDatasourceFactory( context )
                )
                .setCacheWriteDataSinkFactory(
                    CacheDataSink.Factory()
                        .setCache( cache )
                        .setFragmentSize( CHUNK_LENGTH )
                )
                .setFlags( FLAG_IGNORE_CACHE_ON_ERROR )
        )
        .setCacheWriteDataSinkFactory( null )
        .setFlags( FLAG_IGNORE_CACHE_ON_ERROR )
) { dataSpec ->
//    Database.asyncTransaction {
//        runBlocking( Dispatchers.Main ) {
//            player.currentMediaItem
//        }?.also( ::insertIgnore )
//    }

    val videoId = dataSpec.uri.toString().substringAfter("watch?v=")

    fun isCached() =
        cache.isCached( videoId, dataSpec.position, CHUNK_LENGTH )
    fun isDownloaded() =
        downloadCache.isCached( videoId, dataSpec.position, CHUNK_LENGTH )

    val isLocal = dataSpec.uri.scheme == ContentResolver.SCHEME_CONTENT || dataSpec.uri.scheme == ContentResolver.SCHEME_FILE

    if( !isLocal )
        upsertSongInfo( videoId )

    return@Factory if( isLocal || isCached() || isDownloaded() ){
        Timber.tag( LOG_TAG ).d( "$videoId exists in cache, proceeding to use from cache" )
        // No need to fetch online for already cached data
        dataSpec.subrange( dataSpec.uriPositionOffset, C.LENGTH_UNSET.toLong() )
    }else
        dataSpec.process( videoId, Preferences.AUDIO_QUALITY.value, context.isConnectionMetered() )
}

@OptIn(ExperimentalSerializationApi::class)
@UnstableApi
fun DownloadHelperImpl.createDataSourceFactory( context: Context ): DataSource.Factory =
    ResolvingDataSource.Factory(
        CacheDataSource.Factory()
                       .setCache( downloadCache )
                       .apply {
                           setUpstreamDataSourceFactory(
                               upstreamDatasourceFactory( context )
                           )
                           setCacheWriteDataSinkFactory( null )
                       }
    ) { dataSpec: DataSpec ->
        val videoId: String = dataSpec.uri
                                      .toString()
                                      .substringAfter( "watch?v=" )
                                      .also { upsertSongInfo( context, it ) }

        val isDownloaded = downloadCache.isCached( videoId, dataSpec.position, CHUNK_LENGTH )

        return@Factory if( isDownloaded ){
            Timber.tag( LOG_TAG ).d( "$videoId is downloaded, proceeding to use from cache" )
            // No need to fetch online for already cached data
            dataSpec.subrange( dataSpec.uriPositionOffset, C.LENGTH_UNSET.toLong() )
        }else
            dataSpec.process( videoId, Preferences.AUDIO_QUALITY.value, context.isConnectionMetered() )
    }
//</editor-fold>

private data class Cache(
    val cpn: String,
    val contentLength: Long,
    val playableUrl: String,
    val expiredTimeMillis: Long
)
