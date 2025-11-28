package me.knighthat.kreate.di

import android.content.Context
import me.knighthat.kreate.preference.PrefType
import me.knighthat.kreate.preference.Storage
import org.koin.core.qualifier.named
import org.koin.dsl.module


private const val PREFERENCES_FILENAME = "preferences"
private const val CREDENTIALS_FILENAME = "credentials"

actual val preferencesModule = module {
    single<Storage>( named(PrefType.PLAIN) ) {
        val context: Context = get()
        context.getSharedPreferences( PREFERENCES_FILENAME, Context.MODE_PRIVATE )
    }

    single<Storage>( named(PrefType.PRIVATE) ) {
        val context: Context = get()
        context.getSharedPreferences( CREDENTIALS_FILENAME, Context.MODE_PRIVATE )
    }
}