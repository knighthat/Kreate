package it.fast4x.rimusic

import android.app.Application
import androidx.compose.runtime.getValue
import app.kreate.android.BuildConfig
import app.kreate.android.Preferences
import app.kreate.android.coil3.ImageFactory
import app.kreate.android.service.innertube.InnertubeProvider
import app.kreate.android.utils.CrashHandler
import app.kreate.android.utils.logging.RollingFileLoggingTree
import dagger.hilt.android.HiltAndroidApp
import me.knighthat.innertube.Innertube
import timber.log.Timber

@HiltAndroidApp
class MainApplication : Application() {

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
        ImageFactory.init( this )
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