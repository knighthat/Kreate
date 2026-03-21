package app.kreate.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import app.kreate.android.Preferences
import app.kreate.android.service.download.CacheState
import app.kreate.android.service.download.CacheStateImpl
import it.fast4x.rimusic.enums.ExoPlayerCacheLocation
import org.koin.core.qualifier.Qualifier
import org.koin.core.qualifier.QualifierValue
import org.koin.dsl.module
import kotlin.io.path.createTempDirectory


private const val CACHE_DIRNAME = "exo_cache"
private const val DOWNLOAD_CACHE_DIRNAME = "exo_downloads"

@OptIn(UnstableApi::class)
private fun initCache(
    context: Context,
    preferences: Preferences.Long,
    cacheDirName: String
): Cache {
    val fromSetting by preferences

    val cacheEvictor = when( fromSetting ) {
        0L, Long.MAX_VALUE -> NoOpCacheEvictor()
        else -> LeastRecentlyUsedCacheEvictor( fromSetting )
    }
    val cacheDir = when( fromSetting ) {
        // Temporary directory deletes itself after close
        // It means songs remain on device as long as it's open
        0L -> createTempDirectory( cacheDirName ).toFile()

        // Looks a bit ugly but what it does is
        // check location set by user and return
        // appropriate path with [cacheDirName] appended.
        else -> when( Preferences.EXO_CACHE_LOCATION.value ) {
            ExoPlayerCacheLocation.System   -> context.cacheDir
            ExoPlayerCacheLocation.Private  -> context.filesDir
            ExoPlayerCacheLocation.SPLIT    -> if( cacheDirName == DOWNLOAD_CACHE_DIRNAME ) context.filesDir else context.cacheDir
        }.resolve( cacheDirName )
    }

    // Ensure this location exists
    cacheDir.mkdirs()

    return SimpleCache( cacheDir, cacheEvictor, StandaloneDatabaseProvider(context) )
}

val cacheModule = module {
    single( CacheType.CACHE ) {
        val context: Context = get()
        initCache( context, Preferences.EXO_CACHE_SIZE, CACHE_DIRNAME )
    }

    single( CacheType.DOWNLOAD ) {
        val context: Context = get()
        initCache( context, Preferences.EXO_DOWNLOAD_SIZE, DOWNLOAD_CACHE_DIRNAME )
    }

    single<CacheState> {
        @OptIn(UnstableApi::class)
        val cache: Cache = get(CacheType.CACHE)
        @OptIn(UnstableApi::class)
        val downloadCache: Cache = get(CacheType.DOWNLOAD)

        CacheStateImpl(cache, downloadCache)
    }
}

enum class CacheType : Qualifier {
    CACHE, DOWNLOAD;

    override val value: QualifierValue = toString().lowercase()
}