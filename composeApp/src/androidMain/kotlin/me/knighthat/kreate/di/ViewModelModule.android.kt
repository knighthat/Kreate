package me.knighthat.kreate.di

import me.knighthat.kreate.viewmodel.AppTopBarViewModel
import me.knighthat.kreate.viewmodel.HomeScreenViewModel
import me.knighthat.kreate.viewmodel.SearchResultViewModel
import me.knighthat.kreate.viewmodel.SearchScreenViewModel
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module


actual val platformViewModel = module {
    singleOf(::TopLayoutConfiguration)
    singleOf(::SharedSearchProperties)
    viewModelOf(::HomeScreenViewModel)
    viewModelOf(::AppTopBarViewModel)
    viewModelOf(::SearchScreenViewModel)
    viewModelOf(::SearchResultViewModel)
}