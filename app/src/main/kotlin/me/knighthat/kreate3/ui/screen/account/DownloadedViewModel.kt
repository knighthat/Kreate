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

package me.knighthat.kreate3.ui.screen.account

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.knighthat.kreate3.database.model.DownloadedSong
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.download.DownloadedFile
import me.knighthat.kreate3.download.SongDownloads
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.preferences.GlobalPreferences
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.ui.component.StorageUse
import me.knighthat.kreate3.ui.component.fileSize
import me.knighthat.kreate3.ui.component.labelRes
import me.knighthat.kreate3.ui.component.origin
import me.knighthat.kreate3.ui.component.storageUse
import me.knighthat.kreate3.ui.component.toMediaItem

/**
 * A whole download: its [item], and [file] saying its size and quality.
 *
 * @property disliked whether the user disliked the song, which only shows with
 * [DownloadedViewModel.showDisliked]
 */
@Immutable
class DownloadedRow(val item: MediaItem, val file: String, val disliked: Boolean)

/**
 * The songs the active user downloaded whole, most recently downloaded first, with the room they
 * take and the downloads not whole yet ([DownloadQueueViewModel]). Nothing is kept: the page
 * opens on every origin, newest first, disliked songs hidden.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DownloadedViewModel(
    private val context: Context,
    songRepository: UserSongRepository,
    private val downloads: SongDownloads,
) : DownloadQueueViewModel(context, downloads) {

    private val _newestFirst = MutableStateFlow(true)
    private val _origins = MutableStateFlow(emptySet<Origin>())
    private val _showDisliked = MutableStateFlow(false)

    /** Every whole download in the order picked, disliked ones too, `null` until known. */
    private val downloaded: StateFlow<List<DownloadedRow>?> =
        combine(
            _newestFirst.flatMapLatest(songRepository::downloaded),
            // The overview comes again every second while a download runs, its files don't
            overview.filterNotNull().map { it.files }.distinctUntilChanged(),
            Preferences.BINARY_SIZES.state,
        ) { songs, files, binary -> songs.mapNotNull { it.toRow(files, binary) } }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, WHILE_SHOWN, null)

    /** Whether the most recently downloaded song comes first. */
    val newestFirst: StateFlow<Boolean> = _newestFirst.asStateFlow()

    /** The origins shown, every origin while it is empty. */
    val origins: StateFlow<Set<Origin>> = _origins.asStateFlow()

    /** Whether the songs the user disliked show, which every other list hides. */
    val showDisliked: StateFlow<Boolean> = _showDisliked.asStateFlow()

    /** Whether some downloads are hidden for being disliked, see [showDisliked]. */
    val hidesDisliked: StateFlow<Boolean> =
        combine(downloaded, _showDisliked) { rows, show ->
            !show && rows.orEmpty().any { it.disliked }
        }.stateIn(viewModelScope, WHILE_SHOWN, false)

    /**
     * The origins to offer a chip for: the ones of downloaded songs and the selected ones, so they
     * can be turned off. Empty when there's nothing to choose between.
     */
    val choices: StateFlow<List<Origin>> =
        combine(downloaded, _origins) { rows, chosen ->
            val offered = rows.orEmpty().mapNotNull { it.item.origin }.toSet() + chosen
            if (offered.size <= 1 && chosen.isEmpty()) emptyList()
            else Origin.entries.filter { it in offered }
        }.stateIn(viewModelScope, WHILE_SHOWN, emptyList())

    /** The whole downloads of the origins shown, and disliked ones if shown, `null` until known. */
    val songs: StateFlow<List<DownloadedRow>?> =
        combine(downloaded, _origins, _showDisliked) { rows, origins, showDisliked ->
            rows?.filter {
                (origins.isEmpty() || it.item.origin in origins) && (showDisliked || !it.disliked)
            }
        }.stateIn(viewModelScope, WHILE_SHOWN, null)

    /**
     * The room downloads take out of [GlobalPreferences.MEDIA3_DOWNLOAD_SIZE], the active user's and
     * every user's (see [me.knighthat.kreate3.download.DownloadsOverview]), `null` until known.
     */
    val storage: StateFlow<StorageUse?> =
        combine(
            overview,
            GlobalPreferences.MEDIA3_DOWNLOAD_SIZE.state,
            Preferences.BINARY_SIZES.state,
        ) { overview, limit, binary ->
            overview ?: return@combine null
            context.storageUse(overview.userBytes, overview.allBytes, limit, binary) {
                context.noBackupFilesDir.usableSpace
            }
        }.distinctUntilChanged()
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, WHILE_SHOWN, null)

    fun toggleOrder() {
        _newestFirst.value = !_newestFirst.value
    }

    fun toggleDisliked() {
        _showDisliked.value = !_showDisliked.value
    }

    /** Shows the songs of [origin] when they're hidden, hides them otherwise. */
    fun toggleOrigin(origin: Origin) {
        _origins.value = _origins.value.let { if (origin in it) it - origin else it + origin }
    }

    /** The active user leaves the download of every song shown, see [SongDownloads.delete]. */
    fun deleteAll() {
        songs.value.orEmpty().forEach { row ->
            row.item.origin?.let { downloads.delete(it, row.item.mediaId) }
        }
    }

    /**
     * A row for a song whose download is whole, `null` for one that isn't yet.
     *
     * @param binary the units of its size, see [fileSize]
     */
    private fun DownloadedSong.toRow(
        files: Map<String, DownloadedFile>,
        binary: Boolean,
    ): DownloadedRow? {
        val file = files[song.songId] ?: return null
        val size = context.resources.fileSize(file.bytes, binary)
        return DownloadedRow(
            item = song.toMediaItem() ?: return null,
            file = file.quality?.let { "$size · ${context.getString(it.labelRes)}" } ?: size,
            disliked = song.likedAt?.let { it.toEpochMilliseconds() < 0 } == true,
        )
    }
}
