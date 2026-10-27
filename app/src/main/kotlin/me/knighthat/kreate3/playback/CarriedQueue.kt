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
import androidx.media3.exoplayer.source.ShuffleOrder
import kotlin.random.Random

/**
 * What a player plays, handed over to the next one when the player restarts, see
 * [PlaybackLifecycle.restartPlayer]: the queue in its shuffled order, the song and where it is,
 * the repeat mode, and whether it was playing. The saved queue may be off or behind, so it isn't
 * read back from the database.
 */
internal class CarriedQueue private constructor(
    private val items: List<MediaItem>,
    private val index: Int,
    private val positionMs: Long,
    private val shuffledOrder: IntArray,
    private val shuffle: Boolean,
    private val repeatMode: Int,
    private val prepared: Boolean,
    private val playWhenReady: Boolean,
) {

    /** Queues it in [player], which has nothing queued yet. */
    @OptIn(UnstableApi::class)
    fun applyTo(player: ExoPlayer) {
        player.setMediaItems(items, index, positionMs)
        // Setting the items made a new order
        if (shuffledOrder.size == items.size)
            player.setShuffleOrder(
                ShuffleOrder.DefaultShuffleOrder(
                    shuffledOrder,
                    Random.nextLong()
                )
            )
        player.repeatMode = repeatMode
        player.shuffleModeEnabled = shuffle
        player.playWhenReady = playWhenReady
        if (prepared) player.prepare()
    }

    companion object {

        /** What [player] plays, `null` when nothing is queued. */
        fun of(player: Player): CarriedQueue? {
            if (player.mediaItemCount == 0) return null

            val timeline = player.currentTimeline
            val shuffledOrder = buildList {
                var index = timeline.getFirstWindowIndex(true)
                while (index != C.INDEX_UNSET) {
                    add(index)
                    index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
                }
            }
            return CarriedQueue(
                items = List(player.mediaItemCount, player::getMediaItemAt),
                index = player.currentMediaItemIndex,
                positionMs = player.currentPosition,
                shuffledOrder = shuffledOrder.toIntArray(),
                shuffle = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
                prepared = player.playbackState != Player.STATE_IDLE,
                playWhenReady = player.playWhenReady,
            )
        }
    }
}
