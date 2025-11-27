package me.knighthat.kreate

import android.app.Application
import android.content.SharedPreferences
import dagger.hilt.android.HiltAndroidApp
import me.knighthat.kreate.preference.Preferences
import javax.inject.Inject
import javax.inject.Named


@HiltAndroidApp
class MainApplication: Application() {

    @Inject
    @Named("plain")
    lateinit var preferences: SharedPreferences
    @Inject
    @Named("private")
    lateinit var credentialsPreferences: SharedPreferences

    override fun onCreate() {
        super.onCreate()

        // Must run at the earliest
        Preferences.load( preferences, credentialsPreferences )
    }
}