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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.source.withoutDisliked
import me.knighthat.kreate3.source.youtube.toMediaItem
import me.knighthat.kreate3.ui.component.origin
import me.knighthat.kreate3.ui.component.toLiveMediaItem
import me.knighthat.kreate3.ui.component.toMediaItem

/** Songs a radio looks for, about two hours of music. */
internal const val RADIO_SIZE = 30

/**
 * Finds songs like a song, each origin its own way: songs of the device are scored from what the
 * user keeps ([UserSongRepository.similar]), the other origins are asked from their source.
 * Songs the user disliked are left out. Nothing is stored.
 */
class Radio(
    private val songs: UserSongRepository,
    private val openSubsonic: OpenSubsonicProvider,
    private val youTube: YouTubeProvider,
) {

    /**
     * Up to [limit] songs like [seed], most alike first. The seed itself is never in the list and
     * a song is there once.
     *
     * @return empty when the origin of [seed] is unknown, off, or has nothing like it
     * @throws me.knighthat.kreate3.provider.opensubsonic.ServerException when the server can't be used
     * @throws me.knighthat.kreate3.provider.youtube.YouTubeException when YouTube can't be reached
     */
    suspend fun after(seed: MediaItem, limit: Int): List<MediaItem> = start(seed, limit).items

    /**
     * Like [after], with how the radio goes on: YouTube's own token, or [QueueContinuation.Similar]
     * for the origins that answer again with other songs. `null` when the origin of [seed] is
     * unknown.
     *
     * @throws me.knighthat.kreate3.provider.opensubsonic.ServerException when the server can't be used
     * @throws me.knighthat.kreate3.provider.youtube.YouTubeException when YouTube can't be reached
     */
    suspend fun start(seed: MediaItem, limit: Int): QueuePart =
        // Called from the controller, on the main thread
        withContext(Dispatchers.Default) { similar(seed.mediaId, seed.origin, limit) }

    /**
     * The next songs of [continuation], for the one that asks the radio again. Dislikes and the
     * seed are left out like in [start].
     *
     * @throws me.knighthat.kreate3.provider.opensubsonic.ServerException when the server can't be used
     * @throws me.knighthat.kreate3.provider.youtube.YouTubeException when YouTube can't be reached
     */
    suspend fun more(continuation: QueueContinuation.Similar, limit: Int): QueuePart =
        withContext(Dispatchers.Default) {
            similar(continuation.songId, continuation.origin, limit)
        }

    private suspend fun similar(id: String, origin: Origin?, limit: Int): QueuePart {
        var next: QueueContinuation? = origin?.let { QueueContinuation.Similar(id, it) }
        val found = when (origin) {
            Origin.LOCAL -> songs.similar(id, limit).mapNotNull { it.toMediaItem() }

            Origin.OPEN_SUBSONIC ->
                openSubsonic.similarSongs(id, limit)?.map { it.toLiveMediaItem() }.orEmpty()

            // The radio starts with the seed, and it is endless: it goes on from its token
            Origin.YOUTUBE -> youTube.radio(id).let { page ->
                next = page.continuation?.let { QueueContinuation.YouTubeRadio(id, it) }
                page.items.map { it.toMediaItem(youTube) }
            }

            null -> emptyList()
        }
        val items = found.filter { it.mediaId != id }
            .withoutDisliked(songs.listDislikedIds())
            .distinctBy { it.mediaId }
            // YouTube's token is past everything the page gave, none of it is left out
            .let { if (next is QueueContinuation.YouTubeRadio) it else it.take(limit) }
        return QueuePart(items, next)
    }
}
