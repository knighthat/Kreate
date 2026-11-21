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

import androidx.media3.common.MediaItem
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.provider.youtube.YouTubeQueueStart

/**
 * How the queue goes on once the songs in it are played: what to ask the source for the next
 * part. The queue has at most one, see [PlaybackController.continuation]; the player asks for the
 * part when few songs are left and gets the continuation of the one after it ([QueuePart.next]).
 * It lives only as long as the queue it was made for: replacing or clearing the queue drops it,
 * and it is never saved, so a queue put back after the app closed ends where it ends.
 */
@Serializable
sealed interface QueueContinuation {

    /** The queue of YouTube Music [start] plays, like a playlist's "Shuffle play". */
    @Serializable
    @SerialName("youtube_next")
    data class YouTubeNext(
        val playlistId: String,
        val params: String? = null,
        val videoId: String? = null,
        val token: String,
    ) : QueueContinuation {

        /** The start [token] belongs to, it only works with it. */
        val start: YouTubeQueueStart
            get() = YouTubeQueueStart(playlistId, params, videoId)
    }

    /** The rest of the playlist [playlistId] of YouTube Music, from its own page, in its order. */
    @Serializable
    @SerialName("youtube_playlist")
    data class YouTubePlaylist(val playlistId: String, val token: String) : QueueContinuation

    /** The endless radio of the song [songId] of YouTube Music. */
    @Serializable
    @SerialName("youtube_radio")
    data class YouTubeRadio(val songId: String, val token: String) : QueueContinuation

    /**
     * Songs like [songId], asked again each time: the origins without a token (the device, an
     * OpenSubsonic server) answer the same question with songs that may differ. Songs already in
     * the queue are skipped, it ends once nothing new comes.
     */
    @Serializable
    @SerialName("similar")
    data class Similar(val songId: String, val origin: Origin) : QueueContinuation

    /** As [SetContinuationCommand] hands it over, see [decode]. */
    fun encode(): String = Json.encodeToString<QueueContinuation>(this)

    companion object {

        /** The continuation [encode] gave, `null` for none or one this version can't read. */
        fun decode(text: String?): QueueContinuation? =
            text?.let {
                try {
                    Json.decodeFromString<QueueContinuation>(it)
                } catch (_: SerializationException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
    }
}

/**
 * A part of the queue to add, with what comes after it.
 *
 * @property next `null` when this was the last part
 */
data class QueuePart(val items: List<MediaItem>, val next: QueueContinuation?)

/**
 * The [QueueContinuation] of the player's queue, shared by whoever starts a queue and the
 * [QueueExtender] that grows it. Safe from any thread.
 */
class QueueContinuationState {

    private val _current = MutableStateFlow<QueueContinuation?>(null)

    /** What the queue goes on with, `null` while it has nothing more to give. */
    val current: StateFlow<QueueContinuation?> = _current.asStateFlow()

    private val _loading = MutableStateFlow(false)

    /** Whether the next part is being fetched right now. */
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _requests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Each time somebody wants the next part now, however many songs are left. */
    val requests: SharedFlow<Unit> = _requests.asSharedFlow()

    /**
     * Songs the queue has once the ones sent with the continuation are in, `0` for no wait. The
     * player is told about songs added and the continuation in no fixed order, the
     * [QueueExtender] doesn't count the songs left before the queue is this long.
     */
    @Volatile
    var readyAt: Int = 0
        private set

    fun set(continuation: QueueContinuation?, readyAt: Int = 0) {
        this.readyAt = readyAt
        _current.value = continuation
    }

    /** Makes [next] the continuation only while it is still [expected], `false` when it isn't. */
    fun advance(expected: QueueContinuation, next: QueueContinuation?): Boolean =
        _current.compareAndSet(expected, next)

    fun clear() = set(null)

    /**
     * The player's queue is replaced by [size] songs, or emptied with `0`. What the queue went on
     * with is dropped, except for a continuation that was given for this very queue: the command
     * with it can reach the service before the songs do, see [readyAt].
     */
    fun replaced(size: Int) {
        if (size > 0 && size == readyAt) return
        clear()
    }

    fun setLoading(loading: Boolean) {
        _loading.value = loading
    }

    /** Asks for the next part now, nothing happens while the queue has no continuation. */
    fun requestMore() {
        _requests.tryEmit(Unit)
    }
}
