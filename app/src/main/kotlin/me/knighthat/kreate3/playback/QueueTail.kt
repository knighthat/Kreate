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

import android.os.Bundle
import androidx.media3.common.MediaItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import me.knighthat.kreate3.provider.youtube.YouTubeQueueStart

/** Where the [QueueTail] of an item is kept in its metadata extras. */
private const val EXTRA_QUEUE_TAIL = "queue_tail"

/**
 * What a queue goes on with after the song that carries it: the next part of a playlist of
 * YouTube Music, as [source] gives it. The last song of a part carries it, [QueueExtender] fetches
 * the next part as playback gets close and moves it on to the new last song. It is saved with the
 * queue, see [PersistentQueue].
 *
 * @property token YouTube's for the next part, it only works with the same [source] and start
 */
@Serializable
data class QueueTail(
    val playlistId: String,
    val params: String? = null,
    val videoId: String? = null,
    val token: String,
    val source: Source = Source.NEXT,
) {

    /** Where the parts come from. */
    enum class Source {
        /** `next` of the start, about 25 songs a part, like a playlist's "Shuffle play". */
        NEXT,

        /** The playlist's own page, about 100 songs a part, in its order. */
        PLAYLIST,
    }

    /** The start [token] belongs to, for [Source.NEXT]. */
    val start: YouTubeQueueStart
        get() = YouTubeQueueStart(playlistId, params, videoId)

    /** As the saved queue keeps it, see [decode]. */
    fun encode(): String = Json.encodeToString(this)

    companion object {

        /** The part of what [start] plays after [token]. */
        fun of(start: YouTubeQueueStart, token: String) =
            QueueTail(start.playlistId, start.params, start.videoId, token)

        /** The part of the playlist [playlistId] after [token], from its page. */
        fun ofPlaylist(playlistId: String, token: String) =
            QueueTail(playlistId = playlistId, token = token, source = Source.PLAYLIST)

        /** The tail [encode] gave, `null` for none or one this version can't read. */
        fun decode(text: String?): QueueTail? =
            text?.let {
                try {
                    Json.decodeFromString<QueueTail>(it)
                } catch (_: SerializationException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
    }
}

/** What the queue goes on with after this song, `null` for most songs. */
val MediaItem.queueTail: QueueTail?
    get() = QueueTail.decode(queueTailText)

/** [queueTail] as [QueueTail.encode] gave it, without reading it. */
internal val MediaItem.queueTailText: String?
    get() = mediaMetadata.extras?.getString(EXTRA_QUEUE_TAIL)

/** The song carrying [tail], or none when it is `null`; the rest of its extras stay. */
fun MediaItem.withQueueTail(tail: QueueTail?): MediaItem {
    val extras = Bundle(mediaMetadata.extras ?: Bundle())
    if (tail == null)
        extras.remove(EXTRA_QUEUE_TAIL)
    else
        extras.putString(EXTRA_QUEUE_TAIL, tail.encode())
    return buildUpon()
        .setMediaMetadata(mediaMetadata.buildUpon().setExtras(extras).build())
        .build()
}

/** The songs with [tail] on the last one, nothing to carry it when there are none. */
fun List<MediaItem>.withQueueTail(tail: QueueTail?): List<MediaItem> =
    if (tail == null || isEmpty()) this else dropLast(1) + last().withQueueTail(tail)
