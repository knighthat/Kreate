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

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

/**
 * [PlaybackController] that talks to [PlaybackService] with a [MediaController]. Connecting also
 * starts the service, so nothing else has to.
 *
 * A controller must be used on the thread that made it, so all work runs on [scope], which has
 * to run on the main thread. Calls return before the work is done, and a failure is logged.
 */
class PlaybackControllerImpl(
    private val context: Context,
    private val scope: CoroutineScope,
    private val radio: Radio,
) : PlaybackController {

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _errors = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val errors: Flow<String> = _errors.asSharedFlow()

    private var connection: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var radioSearch: Job? = null

    init {
        // Connects early so the state is known before anything is played
        connect()
    }

    override fun progress(): Float {
        val controller = controller ?: return 0f
        val duration = controller.duration
        if (duration == C.TIME_UNSET || duration <= 0) return 0f
        return (controller.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
    }

    override fun togglePlay() = run("toggle play") { controller ->
        if (controller.isPlaying) return@run controller.pause()
        // A failure leaves the player idle, playing needs it prepared again
        if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
        controller.play()
    }

    override fun next() = run("skip to next") { it.seekToNext() }

    override fun play(item: MediaItem) = run("play ${item.mediaId}") { controller ->
        val queued = (0 until controller.mediaItemCount)
            .firstOrNull { controller.getMediaItemAt(it).mediaId == item.mediaId }
        val index = queued ?: controller.mediaItemCount.also { controller.addMediaItem(item) }

        controller.seekToDefaultPosition(index)
        controller.prepare()
        controller.play()
    }

    override fun playAll(items: List<MediaItem>) = run("play ${items.size} items") { controller ->
        controller.setMediaItems(items)
        controller.prepare()
        controller.play()
    }

    override fun radio(seed: MediaItem, append: Boolean) {
        radioSearch?.cancel()
        if (!append) playAll(listOf(seed))
        radioSearch = scope.launch {
            val similar = try {
                radio.after(seed, RADIO_SIZE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't find songs like ${seed.mediaId}", e)
                return@launch
            }
            run("queue ${similar.size} songs like ${seed.mediaId}") { controller ->
                if (append) {
                    val queued = (0 until controller.mediaItemCount)
                        .mapTo(HashSet()) { controller.getMediaItemAt(it).mediaId }
                    val added = similar.filter { it.mediaId !in queued }
                    val wasEmpty = controller.mediaItemCount == 0
                    controller.addMediaItems(added)
                    // Ready to play, the user starts it
                    if (wasEmpty && added.isNotEmpty()) controller.prepare()
                    return@run
                }
                // The user started something else meanwhile
                if (controller.mediaItemCount == 0 ||
                    controller.getMediaItemAt(0).mediaId != seed.mediaId
                ) return@run
                controller.addMediaItems(similar)
            }
        }
    }

    override fun enqueue(items: List<MediaItem>) = run("queue ${items.size} items") { controller ->
        val wasEmpty = controller.mediaItemCount == 0
        controller.addMediaItems(items)
        // Ready to play, the user starts it
        if (wasEmpty) controller.prepare()
    }

    /**
     * Runs [action] with the connected controller. It waits for the connection, the actions of
     * calls made meanwhile run in the order of the calls.
     */
    private fun run(what: String, action: (MediaController) -> Unit) {
        scope.launch {
            try {
                action(connect().await())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't $what", e)
            }
        }
    }

    /**
     * The connection that is running or being made, a new one when there is none or the service
     * went away since, for example after the user swiped the app away.
     */
    private fun connect(): ListenableFuture<MediaController> {
        connection?.takeIf { !it.isDone || it.isUsable() }?.let { return it }

        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(disconnected: MediaController) {
                    if (controller !== disconnected) return
                    controller = null
                    _state.value = PlaybackState()
                }
            })
            .buildAsync()
        connection = future

        scope.launch {
            try {
                val connected = future.await()
                controller = connected
                connected.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) =
                        publish(connected)

                    override fun onPlayerError(error: PlaybackException) {
                        Log.w(TAG, "Couldn't play ${connected.currentMediaItem?.mediaId}", error)
                        _errors.tryEmit(error.userMessage(connected.currentMediaItem))
                    }
                })
                publish(connected)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't connect", e)
            }
        }
        return future
    }

    private fun publish(controller: MediaController) {
        _state.value = PlaybackState(
            current = controller.currentMediaItem,
            isPlaying = controller.isPlaying,
            isEnded = controller.playbackState == Player.STATE_ENDED,
        )
    }

    private fun ListenableFuture<MediaController>.isUsable(): Boolean =
        !isCancelled && runCatching { get().isConnected }.getOrDefault(false)

    private companion object {

        const val TAG = "PlaybackController"

        /** Songs a radio looks for, about two hours of music. */
        const val RADIO_SIZE = 30
    }
}
