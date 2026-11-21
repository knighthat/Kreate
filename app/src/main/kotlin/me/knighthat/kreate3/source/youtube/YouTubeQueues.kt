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
import me.knighthat.kreate3.playback.QueueContinuation
import me.knighthat.kreate3.playback.QueuePart
import me.knighthat.kreate3.provider.youtube.YouTubePage
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.provider.youtube.YouTubeQueueStart
import me.knighthat.kreate3.source.withoutDisliked
import me.knighthat.kreate3.ui.component.toLiveMediaItem

/** Parts asked for in a row when every song of one is left out, before giving up on the rest. */
private const val EMPTY_PARTS = 3

/**
 * The first part of what [shuffle], a playlist's "Shuffle play", plays: about 25 songs in an order
 * YouTube picks, with where the next part is, see [queuePart].
 *
 * @throws me.knighthat.kreate3.provider.youtube.YouTubeException when YouTube can't be reached
 */
suspend fun YouTubeProvider.shuffledQueue(
    shuffle: YouTubeQueueStart,
    disliked: Set<String>,
): QueuePart = parts(
    disliked,
    null,
    { QueueContinuation.YouTubeNext(shuffle.playlistId, shuffle.params, shuffle.videoId, it) },
) { token ->
    queue(shuffle, token).let { page ->
        YouTubePage(page.items.map { it.toMediaItem(this) }, page.continuation)
    }
}

/**
 * The part of the queue after [continuation]: the songs that can be played and aren't [disliked],
 * with where the part after it is. Nothing is stored. [QueueContinuation.Similar] isn't
 * YouTube's to answer, see `Radio`.
 *
 * @throws me.knighthat.kreate3.provider.youtube.YouTubeException when YouTube can't be reached
 */
suspend fun YouTubeProvider.queuePart(
    continuation: QueueContinuation,
    disliked: Set<String>,
): QueuePart = when (continuation) {
    is QueueContinuation.YouTubeNext -> parts(
        disliked,
        continuation.token,
        { continuation.copy(token = it) },
    ) {
        queue(continuation.start, it).let { page ->
            YouTubePage(page.items.map { item -> item.toMediaItem(this) }, page.continuation)
        }
    }

    is QueueContinuation.YouTubePlaylist -> parts(
        disliked,
        continuation.token,
        { continuation.copy(token = it) },
    ) { token ->
        // The page asks for the first part, a continuation always has a token
        val page = playlistSongs(checkNotNull(token))
        YouTubePage(
            page.items.map {
                it.toLiveMediaItem(artwork(it.song.thumbnail ?: it.album?.thumbnail, SONG_ARTWORK))
            },
            page.continuation,
        )
    }

    is QueueContinuation.YouTubeRadio -> parts(
        disliked,
        continuation.token,
        { continuation.copy(token = it) },
    ) { token ->
        radio(continuation.songId, token).let { page ->
            YouTubePage(page.items.map { it.toMediaItem(this) }, page.continuation)
        }
    }

    is QueueContinuation.Similar -> QueuePart(emptyList(), null)
}

/**
 * The next part [fetch] gives after [token], without the songs that can't be played or are
 * [disliked], with the continuation [continuationOf] makes of the token after it. A part left
 * empty is skipped, up to [EMPTY_PARTS] in a row.
 */
private suspend fun parts(
    disliked: Set<String>,
    token: String?,
    continuationOf: (String) -> QueueContinuation,
    fetch: suspend (token: String?) -> YouTubePage<MediaItem>,
): QueuePart {
    var continuation = token
    repeat(EMPTY_PARTS) {
        val page = fetch(continuation)
        val songs = page.items
            .filter { it.mediaMetadata.isPlayable == true }
            .withoutDisliked(disliked)
        continuation = page.continuation
        if (songs.isNotEmpty()) return QueuePart(songs, continuation?.let(continuationOf))
        if (continuation == null) return QueuePart(emptyList(), null)
    }
    return QueuePart(emptyList(), null)
}

/**
 * The songs of a playlist that can be played, with the rest of the playlist's page after them
 * when [continuation] says it has more, so the rest comes as it plays.
 */
fun List<MediaItem>.continuedBy(playlistId: String, continuation: String?): QueuePart =
    QueuePart(
        filter { it.mediaMetadata.isPlayable == true },
        continuation?.let { QueueContinuation.YouTubePlaylist(playlistId, it) },
    )
