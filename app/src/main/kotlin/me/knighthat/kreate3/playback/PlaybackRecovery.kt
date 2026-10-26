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

import android.content.Context
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.knighthat.kreate3.R
import me.knighthat.kreate3.notification.AppChannel
import me.knighthat.kreate3.notification.Message
import me.knighthat.kreate3.notification.Notifier
import me.knighthat.kreate3.preferences.Preferences
import kotlin.time.Duration.Companion.seconds

/**
 * Songs skipped in a row, with none playing in between, before the player stops: when the server
 * or the network is down every song fails, skipping would run through the whole queue.
 */
private const val MAX_SKIPS_IN_A_ROW = 3

/**
 * Keeps the player going when a song fails, by what [Preferences.PLAYBACK_FAILURE_ACTION] says.
 * A song that loads longer than [Preferences.PLAYBACK_STALL_SECONDS] while it should be playing
 * fails too: a slow server otherwise leaves the player loading for minutes, Media3 retries a load
 * several times and starts over whenever a few bytes come. The user is told why each song that
 * isn't tried again failed, through [notifier], whether a screen is open or not.
 *
 * Use it on the main thread, [scope] has to run there. Call [release] with the player.
 */
class PlaybackRecovery(
    private val player: Player,
    private val notifier: Notifier,
    private val context: Context,
    private val scope: CoroutineScope,
) : Player.Listener {

    private var stallWatch: Job? = null

    /** The song tried again, it isn't tried a second time until another one plays. */
    private var retried: String? = null
    private var skipsInARow = 0

    init {
        player.addListener(this)
        watchStall()
    }

    override fun onPlayerError(error: PlaybackException) {
        val item = player.currentMediaItem
        Log.w(TAG, "Couldn't play ${item?.mediaId}", error)
        recover(error.userMessage(context, item))
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // Also when the same song starts over, repeating it
        retried = null
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) skipsInARow = 0
    }

    override fun onEvents(player: Player, events: Player.Events) {
        // Another song or another place in it loads from now on, the wait starts over
        if (events.containsAny(
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_POSITION_DISCONTINUITY,
            )
        ) {
            stallWatch?.cancel()
            stallWatch = null
        }
        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_POSITION_DISCONTINUITY,
            )
        ) watchStall()
    }

    fun release() {
        player.removeListener(this)
        stallWatch?.cancel()
        stallWatch = null
    }

    /** Starts waiting while the player loads instead of playing, stops once it doesn't. */
    private fun watchStall() {
        val loading = player.playbackState == Player.STATE_BUFFERING && player.playWhenReady
        if (!loading) {
            stallWatch?.cancel()
            stallWatch = null
            return
        }
        if (stallWatch != null) return

        val seconds = Preferences.PLAYBACK_STALL_SECONDS.value
        if (seconds <= 0) return
        stallWatch = scope.launch {
            delay(seconds.seconds)
            val item = player.currentMediaItem
            Log.w(TAG, "Gave up loading ${item?.mediaId} after $seconds s")
            // Stopping changes the state, this job must not be cancelled by it
            stallWatch = null
            // Drops the load, preparing again starts a new one where the song was
            player.stop()
            recover(cantPlayMessage(context, item, R.string.error_playback_too_slow))
        }
    }

    /** Tries the song again or plays the next one, with the player idle after a failure. */
    private fun recover(message: String) {
        val action = Preferences.PLAYBACK_FAILURE_ACTION.value
        val songId = player.currentMediaItem?.mediaId

        if (action.retries && songId != null && songId != retried) {
            retried = songId
            Log.i(TAG, "Trying $songId again")
            player.prepare()
            return
        }

        if (action.skips && skipsInARow < MAX_SKIPS_IN_A_ROW && player.hasNextMediaItem()) {
            skipsInARow++
            Log.i(TAG, "Skipping $songId")
            player.seekToNextMediaItem()
            player.prepare()
        }
        notifier.post(Message(message, dismissable = true, channel = AppChannel.PLAYBACK))
    }

    private companion object {

        const val TAG = "PlaybackRecovery"
    }
}
