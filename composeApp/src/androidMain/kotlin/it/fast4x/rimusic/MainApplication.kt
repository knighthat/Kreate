package it.fast4x.rimusic

import android.app.Application
import androidx.compose.runtime.getValue
import app.kreate.android.BuildConfig
import app.kreate.android.Preferences
import app.kreate.android.utils.CrashHandler
import app.kreate.android.utils.logging.RollingFileLoggingTree
import timber.log.Timber

class MainApplication : Application() {

    override fun onCreate() {
        Preferences.load( this )

        super.onCreate()
        //DatabaseInitializer()
        Dependencies.init(this)

        Thread.setDefaultUncaughtExceptionHandler( CrashHandler(this) )

        val isRuntimeLogEnabled by Preferences.RUNTIME_LOG
        if( isRuntimeLogEnabled )
            Timber.plant( RollingFileLoggingTree(cacheDir) )

        if( BuildConfig.DEBUG || (isRuntimeLogEnabled && Preferences.RUNTIME_LOG_SHARED.value) )
            Timber.plant( Timber.DebugTree() )
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