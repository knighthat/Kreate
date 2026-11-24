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

package me.knighthat.kreate3.update

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.knighthat.kreate3.constant.AutoUpdateMode
import me.knighthat.kreate3.preferences.GlobalPreferences
import me.knighthat.kreate3.preferences.Preferences

/**
 * In a build that doesn't update itself (no [AppUpdater]), keeps
 * [GlobalPreferences.AUTO_UPDATE_MODE] at `OFF`: at start and whenever it comes back (a reset to
 * defaults, a restored backup), so the setting says what the build does.
 */
class AutoUpdateOff(scope: CoroutineScope) {

    init {
        scope.launch {
            // Written before the stored values are in, it would be replaced by them
            Preferences.awaitLoaded()
            GlobalPreferences.AUTO_UPDATE_MODE.state.collect {
                if (it != AutoUpdateMode.OFF)
                    GlobalPreferences.AUTO_UPDATE_MODE.update(AutoUpdateMode.OFF)
            }
        }
    }
}
