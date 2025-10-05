package app.kreate.android.di

import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.ui.util.fastFilter
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.di.PlayerModule.upsertSongFormat
import app.kreate.android.di.PlayerModule.upsertSongInfo
import app.kreate.android.service.Discord
import app.kreate.android.service.NetworkService
import app.kreate.android.service.player.CustomExoPlayer
import app.kreate.android.utils.CharUtils
import app.kreate.android.utils.ConnectivityUtils
import app.kreate.android.utils.innertube.CURRENT_LOCALE
import app.kreate.database.models.Format
import com.grack.nanojson.JsonObject
import com.grack.nanojson.JsonWriter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.head
import io.ktor.http.URLBuilder
import io.ktor.http.parseQueryString
import io.ktor.util.collections.ConcurrentMap
import io.ktor.util.network.UnresolvedAddressException
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.enums.AudioQualityFormat
import it.fast4x.rimusic.service.LoginRequiredException
import it.fast4x.rimusic.service.MissingDecipherKeyException
import it.fast4x.rimusic.service.NoInternetException
import it.fast4x.rimusic.service.PlayableFormatNotFoundException
import it.fast4x.rimusic.service.UnplayableException
import it.fast4x.rimusic.utils.isConnectionMetered
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
import me.knighthat.innertube.UserAgents
import me.knighthat.innertube.response.PlayerResponse
import me.knighthat.utils.Toaster
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.services.youtube.YoutubeStreamHelper
import timber.log.Timber
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Named
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import me.knighthat.innertube.request.body.Context as InnertubeContext


