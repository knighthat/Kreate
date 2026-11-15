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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.ProfileManager
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * The foss build talks to no non-free network service: no Discord presence, no song recognition
 * (`BuildConfig.NON_FREE_NET` is off). Their switches are turned off in each profile once it is in
 * use, see [NonFreeNetOff].
 */
@Suppress("UNUSED_PARAMETER")
fun nonFreeNetModules(isSetup: Boolean): List<Module> = listOf(
    module { single(createdAtStart = true) { NonFreeNetOff(get(), get()) } }
)

/**
 * Turns off [Preferences.DISCORD_ENABLED] and [Preferences.SHAZAM_ENABLED] in the active profile,
 * at start and after each switch to another profile, so the settings say what the foss build does.
 */
class NonFreeNetOff(profileManager: ProfileManager, scope: CoroutineScope) {

    init {
        scope.launch {
            profileManager.activeId.filterNotNull().collect { id ->
                // Written before the profile's own values are in, they would be replaced by them
                Preferences.awaitProfile(id)
                Preferences.DISCORD_ENABLED.update(false)
                Preferences.SHAZAM_ENABLED.update(false)
            }
        }
    }
}
