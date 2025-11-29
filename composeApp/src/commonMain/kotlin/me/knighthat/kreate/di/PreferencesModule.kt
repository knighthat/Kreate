package me.knighthat.kreate.di

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import me.knighthat.kreate.preference.PrefDataStore
import me.knighthat.kreate.preference.PrefType
import me.knighthat.kreate.util.getConfigDir
import okio.Path.Companion.toOkioPath
import org.koin.core.qualifier.named
import org.koin.dsl.module


private const val PREFERENCES_FILENAME = "config.preferences_pb"
private const val CREDENTIALS_FILENAME = "credentials.preferences_pb"

val preferencesModule = module {
    single<PrefDataStore>(named(PrefType.PLAIN) ) {
        val file = getConfigDir().resolve( PREFERENCES_FILENAME )
        val path = file.toOkioPath()
        PreferenceDataStoreFactory.createWithPath { path }
    }

    single<PrefDataStore>(named(PrefType.PRIVATE) ) {
        val file = getConfigDir().resolve( CREDENTIALS_FILENAME )
        val path = file.toOkioPath()
        PreferenceDataStoreFactory.createWithPath { path }
    }
}