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
import kotlinx.coroutines.runBlocking
import me.knighthat.kreate3.constant.StreamQuality
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.provider.youtube.YouTubeProvider

/**
 * Picks the copy of a song the cache reads. The cache keeps a song in each quality it was
 * streamed in ([QueuedSong.cacheKey]), a lower one is only dropped once a better one is whole.
 *
 * When a song starts, the best whole copy in the quality it is set to or a better one is read.
 * Online, a part of one comes next, then a new copy in the quality it is set to, streamed while
 * the others stay. Offline, the best whole copy is read whatever its quality. The rest of the
 * song, like after a seek, is read from the same copy.
 *
 * @param copies the copies kept of a song, with whether each is whole, see [SongCaches.copies]
 * @param drop drops a copy of a song, see [SongCaches.drop]
 * @param quality the quality the songs of an origin are set to now
 * @param isOnline whether songs can be streamed now
 */
class CacheCopies internal constructor(
    private val copies: (QueuedSong) -> Map<StreamQuality, Boolean>,
    private val drop: (QueuedSong) -> Unit,
    private val quality: suspend (Origin) -> StreamQuality,
    private val isOnline: () -> Boolean,
) {

    constructor(caches: SongCaches, context: Context, youTube: YouTubeProvider) : this(
        caches::copies,
        caches::drop,
        { context.streamQuality(it, youTube) },
        context::isOnline,
    )

    /** The copy each song is read from since it started, by its address, the oldest first. */
    private val reading = object : LinkedHashMap<String, StreamQuality>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, StreamQuality>) =
            size > MAX_SONGS
    }

    /**
     * The quality of the copy to read [song] from, from [position]. A read past the start is of
     * the copy the song started with, or one picked like at the start when nothing was read yet,
     * like after a restart.
     */
    fun pick(song: QueuedSong, position: Long): StreamQuality {
        val key = song.uri.toString()
        if (position > 0) synchronized(reading) { reading[key] }?.let { return it }

        val current = runBlocking { quality(song.origin) }
        val copies = copies(song)
        val online = isOnline()
        val good = copies.filterKeys { it >= current }
        val picked = good.filterValues { it }.keys.maxOrNull()
            ?: good.keys.maxOrNull()?.takeIf { online }
            ?: current.takeIf { online }
            ?: copies.filterValues { it }.keys.maxOrNull()
            ?: copies.keys.maxOrNull()
            ?: current
        synchronized(reading) { reading[key] = picked }
        return picked
    }

    /**
     * A read of [song] through the cache ended, in its [QueuedSong.quality]. Once that copy is
     * whole, the copies of lower qualities are dropped.
     */
    fun readEnded(song: QueuedSong) {
        val quality = song.quality ?: return
        try {
            val copies = copies(song)
            if (copies[quality] != true) return
            copies.keys.filter { it < quality }.forEach { drop(song.copy(quality = it)) }
        } catch (e: Exception) {
            // Kept for now, dropped next time
            Log.w(TAG, "Couldn't drop the copies of ${song.id} below $quality", e)
        }
    }

    private companion object {
        const val TAG = "CacheCopies"

        /** Songs whose copy is remembered, about a long queue. */
        const val MAX_SONGS = 200
    }
}
