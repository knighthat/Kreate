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

package me.knighthat.kreate.provider

import me.knighthat.kreate.provider.local.LocalProvider
import me.knighthat.kreate.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate.provider.youtube.YouTubeProvider
import org.koin.dsl.module

/**
 * Pass it to `initKoin` to make [Providers] and every [Provider] available. A new provider needs
 * to be added here and to `Origin`.
 */
val providerModule = module {
    single { LocalProvider(get(), get(), get()) }
    single { YouTubeProvider() }
    single { OpenSubsonicProvider(get(), get()) }
    single {
        Providers(
            listOf(
                get<LocalProvider>(),
                get<YouTubeProvider>(),
                get<OpenSubsonicProvider>()
            )
        )
    }
}
