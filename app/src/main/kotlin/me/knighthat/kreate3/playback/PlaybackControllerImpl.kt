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
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlin.time.Duration

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

    private val _queue = MutableStateFlow<List<MediaItem>>(emptyList())
    override val queue: StateFlow<List<MediaItem>> = _queue.asStateFlow()

    private val _shuffledOrder = MutableStateFlow<List<Int>?>(null)
    override val shuffledOrder: StateFlow<List<Int>?> = _shuffledOrder.asStateFlow()

    private val _sleepTimer = MutableStateFlow<SleepTimer?>(null)
    override val sleepTimer: StateFlow<SleepTimer?> = _sleepTimer.asStateFlow()

    private var connection: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var radioSearch: Job? = null

    /** What the last [enqueue] or [playNext] added and where, for [unqueue]. */
    private var lastAdded: Added? = null

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

    override fun position(): Long = controller?.currentPosition?.coerceAtLeast(0) ?: 0

    override fun duration(): Long? =
        controller?.duration?.takeIf { it != C.TIME_UNSET && it > 0 }

    override fun next() = run("skip to next") { it.seekToNext() }

    override fun drop(mediaId: String) = run("drop $mediaId") { controller ->
        // Its other items first, so moving on never lands on one of them
        for (index in controller.mediaItemCount - 1 downTo 0) {
            if (index == controller.currentMediaItemIndex) continue
            if (controller.getMediaItemAt(index).mediaId == mediaId) controller.removeMediaItem(
                index
            )
        }
        if (controller.currentMediaItem?.mediaId != mediaId) return@run

        val index = controller.currentMediaItemIndex
        // With nothing after it, the player stops first: removing it would end the queue on the
        // item before it
        val next = controller.nextMediaItemIndex
        if (next == C.INDEX_UNSET || next == index) controller.stop()
        controller.removeAt(index)
    }

    override fun remove(index: Int) = run("remove item $index") { it.removeAt(index) }

    override fun clear() {
        // A radio still looking would queue its songs again
        radioSearch?.cancel()
        run("clear the queue") { controller ->
            val index = controller.currentMediaItemIndex
            val count = controller.mediaItemCount
            if (index !in 0 until count) return@run
            // The ones after it first, the current one keeps its index for the rest
            controller.removeMediaItems(index + 1, count)
            controller.removeMediaItems(0, index)
        }
    }

    override fun move(from: Int, to: Int) = run("move item $from to $to") { controller ->
        val count = controller.mediaItemCount
        if (from !in 0 until count || to !in 0 until count || from == to) return@run
        controller.moveMediaItem(from, to)
    }

    override fun skipTo(index: Int) = run("skip to item $index") { controller ->
        if (index !in 0 until controller.mediaItemCount) return@run
        controller.seekToDefaultPosition(index)
        if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
        controller.play()
    }

    override fun previous() = run("skip to previous") { it.seekToPrevious() }

    override fun seekTo(positionMs: Long) = run("seek to $positionMs") { it.seekTo(positionMs) }

    // The player keeps repeating one and shuffling apart, see KreatePlayer
    override fun toggleShuffle() = run("toggle shuffle") {
        it.shuffleModeEnabled = !it.shuffleModeEnabled
    }

    override fun cycleRepeat() = run("change the repeat mode") {
        it.repeatMode = it.repeatMode.toRepeatMode().next.toPlayerMode()
    }

    override fun stop() {
        // A radio still looking would queue its songs again
        radioSearch?.cancel()
        run("stop") { controller ->
            controller.stop()
            controller.clearMediaItems()
        }
    }

    override fun startSleepTimer(duration: Duration?) = run("start a sleep timer") {
        it.sendCustomCommand(SleepTimerCommands.START, SleepTimerCommands.startArgs(duration))
    }

    override fun changeSleepTimer(action: SleepTimerAction) = run("$action the sleep timer") {
        it.sendCustomCommand(SleepTimerCommands.of(action), Bundle.EMPTY)
    }

    @OptIn(UnstableApi::class)
    override fun audioSessionId(): Int? = controller?.sessionExtras
        ?.getInt(EXTRA_AUDIO_SESSION_ID, C.AUDIO_SESSION_ID_UNSET)
        ?.takeIf { it != C.AUDIO_SESSION_ID_UNSET }

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
        lastAdded = Added(items, controller.mediaItemCount)
        controller.addMediaItems(items)
        // Ready to play, the user starts it
        if (wasEmpty) controller.prepare()
    }

    override fun follow(seed: MediaItem, items: List<MediaItem>) =
        run("queue ${items.size} items after ${seed.mediaId}") { controller ->
            // The user started something else meanwhile
            if (controller.mediaItemCount == 0 ||
                controller.getMediaItemAt(0).mediaId != seed.mediaId
            ) return@run
            controller.addMediaItems(items)
        }

    override fun playNext(items: List<MediaItem>) =
        run("queue ${items.size} items next") { controller ->
            val wasEmpty = controller.mediaItemCount == 0
            val index = if (wasEmpty) 0 else controller.currentMediaItemIndex + 1
            lastAdded = Added(items, index)
            controller.addMediaItems(index, items)
            // Ready to play, the user starts it
            if (wasEmpty) controller.prepare()
        }

    override fun unqueue(items: List<MediaItem>) =
        run("take back ${items.size} queued items") { controller ->
            val ids = items.map(MediaItem::mediaId)
            val start = lastAdded?.takeIf { it.items === items }?.index
            lastAdded = null
            val count = controller.mediaItemCount
            val where = if (
                start != null && start + ids.size <= count &&
                ids.indices.all { controller.getMediaItemAt(start + it).mediaId == ids[it] }
            ) (start until start + ids.size).toSet()
            else {
                // Moved or removed meanwhile, the copies added were the last ones
                val found = HashSet<Int>()
                for (id in ids) (count - 1 downTo 0)
                    .firstOrNull { it !in found && controller.getMediaItemAt(it).mediaId == id }
                    ?.let(found::add)
                found
            }
            // From the end, the indexes before stay right
            where.sortedDescending().forEach { controller.removeAt(it) }
        }

    override fun insert(index: Int, items: List<MediaItem>) =
        run("put back ${items.size} items at $index") { controller ->
            if (items.isEmpty()) return@run
            val wasEmpty = controller.mediaItemCount == 0
            controller.addMediaItems(index.coerceIn(0, controller.mediaItemCount), items)
            if (wasEmpty) controller.prepare()
        }

    override fun restore(items: List<MediaItem>, index: Int, positionMs: Long, play: Boolean) =
        run("restore ${items.size} items") { controller ->
            if (items.isEmpty()) return@run
            controller.setMediaItems(items, index.coerceIn(0, items.lastIndex), positionMs)
            controller.prepare()
            if (play) controller.play()
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
     * Lets go of the service, so it can end. Nothing plays until the next call, which connects
     * again, or [connect].
     */
    internal fun disconnect() {
        radioSearch?.cancel()
        connection?.let(MediaController::releaseFuture)
        connection = null
        controller = null
        _state.value = PlaybackState()
        _queue.value = emptyList()
        _shuffledOrder.value = null
        _sleepTimer.value = null
    }

    /**
     * The connection that is running or being made, a new one when there is none or the service
     * went away since, for example after the user swiped the app away.
     */
    internal fun connect(): ListenableFuture<MediaController> {
        connection?.takeIf { !it.isDone || it.isUsable() }?.let { return it }

        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(disconnected: MediaController) {
                    if (controller !== disconnected) return
                    controller = null
                    _state.value = PlaybackState()
                    _queue.value = emptyList()
                    _shuffledOrder.value = null
                    _sleepTimer.value = null
                }

                // The service keeps the sleep timer, see SleepTimerRunner
                override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
                    if (controller === this@PlaybackControllerImpl.controller)
                        _sleepTimer.value = extras.sleepTimer()
                }
            })
            .buildAsync()
        connection = future

        scope.launch {
            try {
                val connected = future.await()
                // Let go of meanwhile, see disconnect
                if (connection !== future) return@launch
                controller = connected
                connected.addListener(object : Player.Listener {
                    override fun onEvents(player: Player, events: Player.Events) {
                        if (events.contains(Player.EVENT_TIMELINE_CHANGED)) publishQueue(connected)
                        else if (events.contains(Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED))
                            publishShuffledOrder(connected)
                        publish(
                            connected,
                            jumped = events.contains(Player.EVENT_POSITION_DISCONTINUITY),
                        )
                    }
                })
                publishQueue(connected)
                publish(connected)
                _sleepTimer.value = connected.sessionExtras.sleepTimer()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Couldn't connect", e)
            }
        }
        return future
    }

    private fun publish(controller: MediaController, jumped: Boolean = false) {
        _state.value = PlaybackState(
            current = controller.currentMediaItem,
            index = if (controller.mediaItemCount > 0) controller.currentMediaItemIndex else -1,
            isPlaying = controller.isPlaying,
            isEnded = controller.playbackState == Player.STATE_ENDED,
            repeatMode = controller.repeatMode.toRepeatMode(),
            isShuffled = controller.shuffleModeEnabled,
            jumps = _state.value.jumps + if (jumped) 1 else 0,
        )
    }

    private fun publishQueue(controller: MediaController) {
        _queue.value = List(controller.mediaItemCount, controller::getMediaItemAt)
        publishShuffledOrder(controller)
    }

    private fun publishShuffledOrder(controller: MediaController) {
        _shuffledOrder.value =
            if (controller.shuffleModeEnabled) controller.currentTimeline.shuffledOrder() else null
    }

    /** Takes the item at [index] out of the queue, moving on first when it is the current one. */
    private fun MediaController.removeAt(index: Int) {
        if (index !in 0 until mediaItemCount) return
        if (index == currentMediaItemIndex) {
            // Counts repeating one as not repeating, and wraps around when repeating all
            val next = nextMediaItemIndex
            // Moving on first, removing the current item while repeating one would end the queue
            // even with items after it: the player looks for what follows it by repeating it
            if (next != C.INDEX_UNSET && next != index) seekToNextMediaItem()
        }
        // With nothing else to play, removing it ends the queue
        removeMediaItem(index)
    }

    /** [items] as given to [enqueue] or [playNext], added at [index] of the queue. */
    private class Added(val items: List<MediaItem>, val index: Int)

    private fun ListenableFuture<MediaController>.isUsable(): Boolean =
        !isCancelled && runCatching { get().isConnected }.getOrDefault(false)

    private companion object {

        const val TAG = "PlaybackController"
    }
}

private fun @Player.RepeatMode Int.toRepeatMode(): RepeatMode = when (this) {
    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
    else -> RepeatMode.OFF
}

private fun RepeatMode.toPlayerMode(): @Player.RepeatMode Int = when (this) {
    RepeatMode.OFF -> Player.REPEAT_MODE_OFF
    RepeatMode.ONE -> Player.REPEAT_MODE_ONE
    RepeatMode.ALL -> Player.REPEAT_MODE_ALL
}

/**
 * Positions of the timeline's items in the order they play while shuffled, from the first. A
 * controller's timeline carries the player's shuffled order.
 */
internal fun Timeline.shuffledOrder(): List<Int> {
    if (isEmpty) return emptyList()
    val order = ArrayList<Int>(windowCount)
    var index = getFirstWindowIndex(true)
    while (index != C.INDEX_UNSET) {
        order += index
        // Without repeating, or the walk would go around for ever
        index = getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
    }
    return order
}
