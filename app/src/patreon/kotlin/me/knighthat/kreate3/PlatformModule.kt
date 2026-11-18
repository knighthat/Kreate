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

package me.knighthat.kreate3

import me.knighthat.kreate3.api.PatreonAuth
import me.knighthat.kreate3.patreon.PATREON_REDIRECT
import me.knighthat.kreate3.patreon.PatreonAccount
import me.knighthat.kreate3.patreon.PatreonSession
import me.knighthat.kreate3.ui.navigation.PatreonSettings
import me.knighthat.kreate3.ui.navigation.PlatformEntries
import me.knighthat.kreate3.ui.screen.settings.network.patreon.PatreonSettingsScreen
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** What only the patreon build has: its Patreon sign in, what it tells, and its page. */
val platformModule = module {
    single { PatreonAuth(get(), PATREON_REDIRECT) }
    // Created as the app starts, so it reads the secrets before anybody asks
    single(createdAtStart = true) { PatreonSession(get()) }
    single { PatreonAccount(androidContext(), get(), get(), get(), get()) }
    single<PlatformEntries> {
        PlatformEntries { onBack -> entry<PatreonSettings> { PatreonSettingsScreen(onBack) } }
    }
}
