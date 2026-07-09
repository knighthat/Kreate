package app.kreate.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module


expect val platformModules: Array<out Module>

private val globalModule = module {
    // A coroutine scope whose lifecycle is tied to the app's
    // It should be used unless the job is also intended to
    // survive as long as the app
    single { CoroutineScope(Dispatchers.Default + SupervisorJob()) }
}

fun initKoin( config: KoinAppDeclaration? = null ) {
    startKoin {
        config?.invoke( this )

        modules(
            globalModule,
            datastoreModule,
            databaseModule,
            viewModelModule,
            networkModule,
            imageModule,
            externalServicesModule,
            gatewayModule,
            *platformModules
        )
    }
}