package it.fast4x.rimusic

import android.app.Application
import androidx.compose.runtime.getValue
import app.kreate.Platform
import app.kreate.android.BuildConfig
import app.kreate.android.Preferences
import app.kreate.android.service.innertube.InnertubeProvider
import app.kreate.android.utils.CrashHandler
import app.kreate.android.utils.logging.RollingFileLoggingTree
import app.kreate.coil3.ImageFactory
import dagger.hilt.android.HiltAndroidApp
import me.knighthat.innertube.Innertube
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class MainApplication : Application() {

    @Inject
    lateinit var imageFactoryProvider: ImageFactory.Provider

    override fun onCreate() {
        Preferences.load( this )

        super.onCreate()
        //DatabaseInitializer()
        Dependencies.init(this)

        Thread.setDefaultUncaughtExceptionHandler( CrashHandler(this) )

        val isRuntimeLogEnabled by Preferences.RUNTIME_LOG
        val fileCount by Preferences.RUNTIME_LOG_FILE_COUNT
        val maxSizePerFile by Preferences.RUNTIME_LOG_MAX_SIZE_PER_FILE
        if( isRuntimeLogEnabled && fileCount > 0 && maxSizePerFile > 0 )
            Timber.plant( RollingFileLoggingTree(cacheDir, fileCount, maxSizePerFile) )

        if( BuildConfig.DEBUG || (isRuntimeLogEnabled && Preferences.RUNTIME_LOG_SHARED.value) )
            Timber.plant( Timber.DebugTree() )

        Innertube.setProvider( InnertubeProvider() )
        Platform.imageFactoryProvider = this.imageFactoryProvider
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