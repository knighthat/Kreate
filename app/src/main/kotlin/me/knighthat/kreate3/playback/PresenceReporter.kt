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

import androidx.media3.common.C
import androidx.media3.common.Player
import me.knighthat.kreate3.presence.Doing
import me.knighthat.kreate3.presence.PresenceState

/**
 * Tells [state] which song is in [player], whether it plays and where it is, for Discord. A
 * stopped player (nothing queued, idle or at the end) has no song. Used on the player's thread.
 */
internal class PresenceReporter(
    private val player: Player,
    private val state: PresenceState,
) : Player.Listener {

    init {
        player.addListener(this)
        report()
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.containsAny(
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_MEDIA_METADATA_CHANGED,
            )
        ) report()
    }

    /** Stops telling, the player is going away; it has no song from now on. */
    fun release() {
        player.removeListener(this)
        state.play(null)
    }

    private fun report() {
        val item = player.currentMediaItem
        if (item == null
            || player.playbackState == Player.STATE_IDLE
            || player.playbackState == Player.STATE_ENDED
        ) {
            state.play(null)
            return
        }

        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
            ?: item.mediaMetadata.durationMs?.takeIf { it > 0 }
        state.play(
            Doing.Listening(
                item = item,
                // Still playing while it loads, paused only when the user paused it
                playing = player.playWhenReady,
                position = player.currentPosition,
                duration = duration,
                at = System.currentTimeMillis(),
            )
        )
    }
}
