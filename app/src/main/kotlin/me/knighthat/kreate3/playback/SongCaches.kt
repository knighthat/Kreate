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
import androidx.media3.datasource.cache.SimpleCache
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
 * @param downloadsSize bytes [downloads] keeps at most, see [Preferences.MEDIA3_DOWNLOAD_SIZE]
 */
@OptIn(UnstableApi::class)
class SongCaches internal constructor(context: Context, cacheSize: Long, downloadsSize: Long) {

    constructor(context: Context) : this(
        context,
        AppRestart.readOnce(Preferences.MEDIA3_CACHE_SIZE),
        AppRestart.readOnce(Preferences.MEDIA3_DOWNLOAD_SIZE),
    )

    private val database = StandaloneDatabaseProvider(context)

    /**
     * What was streamed, the songs read least recently are dropped to stay under its size. It is
     * in the cache folder, Android may empty it when the device runs out of space.
     */
    val cache: Cache = SimpleCache(
        File(context.cacheDir, "exoplayer"),
        LeastRecentlyUsedCacheEvictor(cacheSize),
        database,
    )

    /** What the user downloaded, kept until it is removed. Playback only reads it. */
    val downloads: Cache = SimpleCache(
        File(context.noBackupFilesDir, "exoplayer"),
        LeastRecentlyUsedCacheEvictor(downloadsSize),
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
            quality to (length >= 0 && cache.isCached(key, 0, length))
        }.toMap()

    /** Drops what [cache] keeps of [song], of its [QueuedSong.quality]. */
    fun drop(song: QueuedSong) = cache.removeResource(song.cacheKey)
}
