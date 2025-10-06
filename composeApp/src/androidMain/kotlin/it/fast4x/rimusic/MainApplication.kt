package it.fast4x.rimusic

import android.app.Application
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.runtime.getValue
import androidx.core.content.getSystemService
import app.kreate.Platform
import app.kreate.android.BuildConfig
import app.kreate.android.Preferences
import app.kreate.android.service.innertube.InnertubeProvider
import app.kreate.android.utils.ConnectivityUtils
import app.kreate.android.utils.CrashHandler
import app.kreate.android.utils.logging.RollingFileLoggingTree
import app.kreate.coil3.ImageFactory
import dagger.hilt.android.HiltAndroidApp
import me.knighthat.innertube.Innertube
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Named

@HiltAndroidApp
class MainApplication : Application() {

    @Inject
    @Named("plain")
    lateinit var preferences: SharedPreferences
    @Inject
    @Named("private")
    lateinit var encryptedPreferences: SharedPreferences

    @Inject
    lateinit var imageFactoryProvider: ImageFactory.Provider

    override fun onCreate() {
        super.onCreate()

        Preferences.load( preferences, encryptedPreferences )

        //DatabaseInitializer()
        Dependencies.init(this)

        Thread.setDefaultUncaughtExceptionHandler( CrashHandler(this) )

        val isRuntimeLogEnabled by Preferences.RUNTIME_LOG
        val fileCount by Preferences.RUNTIME_LOG_FILE_COUNT
        val maxSizePerFile by Preferences.RUNTIME_LOG_MAX_SIZE_PER_FILE
        if( isRuntimeLogEnabled && fileCount > 0 && maxSizePerFile > 0 )
            Timber.plant( RollingFileLoggingTree(this, fileCount, maxSizePerFile) )

        if( BuildConfig.DEBUG || (isRuntimeLogEnabled && Preferences.RUNTIME_LOG_SHARED.value) )
            Timber.plant( Timber.DebugTree() )

        Innertube.setProvider( InnertubeProvider() )
        Platform.imageFactoryProvider = this.imageFactoryProvider

        // Register network callback
        getSystemService<ConnectivityManager>()?.run {
            val networkRequest: NetworkRequest = NetworkRequest.Builder()
                                                               .addCapability( NetworkCapabilities.NET_CAPABILITY_INTERNET )
                                                               .build()
            registerNetworkCallback( networkRequest, ConnectivityUtils )
        }
    }

    override fun onTerminate() {
        Preferences.unload()

        super.onTerminate()
    }
}

object Dependencies {
    lateinit var application: MainApplication
        private set

    internal fun init(application: MainApplication) {
        this.application = application
    }
}