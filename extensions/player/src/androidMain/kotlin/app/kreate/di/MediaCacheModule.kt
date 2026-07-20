@file:androidx.media3.common.util.UnstableApi

package app.kreate.di

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import app.kreate.UserAgents
import app.kreate.internal.resolvers.resolveInnertubeMedia
import app.kreate.preferences.Preferences
import app.kreate.utils.isLocalFile
import it.fast4x.rimusic.enums.ExoPlayerCacheLocation
import okhttp3.OkHttpClient
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.QualifierValue
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.io.path.createTempDirectory


private const val CACHE_DIRNAME = "exo_cache"
private const val DOWNLOAD_CACHE_DIRNAME = "exo_downloads"

internal val PLAYBACK_DATA_SOURCE = named("PLAYBACK_DATA_SOURCE")
internal val DOWNLOAD_DATA_SOURCE = named("DOWNLOAD_DATA_SOURCE")

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

    return SimpleCache(cacheDir, cacheEvictor, StandaloneDatabaseProvider(context))
}

val mediaCacheModule = module {
    //<editor-fold desc="Cache">
    // Each cache instance is the source of truth, 2 different Cache can't point to the same folder.

    single( CacheType.CACHE ) {
        initCache( get(), Preferences.EXO_CACHE_SIZE.value, CACHE_DIRNAME )
    }
    single( CacheType.DOWNLOAD ) {
        initCache( get(), Preferences.EXO_DOWNLOAD_SIZE.value, DOWNLOAD_CACHE_DIRNAME )
    }
    //</editor-fold>
    //<editor-fold desc="DataSources">
    // DataSource facilitates read and write operations, it cannot be operated by multiple services
    // at the same time, so DataSource must be created separately for each service, hence factory

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
    factory( PLAYBACK_DATA_SOURCE ) {
        ResolvingDataSource.Factory(
            // Player has the ability to play from local file, so DefaultDataSource is required
            // to for read purpose, combined with OkHttpDataSource to get remote streaming.
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
    factory( DOWNLOAD_DATA_SOURCE ) {
        ResolvingDataSource.Factory(
            // Download's ResolvingDataSource has no business reading local files,
            // so only OkHttpDataSource is provided. And uri points to local file gets error
            OkHttpDataSource.Factory(get<OkHttpClient>()).setUserAgent( UserAgents.CHROME_WINDOWS )
        ) { dataSpec ->
            if ( dataSpec.uri.isLocalFile() )
                error( "Cannot download local file ${dataSpec.uri}" )
            else
                resolveInnertubeMedia( dataSpec )
        }
    }
    //</editor-fold>
}

enum class CacheType : Qualifier {
    CACHE, DOWNLOAD;

    override val value: QualifierValue = toString().lowercase()
}
