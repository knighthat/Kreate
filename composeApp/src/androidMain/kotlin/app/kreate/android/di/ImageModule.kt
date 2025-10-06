package app.kreate.android.di

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.ui.util.fastCoerceAtLeast
import app.kreate.AppIcon
import app.kreate.android.Preferences
import app.kreate.android.service.NetworkService
import app.kreate.coil3.ImageFactory
import coil3.ImageLoader
import coil3.asImage
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import it.fast4x.rimusic.thumbnail
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object ImageModule {

    @Provides
    @Singleton
    fun providesDiskCache(
        @ApplicationContext context: Context
    ): DiskCache =
        DiskCache.Builder()
                 .directory(
                     context.cacheDir.resolve( "coil3" )
                 )
                 // DiskCache.Builder doesn't allow 0 byte.
                 // `1` is there for the sake of creating this,
                 // but won't be added to ImageLoader
                 .maxSizeBytes(
                     size = Preferences.IMAGE_CACHE_SIZE
                                       .value
                                       .fastCoerceAtLeast( 1L )
                 )
                 .cleanupCoroutineContext( Dispatchers.IO )
                 .build()

    @Provides
    @Singleton
    fun providesImageLoader(
        @ApplicationContext context: Context,
        diskCache: DiskCache
    ): ImageLoader =
        // TODO: Add a toggle in setting that let user enable network caching
        //   This feature will set an expiration date on cache, forcing
        //   user to "re-fetch" the image data again after a period of time.
        //   This will potentially double the storage.
        ImageLoader.Builder( context )
                   .crossfade( true )
                   .diskCachePolicy( CachePolicy.ENABLED )
                   .error(
                       AppIcon.bitmap( context ).asImage()
                   )
                   .components {
                       add(
                           KtorNetworkFetcherFactory(NetworkService.client)
                       )
                   }
                   .apply {
                       val cacheSize by Preferences.IMAGE_CACHE_SIZE
                       if( cacheSize > 0 )
                           diskCache( diskCache )
                   }
                   .build()

    @Provides
    @Singleton
    fun providesImageFactoryProvider(
        @ApplicationContext context: Context,
        diskCache: DiskCache,
        imageLoader: ImageLoader
    ): ImageFactory.Provider =
        object: ImageFactory.Provider {

            override val diskCache: DiskCache by lazy { diskCache }
            override val imageLoader: ImageLoader by lazy { imageLoader }

            override fun requestBuilder(
                thumbnailUrl: String?,
                builder: ImageRequest.Builder.() -> Unit
            ): ImageRequest =
                /*
                 * TODO: Make a simple system to detect network speed and/or
                 *  data saver that automatically lower the quality to
                 *  reduce loading time and to preserve data usage.
                 */
                ImageRequest.Builder( context )
                            .data( thumbnailUrl.thumbnail( ImageFactory.thumbnailSize ) )
                            .diskCacheKey( thumbnailUrl )
                            .apply( builder )
                            .build()
        }
}