package app.kreate.di

import org.koin.core.module.Module


actual val platformModules: Array<out Module> = arrayOf( preferencesModule, cacheModule )