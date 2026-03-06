package app.kreate.di

import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration


expect val platformModules: Array<out Module>

fun initKoin( config: KoinAppDeclaration? = null ) {
    startKoin {
        config?.invoke( this )

        modules( databaseModule, *platformModules )
    }
}