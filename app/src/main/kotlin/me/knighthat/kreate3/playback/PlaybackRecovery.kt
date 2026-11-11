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
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
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
 * A song whose loading gets no further for [Preferences.PLAYBACK_STALL_SECONDS] while it should be
 * playing fails too: a server that stopped answering otherwise leaves the player loading for
 * minutes, Media3 retries a load several times and starts over whenever a few bytes come. A slow
 * load that keeps getting further is waited for, "load before playing" can take longer than the
 * limit on a slow network. The user is told why each song that
 * isn't tried again failed, through [notifier], whether a screen is open or not.
 *
 * A song its source says is unavailable ([ERROR_CODE_UNAVAILABLE]) is asked about with [check]
 * when the setting tries songs again, instead of being tried again at once: one that plays after
 * all is tried again, one published in its place by YouTube takes its spot in the queue and
 * plays, and one gone for good fails like a song tried again would, skipped or stopped on as the
 * setting says.
 *
 * Use it on the main thread, [scope] has to run there. Call [release] with the player.
 */
class PlaybackRecovery(
    private val player: Player,
    private val notifier: Notifier,
    private val context: Context,
    private val scope: CoroutineScope,
    private val check: suspend (MediaItem) -> SongCheck,
) : Player.Listener {

    private var stallWatch: Job? = null

    /** [check] of the song that failed, cancelled when another song plays. */
    private var checking: Job? = null

    /** The song tried again, it isn't tried a second time until another one plays. */
    private var retried: String? = null
    private var skipsInARow = 0

    /** A skip of [recover] is on its way, the song it moves to wasn't picked by the user. */
    private var skipping = false

    init {
        player.addListener(this)
        watchStall()
    }

    override fun onPlayerError(error: PlaybackException) {
        val item = player.currentMediaItem
        Logger.w(error, TAG) { "Couldn't play ${item?.mediaId}" }
        recover(
            error.userMessage(context, item),
            unavailable = error.errorCode == ERROR_CODE_UNAVAILABLE,
        )
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        checking?.cancel()
        checking = null
        // Also when the same song starts over, repeating it
        retried = null
        // Another song or queue the user picked starts the count over, as a song that played does
        if (skipping) skipping = false else skipsInARow = 0
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
        checking?.cancel()
        checking = null
    }

    /**
     * Starts waiting while the player loads instead of playing, stops once it doesn't. The wait
     * starts over whenever more of the song is loaded.
     */
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
            var loaded = player.bufferedPosition
            var waited = 0
            while (waited < seconds) {
                delay(1.seconds)
                val now = player.bufferedPosition
                if (now > loaded) {
                    loaded = now
                    waited = 0
                } else {
                    waited++
                }
            }
            val item = player.currentMediaItem
            Logger.w(tag = TAG) { "Gave up loading ${item?.mediaId}, nothing loaded for $seconds s" }
            // Stopping changes the state, this job must not be cancelled by it
            stallWatch = null
            // Drops the load, preparing again starts a new one where the song was
            player.stop()
            recover(cantPlayMessage(context, item, R.string.error_playback_too_slow))
        }
    }

    /**
     * Tries the song again or plays the next one, with the player idle after a failure.
     *
     * @param unavailable whether its source says the song is unavailable, it is [check]ed
     * before it is tried again
     */
    private fun recover(message: String, unavailable: Boolean = false) {
        val action = Preferences.PLAYBACK_FAILURE_ACTION.value
        val item = player.currentMediaItem
        val songId = item?.mediaId

        if (action.retries && songId != null && songId != retried) {
            retried = songId
            if (unavailable) {
                lookUp(item, message)
            } else {
                Logger.i(tag = TAG) { "Trying $songId again" }
                player.prepare()
            }
            return
        }
        giveUp(message)
    }

    /**
     * Asks whether [item], which failed as unavailable, plays after all or was published again,
     * and acts on the answer. A check that fails, like without a network, tries the song again.
     */
    private fun lookUp(item: MediaItem, message: String) {
        val songId = item.mediaId
        Logger.i(tag = TAG) { "$songId is unavailable, checking whether it was published again" }
        checking?.cancel()
        checking = scope.launch {
            val found = try {
                check(item)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e, TAG) { "Couldn't check $songId, trying it again" }
                SongCheck.Playable
            }
            checking = null
            // Played again or moved on meanwhile, the player has its own song to play
            if (player.currentMediaItem?.mediaId != songId || player.playerError == null) {
                return@launch
            }

            when (found) {
                SongCheck.Playable -> {
                    Logger.i(tag = TAG) { "Trying $songId again" }
                    player.prepare()
                }

                is SongCheck.Replaced -> {
                    Logger.i(tag = TAG) { "$songId was published again as ${found.item.mediaId}" }
                    player.replaceMediaItem(player.currentMediaItemIndex, found.item)
                    player.prepare()
                }

                SongCheck.Gone -> {
                    Logger.i(tag = TAG) { "$songId is gone from YouTube" }
                    giveUp(message)
                }
            }
        }
    }

    /** Plays the next song or stays on this one, as the setting says, and tells why it failed. */
    private fun giveUp(message: String) {
        val action = Preferences.PLAYBACK_FAILURE_ACTION.value
        val songId = player.currentMediaItem?.mediaId

        if (action.skips && skipsInARow < MAX_SKIPS_IN_A_ROW && player.hasNextMediaItem()) {
            skipsInARow++
            Logger.i(tag = TAG) { "Skipping $songId" }
            skipping = true
            player.seekToNextMediaItem()
            player.prepare()
        }
        notifier.post(Message(message, dismissable = true, channel = AppChannel.PLAYBACK))
    }

    private companion object {

        const val TAG = "PlaybackRecovery"
    }
}
