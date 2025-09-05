package app.kreate.android.di

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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.fast4x.rimusic.enums.ExoPlayerCacheLocation
import javax.inject.Named
import javax.inject.Singleton
import kotlin.io.path.createTempDirectory


@Module
@InstallIn(SingletonComponent::class)
@OptIn(UnstableApi::class)
object CacheModule {

    private const val CACHE_DIRNAME = "exo_cache"
    private const val DOWNLOAD_CACHE_DIRNAME = "exo_downloads"

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

    @Provides
    @Singleton
    @Named("cache")
    fun providesCache( @ApplicationContext context: Context ): Cache {
        // This call should only run once
        Preferences.load( context )

        return initCache( context, Preferences.EXO_CACHE_SIZE, CACHE_DIRNAME )
    }

    @Provides
    @Singleton
    @Named("downloadCache")
    fun providesDownloadCache( @ApplicationContext context: Context ): Cache {
        // This call should only run once
        Preferences.load( context )

        return initCache( context, Preferences.EXO_DOWNLOAD_SIZE, DOWNLOAD_CACHE_DIRNAME )
    }
}