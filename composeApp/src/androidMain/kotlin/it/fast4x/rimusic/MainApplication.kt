package it.fast4x.rimusic

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.getSystemService
import androidx.lifecycle.ProcessLifecycleOwner
import app.kreate.android.BuildConfig
import app.kreate.android.drawable.AppIcon
import app.kreate.android.utils.ConnectivityUtils
import app.kreate.android.utils.CrashHandler
import app.kreate.di.THUMBNAIL_SIZE
import app.kreate.di.initKoin
import app.kreate.logging.CoilLogger
import app.kreate.logging.KoinBufferedLogger
import app.kreate.logging.TimberToKermitLogger
import app.kreate.logging.setupLogging
import app.kreate.util.setDebugMode
import app.kreate.util.setFlavorArch
import app.kreate.util.setFlavorEnv
import app.kreate.util.setVersionName
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import io.ktor.client.HttpClient
import it.fast4x.rimusic.utils.AppLifecycleTracker
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import timber.log.Timber


class MainApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()

        // Set this at the earliest to lock the setter
        setDebugMode( BuildConfig.DEBUG )
        setVersionName( BuildConfig.VERSION_NAME )
        setFlavorArch( BuildConfig.FLAVOR_arch )
        setFlavorEnv( BuildConfig.FLAVOR_env )

        Thread.setDefaultUncaughtExceptionHandler( CrashHandler(this) )

        val koinLogger = KoinBufferedLogger()
        initKoin {
            logger( koinLogger )

            androidContext( this@MainApplication )
        }

        setupLogging( koinLogger )
        Timber.uprootAll()      // Clear all
        Timber.plant( TimberToKermitLogger() )

        // Register network callback
        getSystemService<ConnectivityManager>()?.run {
            val networkRequest: NetworkRequest = NetworkRequest.Builder()
                                                               .addCapability( NetworkCapabilities.NET_CAPABILITY_INTERNET )
                                                               .build()
            registerNetworkCallback( networkRequest, ConnectivityUtils )
        }
        // Register app lifecycle tracker
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleTracker)
    }

    override fun newImageLoader( context: PlatformContext ): ImageLoader {
        val client: HttpClient by inject()
        val diskCache: DiskCache by inject()
        val memoryCache: MemoryCache by inject()
        val appIcon = AppIcon.bitmap( context, THUMBNAIL_SIZE ).asImage()

        // TODO: Add a toggle in setting that let user enable network caching
        // This feature will set an expiration date on cache, forcing
        // user to "re-fetch" the image data again after a period of time.
        // This will potentially double the storage.
        return ImageLoader.Builder(context)
                          .logger( CoilLogger() )
                          .coroutineContext( Dispatchers.IO )
                          .decoderCoroutineContext( Dispatchers.Default )
                          .crossfade( true )
                          .error( appIcon )
                          .memoryCache( memoryCache )
                          .diskCache {
                              if( diskCache.maxSize > 1 )
                                  diskCache
                              else
                                  null
                          }
                          .components {
                              add(
                                  KtorNetworkFetcherFactory(client)
                              )
                          }
                          .build()
    }
}
