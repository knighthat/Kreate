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
import me.knighthat.kreate3.api.PatreonFiles
import me.knighthat.kreate3.patreon.CloudBackups
import me.knighthat.kreate3.patreon.PATREON_REDIRECT
import me.knighthat.kreate3.patreon.PatreonAccount
import me.knighthat.kreate3.patreon.PatreonSession
import me.knighthat.kreate3.patreon.RemoteBackups
import me.knighthat.kreate3.ui.navigation.PatreonSettings
import me.knighthat.kreate3.ui.navigation.PlatformEntries
import me.knighthat.kreate3.ui.screen.settings.backup.RemoteBackupPage
import me.knighthat.kreate3.ui.screen.settings.backup.RemoteBackupsPage
import me.knighthat.kreate3.ui.screen.settings.backup.RemoteBackupsViewModel
import me.knighthat.kreate3.ui.screen.settings.network.patreon.PatreonSettingsScreen
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * What only the patreon build has: its Patreon sign in, what it tells, its page and the backups
 * kept online.
 */
val platformModule = module {
    single { PatreonAuth(get(), PATREON_REDIRECT) }
    // Created as the app starts, so it reads the secrets before anybody asks
    single(createdAtStart = true) { PatreonSession(get()) }
    // At start too, it refreshes a token from before the features claim
    single(createdAtStart = true) { PatreonAccount(androidContext(), get(), get(), get(), get()) }
    single { PatreonFiles(get()) }
    // Created as the app starts too, so the Backup page knows at once whether Remote can open
    single(createdAtStart = true) { RemoteBackups(androidContext(), get(), get(), get(), get()) }
    viewModel { RemoteBackupsViewModel(androidContext(), get(), get(), get(), get(), get(), get()) }
    single { RemoteBackupPage(get<RemoteBackups>().active) { RemoteBackupsPage(it) } }
    // Checks as the app starts whether a cloud backup is due, see CloudBackups
    single(createdAtStart = true) {
        CloudBackups(androidContext(), get(), get(), get(), get()).also { it.start() }
    }
    single<PlatformEntries> {
        PlatformEntries { onBack -> entry<PatreonSettings> { PatreonSettingsScreen(onBack) } }
    }
}
