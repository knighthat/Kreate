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
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import me.knighthat.kreate3.AppRestart
import me.knighthat.kreate3.constant.StreamQuality
import me.knighthat.kreate3.preferences.Preferences
import java.io.File

/**
 * Where the songs of a server or YouTube are kept on the device, shared by every profile. Songs
 * on the device are never kept, they are read where they are. A song is stored under
 * [QueuedSong.cacheKey] in both.
 *
 * Their index is the database `exoplayer_internal.db`, left out of backups like the files.
 *
 * @param cacheSize bytes [cache] keeps at most, see [Preferences.MEDIA3_CACHE_SIZE]
 */
@OptIn(UnstableApi::class)
class SongCaches internal constructor(context: Context, cacheSize: Long) {

    constructor(context: Context) : this(
        context,
        AppRestart.readOnce(Preferences.MEDIA3_CACHE_SIZE),
    )

    /** The database of both, the downloader keeps its list of downloads in it too. */
    internal val database = StandaloneDatabaseProvider(context)

    private val _changed = MutableSharedFlow<QueuedSong>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * Songs whose copies in [cache] may have changed, a copy became whole or was dropped. Each is
     * without a quality, see [QueuedSong.song].
     */
    val changed: SharedFlow<QueuedSong> = _changed.asSharedFlow()

    /**
     * What was streamed, the songs read least recently are dropped to stay under its size. It is
     * in the cache folder, Android may empty it when the device runs out of space.
     */
    val cache: Cache = SimpleCache(
        File(context.cacheDir, "exoplayer"),
        LeastRecentlyUsedCacheEvictor(cacheSize),
        database,
    )

    /**
     * What the user downloaded, kept until it is removed: nothing is dropped to make room, the
     * downloader refuses new songs once it holds [Preferences.MEDIA3_DOWNLOAD_SIZE]. Playback only
     * reads it.
     */
    val downloads: Cache = SimpleCache(
        File(context.noBackupFilesDir, "exoplayer"),
        NoOpCacheEvictor(),
        database,
    )

    /** Size in bytes of the file [cache] keeps parts of for [song], `null` when unknown. */
    fun cachedLength(song: QueuedSong): Long? =
        ContentMetadata.getContentLength(cache.getContentMetadata(song.cacheKey))
            .takeIf { it >= 0 && cache.getCachedSpans(song.cacheKey).isNotEmpty() }

    /**
     * The copies [cache] keeps of [song], one for each quality it was read in through the cache
     * ([QueuedSong.cacheKey]), with whether the whole song is in it.
     */
    fun copies(song: QueuedSong): Map<StreamQuality, Boolean> =
        StreamQuality.entries.mapNotNull { quality ->
            val key = song.copy(quality = quality).cacheKey
            if (cache.getCachedSpans(key).isEmpty()) return@mapNotNull null
            val length = ContentMetadata.getContentLength(cache.getContentMetadata(key))
            quality to cache.isWhole(key)
        }.toMap()

    /** The best quality [cache] keeps the whole of [song] in, `null` when it has no whole copy. */
    fun cached(song: QueuedSong): StreamQuality? =
        copies(song).filterValues { it }.keys.maxOrNull()

    /**
     * Every song [cache] keeps a whole copy of, without a quality ([QueuedSong.song]), with the
     * best quality it is kept in. It goes through the whole cache, keep it off the main thread.
     */
    fun cachedSongs(): Map<QueuedSong, StreamQuality> = buildMap {
        cache.keys.forEach { key ->
            val copy = QueuedSong.ofCacheKey(key) ?: return@forEach
            val quality = copy.quality ?: return@forEach
            if (!cache.isWhole(key)) return@forEach
            val song = copy.song
            if ((get(song) ?: quality) <= quality) put(song, quality)
        }
    }

    /**
     * The best quality [downloads] keeps the whole of [song] in, the one playback reads it in,
     * `null` when it isn't downloaded or not completely yet.
     */
    fun downloaded(song: QueuedSong): StreamQuality? =
        StreamQuality.entries.reversed()
            .firstOrNull { downloads.isWhole(song.copy(quality = it).cacheKey) }

    /**
     * Drops the copies [downloads] keeps of [song] in every quality but [kept], like a lower one
     * once a better one is whole. Only the downloader calls it, playback never writes [downloads].
     */
    fun dropDownloads(song: QueuedSong, kept: StreamQuality? = null) =
        StreamQuality.entries.filter { it != kept }
            .forEach { downloads.removeResource(song.copy(quality = it).cacheKey) }

    /** Drops what [cache] keeps of [song], of its [QueuedSong.quality]. */
    fun drop(song: QueuedSong) {
        cache.removeResource(song.cacheKey)
        changed(song)
    }

    /** Drops every copy [cache] keeps of [song], in any quality. */
    fun dropAll(song: QueuedSong) {
        StreamQuality.entries.forEach { cache.removeResource(song.copy(quality = it).cacheKey) }
        changed(song)
    }

    /** Tells [changed] that the copies of [song] in [cache] may have changed. */
    fun changed(song: QueuedSong) {
        _changed.tryEmit(song.song)
    }

    private fun Cache.isWhole(key: String): Boolean {
        val length = ContentMetadata.getContentLength(getContentMetadata(key))
        return length >= 0 && isCached(key, 0, length)
    }
}
