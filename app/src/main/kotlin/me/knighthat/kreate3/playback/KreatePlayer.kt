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

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * The app's player. Repeating one song and shuffling exclude each other, whoever sets them, the
 * app or the system's media controls: repeating one turns shuffling off, and shuffling turns
 * repeating one off. Repeating all and shuffling go together.
 *
 * Songs added with [playNext] while shuffling play next in the shuffled order too, until shuffling
 * is turned on or off: the shuffle then takes them like any other song still to play. Added while
 * not shuffling, they sit at a random spot of the shuffled order, as every added song does.
 */
@OptIn(UnstableApi::class)
class KreatePlayer(private val player: ExoPlayer) : ExoPlayer by player {

    private val placement = NextPlacement()

    init {
        player.shuffleOrder = KreateShuffleOrder(placement)
    }

    override fun getAudioSessionId(): Int = player.audioSessionId

    override fun setRepeatMode(repeatMode: Int) {
        if (repeatMode == Player.REPEAT_MODE_ONE) shuffleModeEnabled = false
        player.repeatMode = repeatMode
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        if (shuffleModeEnabled && player.repeatMode == Player.REPEAT_MODE_ONE)
            player.repeatMode = Player.REPEAT_MODE_OFF
        if (shuffleModeEnabled == player.shuffleModeEnabled) return

        // The order changes while it's not used, so the next song doesn't change under the player
        if (shuffleModeEnabled) scramblePinned()
        player.shuffleModeEnabled = shuffleModeEnabled
        if (!shuffleModeEnabled) scramblePinned()
    }

    /**
     * Adds [items] right after the current item, in their order, and plays them next in the
     * shuffled order too when shuffling. With nothing queued they become the queue.
     */
    fun playNext(items: List<MediaItem>) {
        if (items.isEmpty()) return
        if (mediaItemCount == 0) return addMediaItems(items)

        val current = currentMediaItemIndex
        placement.anchor = if (shuffleModeEnabled) current else C.INDEX_UNSET
        try {
            // The shuffled order is made while adding
            addMediaItems(current + 1, items)
        } finally {
            placement.anchor = C.INDEX_UNSET
        }
    }

    /** Plays in the order of [positions], the playlist positions of a restored queue. */
    fun restoreShuffledOrder(positions: IntArray) {
        val order = player.shuffleOrder as? KreateShuffleOrder ?: return
        player.shuffleOrder = order.restored(positions)
    }

    /** Hands the songs added with [playNext] over to the shuffle. */
    private fun scramblePinned() {
        val order = player.shuffleOrder as? KreateShuffleOrder ?: return
        val scrambled = order.scrambled(player.currentMediaItemIndex)
        if (scrambled !== order) player.shuffleOrder = scrambled
    }
}
