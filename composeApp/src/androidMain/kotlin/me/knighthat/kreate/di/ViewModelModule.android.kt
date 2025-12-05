package me.knighthat.kreate.di

import me.knighthat.kreate.viewmodel.AppTopBarViewModel
import me.knighthat.kreate.viewmodel.HomeScreenViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module


actual val platformViewModel = module {
    singleOf(::TopLayoutConfiguration)
    viewModelOf(::HomeScreenViewModel)
    viewModelOf(::AppTopBarViewModel)
}