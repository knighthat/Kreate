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

package me.knighthat.kreate3.preferences

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Settings of the whole app, shared by every profile: what they control is shared too, like the
 * caches and the downloads. Only the default profile changes them, the settings screens show them
 * read-only to the others; nothing here stops a write.
 *
 * They are in `global.preferences_pb` with the profile list, which [ProfileManager] serves.
 * Loaded and saved with [Preferences.awaitLoaded] and [Preferences.flush]. Its failures come
 * through [Preferences.failures] as [PreferenceFailure.Store.GLOBAL].
 *
 * Keys are stored on disk, never rename one without a step in [GLOBAL_STEPS]; the names
 * [ProfileManager] uses are taken.
 */
object GlobalPreferences : KoinComponent {

    private val engine: PreferenceEngine by inject(GLOBAL_ENGINE)

    //<editor-fold defaultstate="collapsed" desc="Playback">
    /**
     * Largest size, in bytes, the cache of streamed songs may grow to. The default is 1 GiB.
     * `0` turns the cache off: songs are streamed every time and what it kept is deleted when the
     * app starts. [Long.MAX_VALUE] has no limit. Downloads don't count, see
     * [MEDIA3_DOWNLOAD_SIZE].
     *
     * The size is fixed when the player starts, so a new value applies from the next launch.
     * A smaller value drops the songs read least recently once the next song is cached.
     */
    val MEDIA3_CACHE_SIZE by lazy { long("song_cache", 1024L * 1024 * 1024) }

    /**
     * Largest size, in bytes, the downloaded songs of every profile may take together. The default
     * is 4 GiB. It is separate from [MEDIA3_CACHE_SIZE], downloads are never dropped to make room:
     * once they take this much, new downloads are refused. `0` refuses every new download,
     * [Long.MAX_VALUE] has no limit. Read when a download starts.
     */
    val MEDIA3_DOWNLOAD_SIZE by lazy { long("song_downloads", 4L * 1024 * 1024 * 1024) }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Coil3">
    /**
     * Largest size, in bytes, the image disk cache may grow to. The default is 128 MiB.
     * `0` turns it off (its files are deleted when the app starts), [Long.MAX_VALUE] has no limit.
     *
     * The size is fixed when the image loader is built, so a new value applies from the next
     * launch. A smaller value does not shrink the files already stored, the cache trims
     * itself as new images are written.
     */
    val IMAGE_DISK_CACHE by lazy { long("image_disk_cache", 128L * 1024 * 1024) }

    /**
     * Share of the app's memory class that decoded images in the memory cache may use,
     * from `0.0` to `1.0`. The default is `0.15` (15%).
     *
     * Applies from the next launch, like [IMAGE_DISK_CACHE].
     */
    val IMAGE_MEMORY_CACHE_PERCENT by lazy { double("image_memory_cache_percent", .15) }
    //</editor-fold>

    internal val isLoaded: Boolean get() = engine.isLoaded

    internal suspend fun awaitLoaded() = engine.awaitLoaded()

    internal suspend fun flush() = engine.flush()

    private fun long(name: String, default: Long) =
        Preference(name, default, codec<Long, Long>({ it }, { it }), engine)

    private fun double(name: String, default: Double) =
        Preference(name, default, codec<Double, Double>({ it }, { it }), engine)
}
