package me.knighthat.kreate.di

import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module


expect val platformViewModel: Module

val viewModel = module {
    singleOf(::TopLayoutConfiguration)
}