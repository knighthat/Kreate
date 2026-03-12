package app.kreate.di

import me.knighthat.discord.Discord
import me.knighthat.discord.DiscordImpl
import kotlin.concurrent.atomics.ExperimentalAtomicApi


@OptIn(ExperimentalAtomicApi::class)
actual fun getDiscord(): Discord = DiscordImpl()