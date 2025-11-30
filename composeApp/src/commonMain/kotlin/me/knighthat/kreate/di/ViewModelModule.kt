package me.knighthat.kreate.di

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module


val viewModelModule = module {
    singleOf( ::TopLayoutConfiguration )
}