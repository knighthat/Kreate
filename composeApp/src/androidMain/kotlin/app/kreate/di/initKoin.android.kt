package app.kreate.di

import app.kreate.components.settings.ActionHandler
import app.kreate.components.settings.ActionHandlerImpl
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module


actual val platformModules: Array<out Module> = arrayOf(
    playerModule,
    playbackModule,
    profileModule,
    module {
        singleOf<ActionHandler>( ::ActionHandlerImpl )
    }
)