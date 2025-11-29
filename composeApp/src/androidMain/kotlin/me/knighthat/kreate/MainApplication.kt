package me.knighthat.kreate

import android.app.Application
import me.knighthat.kreate.di.initKoin
import me.knighthat.kreate.logging.setupLogging
import me.knighthat.kreate.util.CrashHandler
import org.koin.android.ext.koin.androidContext


class MainApplication: Application() {

    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler( CrashHandler() )

        initKoin {
            androidContext( this@MainApplication )
        }

        setupLogging()
    }
}