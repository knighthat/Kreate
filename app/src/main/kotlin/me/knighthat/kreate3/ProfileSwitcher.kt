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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.knighthat.kreate3.database.ActiveUser
import me.knighthat.kreate3.playback.PlaybackLifecycle
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.ProfileManager

/**
 * Changes the active profile with playback stopped, see [PlaybackLifecycle.restartAround]: what
 * the player saves goes to the profile it played in, and the player starts again with the queue
 * of the new profile once its user and settings are in use.
 *
 * Runs in [scope], so leaving the screen doesn't leave playback stopped halfway.
 */
class ProfileSwitcher(
    private val profileManager: ProfileManager,
    private val profileUserSync: ProfileUserSync,
    private val activeUser: ActiveUser,
    private val playback: PlaybackLifecycle,
    private val scope: CoroutineScope,
) {

    /**
     * Makes the profile with [profileId] active. Unknown ids and the active profile are ignored.
     */
    fun switchTo(profileId: String): Job = scope.launch {
        if (profileId == profileManager.activeId.value) return@launch
        if (profileId !in profileManager.ids.first()) return@launch

        playback.restartAround {
            profileManager.switchTo(profileId)
            awaitInUse(profileId)
        }
    }

    /**
     * Deletes the profile, see [ProfileUserSync.deleteProfile]. Deleting the active profile makes
     * the default one active, playback stops around it like for [switchTo].
     */
    fun delete(profileId: String): Job = scope.launch {
        if (profileId != profileManager.activeId.value) {
            profileUserSync.deleteProfile(profileId)
            return@launch
        }

        playback.restartAround {
            if (!profileUserSync.deleteProfile(profileId)) return@restartAround
            val next = profileManager.activeId.first { it != null && it != profileId }!!
            awaitInUse(next)
        }
    }

    /** Waits until the repositories and the settings follow [profileId]. */
    private suspend fun awaitInUse(profileId: String) {
        activeUser.id.first { it == profileId }
        Preferences.awaitProfile(profileId)
    }
}
