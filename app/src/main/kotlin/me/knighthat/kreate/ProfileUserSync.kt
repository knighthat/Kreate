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

package me.knighthat.kreate

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.knighthat.kreate.database.ActiveUser
import me.knighthat.kreate.database.model.User
import me.knighthat.kreate.database.repository.UserRepository
import me.knighthat.kreate.preferences.ProfileManager

/**
 * Keeps the database `user` table in step with the profiles of [ProfileManager], without
 * either module knowing about the other. A profile and its user share the same id.
 *
 * It is also the [ActiveUser]. The id is only published after every profile has a row,
 * so nothing is written for a user that doesn't exist yet.
 *
 * Users are never removed by looking at the profile list, which can be empty before the
 * default profile exists. Use [deleteProfile] to remove a profile.
 */
class ProfileUserSync(
    private val profileManager: ProfileManager,
    private val userRepository: UserRepository,
    scope: CoroutineScope,
) : ActiveUser {

    // Profiles already written during this run, so that switching profile doesn't touch every row
    private val synced = mutableMapOf<String, String>()

    // Active profile as of the last run, to tell a switch from a start-up
    private var activeId: String? = null

    override val id: StateFlow<String?> =
        combine(profileManager.profiles, profileManager.activeProfile) { profiles, active ->
            profiles to active
        }.map { (profiles, active) ->
            profiles.forEach { profile ->
                if (synced[profile.id] == profile.name) return@forEach

                // Keep what the database already knows about the user, like its thumbnail and activity times
                val user = userRepository.find(profile.id).first()?.copy(name = profile.name)
                    ?: User(profile.id, profile.name)
                userRepository.save(user)
                synced[profile.id] = profile.name
            }

            val current = active?.id
            if (current != activeId) {
                val previous = activeId
                activeId = current

                // The profile we leave was last seen now
                if (previous != null) userRepository.markSeen(previous)

                // A switch starts a new activity period. On start-up there is no switch,
                // only a profile that never was active gets its first time
                val isSwitch = previous != null
                if (current != null && (isSwitch || userRepository.find(current)
                        .first()?.activeSince == null)
                )
                    userRepository.markActive(current)
            }
            current
        }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * Deletes the profile and its preferences, then the user with everything it owns
     * (library, playlists, history and queue). The shared catalog stays.
     *
     * @return `false` when there is no such profile
     * @throws IllegalArgumentException for the default profile
     */
    suspend fun deleteProfile(profileId: String): Boolean {
        if (!profileManager.delete(profileId)) return false

        userRepository.delete(profileId)
        return true
    }
}
