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
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * The app's player. Repeating one song and shuffling exclude each other, whoever sets them, the
 * app or the system's media controls: repeating one turns shuffling off, and shuffling turns
 * repeating one off. Repeating all and shuffling go together.
 */
@OptIn(UnstableApi::class)
class KreatePlayer(private val player: ExoPlayer) : ExoPlayer by player {

    override fun getAudioSessionId(): Int = player.audioSessionId

    override fun setRepeatMode(repeatMode: Int) {
        if (repeatMode == Player.REPEAT_MODE_ONE) player.shuffleModeEnabled = false
        player.repeatMode = repeatMode
    }

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        if (shuffleModeEnabled && player.repeatMode == Player.REPEAT_MODE_ONE)
            player.repeatMode = Player.REPEAT_MODE_OFF
        player.shuffleModeEnabled = shuffleModeEnabled
    }
}