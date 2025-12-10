package me.knighthat.kreate

import android.app.Application
import coil3.SingletonImageLoader
import me.knighthat.innertube.Innertube
import me.knighthat.kreate.coil.setupCoil
import me.knighthat.kreate.di.initKoin
import me.knighthat.kreate.logging.KoinBufferedLogger
import me.knighthat.kreate.logging.setupLogging
import me.knighthat.kreate.service.InnertubeProviderImpl
import me.knighthat.kreate.util.CrashHandler
import org.koin.android.ext.koin.androidContext


class MainApplication: Application() {

    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler( CrashHandler() )

        val koinLogger = KoinBufferedLogger()
        initKoin {
            logger( koinLogger )

            androidContext( this@MainApplication )
        }

        setupLogging( koinLogger )

        // ImageLoader is placed here to make sure it's set up before anything
        // Services like playback and AA can be sure to have imageLoader
        // without MainActivity even loaded
        SingletonImageLoader.setSafe( ::setupCoil )
        // Setup Innertube as early as possible
        Innertube.setProvider( InnertubeProviderImpl() )
    }
}