@Module
@InstallIn(SingletonComponent::class)
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalSerializationApi::class)
object PlayerModule {

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
    private val justInserted = AtomicReference("")
    private val cachedStreamUrl = ConcurrentMap<String, StreamCache>()
    private val CONTEXTS = arrayOf(
        InnertubeContext.WEB_REMIX_DEFAULT,
        InnertubeContext.ANDROID_VR_DEFAULT,
        InnertubeContext.IOS_DEFAULT,
        InnertubeContext.TVHTML5_EMBEDDED_PLAYER_DEFAULT,
        InnertubeContext.ANDROID_DEFAULT,
        InnertubeContext.WEB_DEFAULT
    )
    private val jsonParser =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            useArrayPolymorphism = true
            explicitNulls = false
        }

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
        if( videoId == justInserted.get() || !isNetworkAvailable( context ) )
            return

        Timber.tag( LOG_TAG ).v( "fetching and upserting $videoId's information to the database" )

        databaseWorker = CoroutineScope(Dispatchers.IO ).launch {
            Innertube.songBasicInfo( videoId, CURRENT_LOCALE )
                     .onSuccess{
                         Timber.tag( LOG_TAG ).v( "$videoId's information successfully found and parsed" )

                         Database.upsert( it )

                         Timber.tag( LOG_TAG ).d( "$videoId's information successfully upserted to the database" )
                     }
                     .onFailure {
                         Timber.tag( LOG_TAG ).e( it, "failed to upsert $videoId's information to database" )
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
        if( videoId == justInserted.get() ) return

        Timber.tag( LOG_TAG ).v( "upserting format ${format.itag} of song $videoId to the database" )

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

                Timber.tag( LOG_TAG ).d( "$videoId is successfully upserted to the database" )

                // Format must be added successfully before setting variable
                justInserted.set( videoId )
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
        Timber.tag( LOG_TAG ).v( "extracting format with quality $audioQualityFormat and metered connection: $connectionMetered")

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
            AudioQualityFormat.Medium -> sortedAudioFormats[sortedAudioFormats.size / 2]
            AudioQualityFormat.Low -> sortedAudioFormats.first()
            AudioQualityFormat.Auto ->
                if ( connectionMetered && Preferences.IS_CONNECTION_METERED.value )
                    sortedAudioFormats[sortedAudioFormats.size / 2]
                else
                    sortedAudioFormats.last()
        }.also {
            Timber.tag( LOG_TAG ).d( "extracted format ${it.itag}" )
        }
    }

    @Throws(MissingDecipherKeyException::class)
    private fun extractStreamUrl( videoId: String, format: PlayerResponse.StreamingData.Format ): String =
        format.signatureCipher?.let { signatureCipher ->
            Timber.tag( LOG_TAG ).v( "deobfuscating signature $signatureCipher" )

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
            url.parameters[sp] = YoutubeJavaScriptPlayerManager.deobfuscateSignature( videoId, s )
            url.toString()
        } ?: format.url!!
    //</editor-fold>

    private fun getSignatureTimestampOrNull( videoId: String ): Int? =
        runCatching {
            YoutubeJavaScriptPlayerManager.getSignatureTimestamp( videoId )
        }
        .onSuccess { Timber.tag( LOG_TAG ).d( "Signature timestamp obtained: $it" ) }
        .onFailure { Timber.tag( LOG_TAG ).e( it, "Failed to get signature timestamp" ) }
        .getOrNull()

    //<editor-fold desc="Validators">
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

    private fun checkPlayabilityStatus( playabilityStatus: PlayerResponse.PlayabilityStatus ) =
        when( playabilityStatus.status ) {
            "OK"                -> Timber.tag( LOG_TAG ).d( "`playabilityStatus` is OK" )
            "LOGIN_REQUIRED"    -> throw LoginRequiredException(playabilityStatus.reason)
            else                -> throw UnplayableException(playabilityStatus.reason)
        }
    //</editor-fold>

    //<editor-fold desc="Get response">
    private fun getPlayerResponseFromNewPipe(
        index: Int,
        songId: String,
        cpn: String
    ): PlayerResponse {
        fun parsePlayerResponseViaReflection( jsonObject: JsonObject): PlayerResponse {
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

    private suspend fun getPlayerResponse(
        songId: String,
        audioQualityFormat: AudioQualityFormat,
        connectionMetered: Boolean
    ): StreamCache {
        var cache: StreamCache? = null

        val cpn = CharUtils.randomString( 12 )
        val signatureTimestamp = getSignatureTimestampOrNull( songId )
        var lastException: Throwable? = null

        var index = 0
        while( index < CONTEXTS.size + 2 ) {
            try {
                val response = if( index > CONTEXTS.lastIndex )
                    getPlayerResponseFromNewPipe( index, songId, cpn )
                else
                    Innertube.player( songId, CONTEXTS[index], CURRENT_LOCALE, signatureTimestamp, CONTEXTS[index].client.visitorData )
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

                    cache = StreamCache(
                        cpn,
                        format.contentLength?.toLong() ?: CHUNK_LENGTH,
                        streamUrl,
                        response.streamingData?.expiresInSeconds?.toLong() ?: 0L
                    )

                    break
                }
            } catch ( e: Exception ) {
                when( e ) {
                    is UnknownHostException,
                    is UnresolvedAddressException -> {
                        // Make sure it's not a temporary network fluctuation
                        if( !ConnectivityUtils.isAvailable.value )
                            throw NoInternetException(e)
                    }

                    else -> {
                        // Only show this exception because this needs update
                        // Other errors might be because of unsuccessful stream extraction
                        if( e is MissingFieldException )
                            e.message?.also( Toaster::e )

                        Timber.tag( LOG_TAG )
                              .e( e, "${CONTEXTS[index].client.clientName} returns error" )
                    }
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

    //<editor-fold desc="Resolvers">
    private fun DataSpec.process(
        videoId: String,
        connectionMetered: Boolean
    ): DataSpec = runBlocking( Dispatchers.IO ) {
        val audioQualityFormat by Preferences.AUDIO_QUALITY

        Timber.tag( LOG_TAG ).v( "processing $videoId at quality $audioQualityFormat with connection metered: $connectionMetered" )

        val cache: StreamCache
        if( cachedStreamUrl.contains( videoId ) ) {
            Timber.tag( LOG_TAG ).d( "Found $videoId in cachedStreamUrl" )

            cache = cachedStreamUrl[videoId]!!

            // Handle expired url with 30secs offset
            if( cache.expiredTimeMillis - 30.seconds.inWholeMilliseconds <= System.currentTimeMillis() ) {
                Timber.tag( LOG_TAG ).d( "url for $videoId has expired!" )

                cachedStreamUrl.remove( videoId )

                return@runBlocking process( videoId, connectionMetered )
            }
        } else {
            Timber.tag( LOG_TAG ).d( "url for $videoId isn't stored! Fetching new url" )

            cachedStreamUrl[videoId] = getPlayerResponse( videoId, audioQualityFormat, connectionMetered )
            cache = cachedStreamUrl[videoId]!!
        }

        val absolutePosition = uriPositionOffset + position
        YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated( videoId, cache.playableUrl )
                                      .toUri()
                                      .buildUpon()
                                      .appendQueryParameter( "range", "$absolutePosition-${cache.contentLength}" )
                                      .appendQueryParameter( "cpn", cache.cpn )
                                      .build()
                                      .let( ::withUri )
    }

    /**
     * Used to determined whether the song can be played from cached,
     * or a call to online service must be made to get needed data.
     */
    private fun resolver(
        context: Context,
        vararg cashes: Cache
    ) = ResolvingDataSource.Resolver { dataSpec ->
        val videoId = dataSpec.uri.toString().substringAfter( "watch?v=" )

        // Delay this block until called. Song can be local too
        val cacheLength = dataSpec.length.takeIf { it != -1L } ?: CHUNK_LENGTH
        fun isCached() = cashes.any {
            it.isCached( videoId, dataSpec.position, cacheLength )
        }
        // When player resumes from persistent queue, the videoId isn't path to the file,
        // but the following format: local:id. Therefore, checking for prefix is needed.
        val isLocal = videoId.startsWith(LOCAL_KEY_PREFIX, true )
                || dataSpec.uri.isLocalFile()

        if( !isLocal )
            upsertSongInfo( context, videoId )

        return@Resolver if( isLocal ) {
            Timber.tag( LOG_TAG ).d( "$videoId is local song" )

            if( videoId.startsWith(LOCAL_KEY_PREFIX, true ) )
                // This will take id from videoId and return path to that media file
                // For example: `local:id` becomes `content:/path/to/media/id`
                ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    videoId.substringAfter(LOCAL_KEY_PREFIX).toLong()
                ).also {
                    Timber.tag( LOG_TAG ).v( "Resolved path: $it" )
                }.let( dataSpec::withUri )
            else
                dataSpec
        } else if( isCached() ) {
            Timber.tag( LOG_TAG ).d( "$videoId exists in cache, proceeding to use from cache" )
            // No need to fetch online for already cached data
            dataSpec
        } else
            dataSpec.process( videoId, context.isConnectionMetered() )
    }
    //</editor-fold>

    //<editor-fold desc="Datasources">
    /**
     * Short-circuit function to quickly make a [DataSource.Factory] from
     * designated [cache]
     */
    private fun dataSourceFactoryFrom( cache: Cache ): CacheDataSource.Factory =
        CacheDataSource.Factory().setCache( cache )

    @Provides
    @Named("defaultDatasource")
    @Singleton
    fun providesOkHttpDataSourceFactory(
        @ApplicationContext context: Context
    ): DataSource.Factory =
        // [DefaultDataSource.Factory] with [context] is required to read
        // data from local files.
        // Normal HTTP requests are handled by [OkHttpDataSource.Factory]
        DefaultDataSource.Factory(
            context,
            OkHttpDataSource.Factory( NetworkService.engine )
                .setUserAgent( UserAgents.CHROME_WINDOWS )
        )

    @Provides
    @Named("downloadDataSource")
    @Singleton
    fun providesDownloadDataSource(
        @ApplicationContext context: Context,
        @Named("downloadCache") downloadCache: Cache,
        @Named("defaultDatasource") defaultDatasource: DataSource.Factory
    ): DataSource.Factory =
        ResolvingDataSource.Factory(
            dataSourceFactoryFrom( downloadCache )
                .setUpstreamDataSourceFactory( defaultDatasource )
                .setCacheWriteDataSinkFactory( null ),
            resolver( context, downloadCache )
        )

    @Provides
    @Named("playerDataSource")
    @Singleton
    fun providesPlayerDataSource(
        @ApplicationContext context: Context,
        @Named("cache") cache: Cache,
        @Named("downloadCache") downloadCache: Cache,
        @Named("defaultDatasource") defaultDatasource: DataSource.Factory
    ): DataSource.Factory =
        ResolvingDataSource.Factory(
            dataSourceFactoryFrom( downloadCache )
                .setCacheWriteDataSinkFactory( null )
                .setFlags( FLAG_IGNORE_CACHE_ON_ERROR )
                .setUpstreamDataSourceFactory(
                    dataSourceFactoryFrom( cache )
                        .setUpstreamDataSourceFactory( defaultDatasource )
                        .setCacheWriteDataSinkFactory(
                            CacheDataSink.Factory()
                                         .setCache( cache )
                                         .setFragmentSize( CHUNK_LENGTH )
                        )
                        .setFlags( FLAG_IGNORE_CACHE_ON_ERROR )
                ),
            resolver( context, cache, downloadCache )
        )
    //</editor-fold>

    @Provides
    @Singleton
    fun providesExoPlayer(
        @ApplicationContext context: Context,
        @Named("playerDataSource") dataSourceFactory: DataSource.Factory,
        @Named("plain") preferences: SharedPreferences,
        discord: Discord
    ): ExoPlayer = CustomExoPlayer(context, dataSourceFactory, preferences, discord)

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
}