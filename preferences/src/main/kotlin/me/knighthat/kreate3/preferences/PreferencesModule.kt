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

package me.knighthat.kreate3.preferences

import android.content.Context
import org.koin.dsl.module

/**
 * Pass it to `initKoin` to make [Preferences], [Secrets], [ProfileManager] and
 * [PreferencesBackup] available.
 */
val preferencesModule = module {
    single { ProfileManager(get(), get()) }
    single { PreferenceEngine(get(), get()) }
    single { SecretStore(get(), get(), get<Context>()) }
    single { PreferencesBackup(get<Context>()) }
}
