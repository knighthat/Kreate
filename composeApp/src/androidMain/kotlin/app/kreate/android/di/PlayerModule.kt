package app.kreate.android.di

import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
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
import app.kreate.android.di.PlayerModule.MAX_CHUNK_LENGTH
import app.kreate.android.di.PlayerModule.upsertSongFormat
import app.kreate.android.di.PlayerModule.upsertSongInfo
import app.kreate.android.service.Discord
import app.kreate.android.service.NetworkService
import app.kreate.android.service.player.CustomExoPlayer
import app.kreate.android.utils.YTPlayerUtils
import app.kreate.android.utils.innertube.CURRENT_LOCALE
import app.kreate.android.utils.isLocalFile
import app.kreate.database.models.Format
import app.kreate.util.LOCAL_KEY_PREFIX
import com.metrolist.innertube.models.response.PlayerResponse
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.util.network.UnresolvedAddressException
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.service.NoInternetException
import it.fast4x.rimusic.service.UnknownException
import it.fast4x.rimusic.utils.isConnectionMetered
import it.fast4x.rimusic.utils.isNetworkAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import me.knighthat.innertube.Endpoints
import me.knighthat.innertube.Innertube
import me.knighthat.innertube.UserAgents
import me.knighthat.utils.Toaster
import timber.log.Timber
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Named
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalSerializationApi::class)
object PlayerModule {

    private const val LOG_TAG = "dataspec"
    private const val CHUNK_LENGTH = 128 * 1024L     // 128Kb
    private const val MAX_CHUNK_LENGTH = 5L * 1024 * 1024       // 5 Mb

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
    private val songUrlCache = ConcurrentHashMap<String, Triple<String, Long, Long?>>()

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
                        format.itag,
                        format.mimeType,
                        format.bitrate.toLong(),
                        format.contentLength,
                        format.lastModified,
                        format.loudnessDb?.toFloat()
                    )
                )

                Timber.tag( LOG_TAG ).d( "$videoId is successfully upserted to the database" )

                // Format must be added successfully before setting variable
                justInserted.set( videoId )
            }
        }
    }

    /**
     * Returns the length from [position] to [contentLength].
     *
     * If [contentLength] is a `null` value, use [C.LENGTH_UNSET]
     * to get the rest of the data.
     *
     * Cap the maximum data to get to [MAX_CHUNK_LENGTH]
     */
    private fun calculateLength( position: Long, contentLength: Long? ): Long =
        contentLength?.let { it - position }
                     ?.coerceAtMost( MAX_CHUNK_LENGTH )
                     ?: C.LENGTH_UNSET.toLong()

    //<editor-fold desc="Resolvers">
    private fun DataSpec.process(
        videoId: String,
        connectionMetered: Boolean
    ): DataSpec = runBlocking( Dispatchers.IO ) {
        val audioQualityFormat by Preferences.AUDIO_QUALITY

        Timber.tag( LOG_TAG ).v( "processing $videoId at quality $audioQualityFormat with connection metered: $connectionMetered" )

        // Checking cached urls
        if( songUrlCache.containsKey( videoId ) ) {
            Timber.tag( LOG_TAG ).d( "cache hit" )

            val (url, expire, length) = songUrlCache[videoId]!!
            val currentTime = System.currentTimeMillis()
            if( expire > currentTime ) {
                val range = calculateLength( position, length )
                return@runBlocking withUri( url.toUri() ).subrange( 0, range )
            }
        } else
            Timber.tag( LOG_TAG ).d( "cache missed" )

        val response = YTPlayerUtils.playerResponseForPlayback(
            videoId = videoId,
            audioQuality = audioQualityFormat,
            isNetworkMetered = connectionMetered
        ).onSuccess { res ->
            upsertSongFormat( videoId, res.format )
        }.getOrElse { err ->
            Timber.tag( LOG_TAG ).e( err )
            Toaster.e( "failed to fetch playback stream" )

            when( err ) {
                is UnknownHostException,
                is UnresolvedAddressException   -> throw NoInternetException(err)
                is PlaybackException            -> throw err
                else                            -> throw UnknownException()
            }
        }

        val streamUrl = response.streamUrl
        songUrlCache[videoId] = Triple(
            streamUrl,
            System.currentTimeMillis() + response.streamExpiresInSeconds * 1000L,
            response.format.contentLength
        )

        val range = calculateLength( position, response.format.contentLength )
        withUri( streamUrl.toUri() ).subrange( 0, range )
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
    ): ExoPlayer = CustomExoPlayer(dataSourceFactory, preferences, context, discord)

    /**
     * Remove cached url of [songId].
     *
     * @return `true` if song's url was cached, and is deleted, `false` otherwise.
     */
    fun clearCachedStreamUrlOf( songId: String ): Boolean =
        songUrlCache.remove( songId ) != null

    private data class StreamCache(
        val cpn: String,
        val contentLength: Long,
        val playableUrl: String,
        val expiredTimeMillis: Long
    )
}