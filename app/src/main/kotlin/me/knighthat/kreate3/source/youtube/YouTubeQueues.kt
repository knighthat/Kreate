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
package me.knighthat.kreate3.source.youtube

import androidx.media3.common.MediaItem
import me.knighthat.kreate3.playback.QueueTail
import me.knighthat.kreate3.playback.withQueueTail
import me.knighthat.kreate3.provider.youtube.YouTubePage
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.provider.youtube.YouTubeQueueStart
import me.knighthat.kreate3.source.withoutDisliked
import me.knighthat.kreate3.ui.component.toLiveMediaItem

/** Parts asked for in a row when every song of one is left out, before giving up on the rest. */
private const val EMPTY_PARTS = 3

/**
 * The first part of what [shuffle], a playlist's "Shuffle play", plays: about 25 songs in an order
 * YouTube picks, the last one carrying where the next part is, see [queuePart].
 *
 * @throws me.knighthat.kreate3.provider.youtube.YouTubeException when YouTube can't be reached
 */
suspend fun YouTubeProvider.shuffledQueue(
    shuffle: YouTubeQueueStart,
    disliked: Set<String>,
): List<MediaItem> = parts(disliked, null, { QueueTail.of(shuffle, it) }) { token ->
    queue(shuffle, token).let { page ->
        YouTubePage(page.items.map { it.toMediaItem(this) }, page.continuation)
    }
}

/**
 * The part of the queue after [tail]: the songs that can be played and aren't [disliked], the last
 * one carrying where the part after it is, see [QueueTail]. Nothing is stored.
 *
 * @throws me.knighthat.kreate3.provider.youtube.YouTubeException when YouTube can't be reached
 */
suspend fun YouTubeProvider.queuePart(tail: QueueTail, disliked: Set<String>): List<MediaItem> =
    when (tail.source) {
        QueueTail.Source.NEXT -> parts(disliked, tail.token, { QueueTail.of(tail.start, it) }) {
            queue(tail.start, it).let { page ->
                YouTubePage(page.items.map { item -> item.toMediaItem(this) }, page.continuation)
            }
        }

        QueueTail.Source.PLAYLIST ->
            parts(disliked, tail.token, { QueueTail.ofPlaylist(tail.playlistId, it) }) { token ->
                // The page asks for the first part, a tail always has a token
                val page = playlistSongs(checkNotNull(token))
                YouTubePage(
                    page.items.map {
                        it.toLiveMediaItem(
                            artwork(it.song.thumbnail ?: it.album?.thumbnail, SONG_ARTWORK)
                        )
                    },
                    page.continuation,
                )
            }
    }

/**
 * The next part [fetch] gives after [token], without the songs that can't be played or are
 * [disliked]; the last one carries the tail [tailOf] makes of the token after it. A part left
 * empty is skipped, up to [EMPTY_PARTS] in a row.
 */
private suspend fun parts(
    disliked: Set<String>,
    token: String?,
    tailOf: (String) -> QueueTail,
    fetch: suspend (token: String?) -> YouTubePage<MediaItem>,
): List<MediaItem> {
    var continuation = token
    repeat(EMPTY_PARTS) {
        val page = fetch(continuation)
        val songs = page.items
            .filter { it.mediaMetadata.isPlayable == true }
            .withoutDisliked(disliked)
        continuation = page.continuation
        if (songs.isNotEmpty()) return songs.withQueueTail(continuation?.let(tailOf))
        if (continuation == null) return emptyList()
    }
    return emptyList()
}

/**
 * The songs of a playlist that can be played, the last one carrying [continuation] of its page
 * when it has more, so the rest comes as it plays, see [QueueTail].
 */
fun List<MediaItem>.continuedBy(playlistId: String, continuation: String?): List<MediaItem> =
    filter { it.mediaMetadata.isPlayable == true }
        .withQueueTail(continuation?.let { QueueTail.ofPlaylist(playlistId, it) })
