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

package me.knighthat.kreate3.playback

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.knighthat.kreate3.playback.PlayerBuild.Companion.now
import me.knighthat.kreate3.preferences.Preferences

/**
 * The settings a player is made with: a change to one applies from the next player, see
 * [PlaybackLifecycle.restartPlayer]. The other settings of playback apply while it plays.
 *
 * @param keepPlayed see [Preferences.PLAYBACK_KEEP_PLAYED]
 */
data class PlayerBuild(val keepPlayed: Boolean) {

    companion object {

        /** What the settings ask for now. */
        fun now(): PlayerBuild = PlayerBuild(Preferences.PLAYBACK_KEEP_PLAYED.value)

        /** [now], whenever a setting changes. */
        val changes: Flow<PlayerBuild> = Preferences.PLAYBACK_KEEP_PLAYED.state.map(::PlayerBuild)
    }
}
