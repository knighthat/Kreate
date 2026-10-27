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

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.knighthat.kreate3.playback.PlaybackLifecycle.Companion.WRITES_TIMEOUT
import kotlin.time.Duration.Companion.seconds

/**
 * Stops playback for a while and starts it again, like around a profile switch: everything the
 * player saves, its queue, history and played songs, has to go to the profile it was playing in,
 * and the next player has to start with what the new profile saved.
 *
 * Restarting the player alone, see [restartPlayer], keeps what it plays.
 *
 * Everything but [restartAround] and [restartPlayer] runs on the main thread, like the service.
 *
 * @param scope where [restartPlayer] runs, leaving the screen doesn't leave playback stopped
 */
class PlaybackLifecycle(
    private val controller: PlaybackControllerImpl,
    private val scope: CoroutineScope,
) {

    private val mutex = Mutex()
    private var service: PlaybackService? = null

    // What the player of the session was made with, null while there is none
    private val made = MutableStateFlow<PlayerBuild?>(null)

    // Taken by the next player, see restartPlayer
    private var carried: CarriedQueue? = null

    /**
     * Whether the player was made with other settings than there are now, see [PlayerBuild].
     * `false` while there is no player: the next one is made with them.
     */
    val restartNeeded: Flow<Boolean> =
        combine(made, PlayerBuild.changes) { made, now -> made != null && made != now }

    /** While `true`, [PlaybackService] makes no session, controllers can't connect. */
    var isStopped: Boolean = false
        private set

    internal fun attach(service: PlaybackService) {
        this.service = service
    }

    internal fun detach(service: PlaybackService) {
        if (this.service === service) this.service = null
    }

    internal fun playerMade(build: PlayerBuild) {
        made.value = build
    }

    internal fun playerReleased() {
        made.value = null
    }

    /** What the previous player played, once, see [restartPlayer]. */
    internal fun takeCarried(): CarriedQueue? = carried.also { carried = null }

    /**
     * Makes a new player with the settings there are now, see [PlayerBuild], which goes on with
     * what the previous one played. Without a player there is nothing to restart.
     */
    fun restartPlayer(): Job = scope.launch {
        restart(carry = true) {}
    }

    /**
     * Releases the player and waits for what it saves, runs [block], then starts playback again
     * with a new player, which restores its queue. Calls made meanwhile wait for this one.
     *
     * The writes get [WRITES_TIMEOUT] to finish, the ones still running then are cancelled, so
     * they don't end up in what [block] changes to.
     */
    suspend fun <T> restartAround(block: suspend () -> T): T = restart(carry = false, block)

    /** [restartAround], handing what the player plays to the next one when [carry]. */
    private suspend fun <T> restart(carry: Boolean, block: suspend () -> T): T = mutex.withLock {
        withContext(Dispatchers.Main.immediate) {
            isStopped = true
            try {
                controller.disconnect()
                carried = if (carry) service?.player?.let(CarriedQueue::of) else null
                service?.stop()?.let { writes ->
                    if (withTimeoutOrNull(WRITES_TIMEOUT) { writes.join() } == null) {
                        Log.w(TAG, "Playback writes took too long, cancelling them")
                        writes.cancelAndJoin()
                    }
                }
                block()
            } finally {
                isStopped = false
                controller.connect()
            }
        }
    }

    private companion object {

        const val TAG = "PlaybackLifecycle"

        val WRITES_TIMEOUT = 5.seconds
    }
}
