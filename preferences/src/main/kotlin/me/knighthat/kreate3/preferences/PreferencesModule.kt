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
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** The [PreferenceEngine] of the global file, [GlobalPreferences] and [ProfileManager] share it */
internal val GLOBAL_ENGINE = named("global_preferences")

/**
 * Pass it to `initKoin` to make [Preferences], [GlobalPreferences], [Secrets], [ProfileManager]
 * and [PreferencesBackup] available.
 */
val preferencesModule = module {
    single { FailureReports() }
    single(GLOBAL_ENGINE) {
        GlobalPreferenceStorage.engine(get(), get<Context>().filesDir, get())
    }
    single {
        ProfileManager(
            scope = get(),
            storage = ProfileManager.profileStorage(get<Context>().filesDir, get()),
            global = get(GLOBAL_ENGINE),
            reports = get(),
        )
    }
    single { PreferenceEngine(get<ProfileManager>(), get()) }
    single { SecretStore(get(), get(), get<Context>()) }
    single { PreferencesBackup(get<Context>()) }
}
