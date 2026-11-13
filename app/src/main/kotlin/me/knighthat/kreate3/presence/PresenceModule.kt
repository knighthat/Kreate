/*
 * Kreate
 * Copyright (C) 2026 KnightHat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.knighthat.kreate3.presence

import io.ktor.client.HttpClient
import me.knighthat.kreate3.BuildConfig
import me.knighthat.kreate3.discord.DiscordAuth
import me.knighthat.kreate3.discord.DiscordClient
import me.knighthat.kreate3.provider.HttpClientFactory
import me.knighthat.kreate3.provider.networkProxy
import me.knighthat.kreate3.provider.proxy
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/** The client Discord's requests and gateway go through. */
private val DISCORD_HTTP = named("discord")

/**
 * Discord's Rich Presence: the account of each profile, what the app is doing and the
 * connection showing it.
 *
 * @param isSetup whether the setup wizard was finished, the presence starts with the app then
 */
fun presenceModule(isSetup: Boolean) = module {
    // The app's client through the app's proxy, without a read timeout: the gateway is quiet for
    // the 40 seconds between heartbeats
    single(DISCORD_HTTP) {
        val okHttp = get<OkHttpClient>().newBuilder()
            .readTimeout(0, TimeUnit.SECONDS)
            .proxy("network", ::networkProxy)
            .build()
        get<HttpClientFactory>().create(okHttp)
    }
    single {
        DiscordAuth(
            http = get(DISCORD_HTTP),
            appId = BuildConfig.DISCORD_APP_ID,
            redirectUri = BuildConfig.DISCORD_REDIRECT_URI,
        )
    }
    single { DiscordClient(get<HttpClient>(DISCORD_HTTP), BuildConfig.DISCORD_APP_ID) }
    single { DiscordAccount(androidContext(), get(), get(), get(), get()) }
    single { PresenceState() }
    // Covers go to litterbox through the app's client, like any request of the app
    single { CoverUploads(androidContext(), get()) }
    single(createdAtStart = isSetup) {
        DiscordPresence(
            context = androidContext(),
            state = get(),
            client = get(),
            account = get(),
            uploads = get(),
            providers = get(),
            artists = get(),
            youTube = get(),
            profiles = get(),
            scope = get(),
        )
    }
}
