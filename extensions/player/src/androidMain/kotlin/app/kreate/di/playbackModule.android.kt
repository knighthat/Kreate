@file:androidx.media3.common.util.UnstableApi

package app.kreate.di

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSink
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import app.kreate.UserAgents
import app.kreate.internal.player.ErrorHandlingPolicy
import app.kreate.internal.player.PlayerImpl
import app.kreate.internal.resolvers.resolveInnertubeMedia
import app.kreate.player.Player
import app.kreate.preferences.Preferences
import app.kreate.utils.isLocalFile
import it.fast4x.rimusic.enums.ExoPlayerCacheLocation
import okhttp3.OkHttpClient
import org.koin.core.module.Module
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.QualifierValue
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.io.path.createTempDirectory
import androidx.media3.common.Player as MediaPlayer


const val CHUNK_LENGTH = 512 * 1024L     // 512KB

private const val CACHE_DIRNAME = "exo_cache"
private const val DOWNLOAD_CACHE_DIRNAME = "exo_downloads"

private fun initCache( context: Context, size: Long, cacheDirName: String ): Cache {
    val location = Preferences.EXO_CACHE_LOCATION.value
    val cacheEvictor = when( size ) {
        0L,
        Long.MAX_VALUE  -> NoOpCacheEvictor()
        else            -> LeastRecentlyUsedCacheEvictor( size )
    }
    val cacheDir = when( size ) {
        // Temporary directory deletes itself after close
        // It means songs remain on device as long as it's open
        0L -> createTempDirectory( cacheDirName ).toFile()

        // Looks a bit ugly but what it does is
        // check location set by user and return
        // appropriate path with [cacheDirName] appended.
        else -> when( location ) {
            ExoPlayerCacheLocation.System   -> context.cacheDir
            ExoPlayerCacheLocation.Private  -> context.filesDir
            ExoPlayerCacheLocation.SPLIT    -> if( cacheDirName == DOWNLOAD_CACHE_DIRNAME ) context.filesDir else context.cacheDir
        }.resolve( cacheDirName )
    }
    // Ensure this location exists
    cacheDir.mkdirs()

    return SimpleCache( cacheDir, cacheEvictor, StandaloneDatabaseProvider(context) )
}

actual val playbackModule: Module = module {
    //<editor-fold desc="Cache">
    single( CacheType.CACHE ) {
        initCache( get(), Preferences.EXO_CACHE_SIZE.value, CACHE_DIRNAME )
    }
    single( CacheType.DOWNLOAD ) {
        initCache( get(), Preferences.EXO_DOWNLOAD_SIZE.value, DOWNLOAD_CACHE_DIRNAME )
    }
    factory( CacheType.CACHE ) {
        CacheDataSource.Factory()
                       .setCache( get(CacheType.CACHE) )
                       .setFlags( FLAG_IGNORE_CACHE_ON_ERROR )
    }
    factory( CacheType.DOWNLOAD ) {
        CacheDataSource.Factory()
                       .setCache( get(CacheType.DOWNLOAD) )
                       .setFlags( FLAG_IGNORE_CACHE_ON_ERROR )
    }
    //</editor-fold>

    single {
        ResolvingDataSource.Factory(
            DefaultDataSource.Factory(
                get(),
                OkHttpDataSource.Factory(get<OkHttpClient>())
                    .setUserAgent( UserAgents.CHROME_WINDOWS )
            )
        ) { dataSpec ->
            if ( dataSpec.uri.isLocalFile() )
                // If this is a local file, no conversion needed
                // because its uri already points to a physical file
                dataSpec
            else
                resolveInnertubeMedia( dataSpec )
        }
    }

    // FIXME: This is technically usable but not recommended,
    //  new instance should be created on each injection.
    //  subscribers should use [PlaybackService]'s player instead of injecting
    //  an instance from Koin.
    // TODO: Convert this into factory
    single<ExoPlayer> {
        //<editor-fold desc="DataSource">
        val dataSource = DefaultMediaSourceFactory(
            // At the bottom of the stack, it's download cache
            get<CacheDataSource.Factory>(CacheType.DOWNLOAD)
                // Read-only cache, player doesn't get to write anything in here
                .setCacheWriteDataSinkFactory( null )
                .setUpstreamDataSourceFactory(
                    // Next up is regular cache
                    get<CacheDataSource.Factory>(CacheType.CACHE)
                        // The final upstream handles 2 cases, local files and remote files
                        .setUpstreamDataSourceFactory( get<ResolvingDataSource.Factory>() )
                        // Player is allowed to write chunks into this storage.
                        .setCacheWriteDataSinkFactory(
                            CacheDataSink.Factory()
                                .setCache( get(CacheType.CACHE) )
                                // Chunks are small so recovery can work better
                                .setFragmentSize( CHUNK_LENGTH )
                                // Bigger than default buffer size to avoid
                                // constant write to disk, but small enough
                                // to avoid data loss if app crashes
                                .setBufferSize( 64 * 1024 )     // 64KiB
                        )
                )
        )
        dataSource.setLoadErrorHandlingPolicy( ErrorHandlingPolicy())
        //</editor-fold>
        //<editor-fold desc="Audio handlers">
        val handleAudioFocus = Preferences.AUDIO_SMART_PAUSE_DURING_CALLS.value
        val audioAttributes = AudioAttributes.Builder()
            .setUsage( C.USAGE_MEDIA )
            .setContentType( C.AUDIO_CONTENT_TYPE_MUSIC )
            .build()
        //</editor-fold>

        ExoPlayer.Builder( get() )
            .setMediaSourceFactory( dataSource )
            .setHandleAudioBecomingNoisy( true )
            .setWakeMode( C.WAKE_MODE_NETWORK )
            .setAudioAttributes( audioAttributes, handleAudioFocus )
            .setUsePlatformDiagnostics( false )
            .build()
    } bind MediaPlayer::class

    single<Player> { PlayerImpl(get()) }
}

enum class CacheType : Qualifier {
    CACHE, DOWNLOAD;

    override val value: QualifierValue = toString().lowercase()
}