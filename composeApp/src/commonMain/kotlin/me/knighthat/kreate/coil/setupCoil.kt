package me.knighthat.kreate.coil

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import me.knighthat.kreate.logging.CoilLogger
import me.knighthat.kreate.preference.Preferences
import me.knighthat.kreate.util.getCacheDir
import okio.Path.Companion.toOkioPath
import org.koin.java.KoinJavaComponent.inject


fun setupCoil( context: PlatformContext ): ImageLoader {
    val httpClient: HttpClient by inject( HttpClient::class.java )

    val builder =
        ImageLoader.Builder( context )
                   .logger( CoilLogger() )
                   .coroutineContext(Dispatchers.IO )
                   .crossfade( true )
                   .decoderCoroutineContext( Dispatchers.Default )
                   .components {
                       add(
                           KtorNetworkFetcherFactory(httpClient)
                       )
                   }
    //<editor-fold desc="Memory cache">
    val memoryCache =
        MemoryCache.Builder().maxSizePercent( context, .2 ).build()
    builder.memoryCachePolicy( CachePolicy.ENABLED )
           .memoryCache( memoryCache )
    //</editor-fold>
    //<editor-fold desc="Disk cache">
    val path = getCacheDir().resolve( "coil_cache" )
    path.mkdirs()
    val cacheSize = Preferences.IMAGE_CACHE_MAX_SIZE.value
    val diskCache =
        DiskCache.Builder()
                 .maxSizeBytes( cacheSize )
                 .cleanupCoroutineContext( Dispatchers.IO )
                 .directory( path.toOkioPath() )
                 .build()
    builder.diskCachePolicy( CachePolicy.ENABLED )
           .diskCache( diskCache )
    //</editor-fold>

    return builder.build()
}
