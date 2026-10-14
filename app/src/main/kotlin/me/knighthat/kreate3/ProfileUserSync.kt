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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.knighthat.kreate3.database.ActiveUser
import me.knighthat.kreate3.database.model.User
import me.knighthat.kreate3.database.repository.UserRepository
import me.knighthat.kreate3.preferences.Profile
import me.knighthat.kreate3.preferences.ProfileManager
import kotlin.uuid.Uuid

/**
 * Keeps the database `user` table in step with the profile ids of [ProfileManager], without
 * either module knowing about the other. A profile and its user share the same id.
 *
 * The profile only has an id, so the user is the only place holding the name. A user is
 * created for every id that has none, an existing user is never overwritten.
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

    // Ids that already have a user during this run, so that switching profile doesn't query every row
    private val synced = mutableSetOf<String>()

    // Active profile as of the last run, to tell a switch from a start-up
    private var activeId: String? = null

    override val id: StateFlow<String?> =
        combine(profileManager.ids, profileManager.activeId) { ids, active ->
            ids to active
        }.map { (ids, active) ->
            ids.forEach { id ->
                if (id in synced) return@forEach

                if (userRepository.find(id).first() == null)
                    userRepository.save(
                        User(
                            id,
                            if (id == Profile.DEFAULT_ID) DEFAULT_NAME else FALLBACK_NAME
                        )
                    )
                synced += id
            }

            val current = active
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
     * Creates a profile named [name]. The user is stored before the id is registered,
     * so the profile never shows up without its name.
     *
     * @return the id of the new profile
     */
    suspend fun createProfile(name: String): String {
        val id = Uuid.random().toString()
        userRepository.save(User(id, name))
        synced += id
        return profileManager.add(id)
    }

    /**
     * Deletes the profile and its preferences, then the user with everything it owns
     * (library, playlists, history and queue). The shared catalog stays.
     *
     * @return `false` when there is no such profile
     * @throws IllegalArgumentException for the default profile
     */
    suspend fun deleteProfile(profileId: String): Boolean {
        if (!profileManager.delete(profileId)) return false

        synced -= profileId
        userRepository.delete(profileId)
        return true
    }

    private companion object {

        const val DEFAULT_NAME = "Default"
        const val FALLBACK_NAME = "Profile"
    }
}
