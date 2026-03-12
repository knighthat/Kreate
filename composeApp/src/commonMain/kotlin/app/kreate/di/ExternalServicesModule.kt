package app.kreate.di

import me.knighthat.discord.Discord
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module


expect fun getDiscord(): Discord

val externalServicesModule = module {
    singleOf( ::getDiscord )
}