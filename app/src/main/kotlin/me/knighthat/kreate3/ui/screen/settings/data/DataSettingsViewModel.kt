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

package me.knighthat.kreate3.ui.screen.settings.data

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import coil3.SingletonImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.knighthat.kreate3.KreateApplication
import me.knighthat.kreate3.database.repository.SongRepository
import me.knighthat.kreate3.download.SongDownloads
import me.knighthat.kreate3.imageCacheDir
import me.knighthat.kreate3.playback.SongCaches
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.source.CoverFiles
import me.knighthat.kreate3.ui.component.StorageUse
import me.knighthat.kreate3.ui.component.storageUse
import me.knighthat.kreate3.ui.screen.account.WHILE_SHOWN
import java.io.File

/**
 * The room the image disk cache, the song cache and the song downloads take, looked at again
 * every few seconds while the page shows, and wiping them. The caches are shared by every user,
 * the downloads are told apart by user. [deviceStorage] puts them with the downloaded covers and
 * the lyrics against the device's storage.
 *
 * @param scope the app's global scope, a wipe goes on when the page closes
 */
@OptIn(UnstableApi::class)
class DataSettingsViewModel(
    private val context: Context,
    private val caches: SongCaches,
    private val downloads: SongDownloads,
    private val covers: CoverFiles,
    songs: SongRepository,
    private val scope: CoroutineScope,
) : ViewModel() {

    private val loader = SingletonImageLoader.get(context)

    /** Counts the wipes, each one looks at the room again at once. */
    private val wiped = MutableStateFlow(0)

    /** Comes again every [INTERVAL] while the page shows, and after a wipe. */
    @kotlin.OptIn(ExperimentalCoroutinesApi::class)
    private val ticks: Flow<Unit> = wiped.transformLatest {
        while (true) {
            emit(Unit)
            delay(INTERVAL)
        }
    }

    /** The overview of the downloads, shared by [songDownloads] and [deviceStorage]. */
    private val overview = downloads.overview().shareIn(viewModelScope, WHILE_SHOWN, 1)

    /** The image disk cache out of [Preferences.IMAGE_DISK_CACHE], `null` until known. */
    val imageCache: StateFlow<StorageUse?> =
        combine(ticks, Preferences.IMAGE_DISK_CACHE.state) { _, limit ->
            val used = loader.diskCache?.size ?: imageCacheFiles()
            context.storageUse(used, used, limit) { context.cacheDir.usableSpace }
        }.watched()

    /** The song cache out of [Preferences.MEDIA3_CACHE_SIZE], `null` until known. */
    val songCache: StateFlow<StorageUse?> =
        combine(ticks, Preferences.MEDIA3_CACHE_SIZE.state) { _, limit ->
            val used = caches.cache.cacheSpace
            context.storageUse(used, used, limit) { context.cacheDir.usableSpace }
        }.watched()

    /**
     * The song downloads out of [Preferences.MEDIA3_DOWNLOAD_SIZE], the active user's and every
     * user's, `null` until known.
     */
    val songDownloads: StateFlow<StorageUse?> =
        combine(overview, Preferences.MEDIA3_DOWNLOAD_SIZE.state) { overview, limit ->
            context.storageUse(overview.userBytes, overview.allBytes, limit) {
                context.noBackupFilesDir.usableSpace
            }
        }.watched()

    /**
     * What the app keeps against the device's storage, every user's, with what the rest of the
     * device takes; `null` until known.
     */
    val deviceStorage: StateFlow<DeviceStorage?> =
        combine(ticks, overview, songs.lyricsBytes()) { _, overview, lyrics ->
            val device = StatFs(Environment.getDataDirectory().path)
            val parts = mapOf(
                StoragePart.IMAGE_CACHE to (loader.diskCache?.size ?: imageCacheFiles()),
                StoragePart.SONG_CACHE to caches.cache.cacheSpace,
                StoragePart.SONG_DOWNLOADS to overview.allBytes,
                StoragePart.DOWNLOAD_COVERS to covers.downloads.filesSize(),
                StoragePart.LYRICS to lyrics,
            )
            val size = { bytes: Long -> Formatter.formatShortFileSize(context, bytes) }
            val used = device.totalBytes - device.availableBytes
            DeviceStorage(
                totalBytes = device.totalBytes,
                usedBytes = used,
                parts = parts.map { (part, bytes) -> PartUse(part, bytes, size(bytes)) },
                total = size(device.totalBytes),
                used = size(used),
                app = size(parts.values.sum()),
            )
        }.watched()

    /**
     * Deletes every image the disk cache keeps, the files of a cache turned off too, and with
     * [memory] the images decoded in memory, which load again from where they come from.
     */
    fun clearImageCache(memory: Boolean) = wipe {
        if (memory) loader.memoryCache?.clear()
        loader.diskCache?.clear() ?: context.imageCacheDir().deleteRecursively()
    }

    /** Deletes every song the song cache keeps, see [SongCaches.clear]. */
    fun clearSongCache() = wipe { caches.clear() }

    /** Deletes every user's downloads, see [SongDownloads.deleteAll]. */
    fun deleteAllDownloads() {
        downloads.deleteAll()
    }

    /** Restarts the app once the settings are written, see [KreateApplication.restartAfterPlayback]. */
    fun restart() = scope.launch {
        Preferences.flush()
        (context.applicationContext as KreateApplication).restartAfterPlayback()
    }

    private fun wipe(block: () -> Unit) = scope.launch(Dispatchers.IO) {
        block()
        wiped.update { it + 1 }
    }

    /** The room the image cache's files take while there is no disk cache to ask. */
    private fun imageCacheFiles(): Long = context.imageCacheDir().filesSize()

    private fun File.filesSize(): Long = walk().filter { it.isFile }.sumOf { it.length() }

    private fun <T> Flow<T>.watched(): StateFlow<T?> =
        distinctUntilChanged()
            .flowOn(Dispatchers.IO)
            .stateIn(viewModelScope, WHILE_SHOWN, null)

    private companion object {
        /** How often the room of the caches is looked at again, they grow as songs play. */
        const val INTERVAL = 2000L
    }
}
