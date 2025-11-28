package me.knighthat.kreate

import android.app.Application
import me.knighthat.kreate.di.initKoin
import org.koin.android.ext.koin.androidContext


class MainApplication: Application() {

    override fun onCreate() {
        super.onCreate()

        initKoin {
            androidContext( this@MainApplication )
        }
    }
}