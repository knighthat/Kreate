package me.knighthat.kreate.di

import me.knighthat.kreate.preference.PrefType
import me.knighthat.kreate.preference.Storage
import org.koin.core.qualifier.named
import org.koin.dsl.module


actual val preferencesModule = module {
    single<Storage>( named(PrefType.PLAIN) ) {
        object : Storage {}
    }

    single<Storage>( named(PrefType.PRIVATE) ) {
        object : Storage {}
    }
}