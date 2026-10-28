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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.knighthat.kreate3.R
import me.knighthat.kreate3.download.DownloadStage
import me.knighthat.kreate3.download.DownloadWait
import me.knighthat.kreate3.download.DownloadsOverview
import me.knighthat.kreate3.download.SongDownloads
import me.knighthat.kreate3.download.UnfinishedDownload
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.ui.component.fileSize
import me.knighthat.kreate3.ui.component.labelRes
import me.knighthat.kreate3.ui.component.origin

/** How long the downloads are still followed once the screen stops, through a rotation. */
internal val WHILE_SHOWN = SharingStarted.WhileSubscribed(5_000)

/**
 * A download that isn't whole yet, with [status] saying where it stands: how much is there while
 * it's downloaded, its place in line for the first few waiting, how far it got once paused, or
 * why it failed.
 */
@Immutable
class UnfinishedRow(val download: UnfinishedDownload, val status: String)

/**
 * The downloads of the active user that aren't whole yet, and what they wait for, followed while
 * the screen shows. A tap on one pauses, resumes or retries it, see [tap].
 */
open class DownloadQueueViewModel(
    private val context: Context,
    private val downloads: SongDownloads,
) : ViewModel() {

    /** The active user's downloads, `null` until they're known. */
    protected val overview: StateFlow<DownloadsOverview?> = downloads.overview()
        .stateIn(viewModelScope, WHILE_SHOWN, null)

    /** In the order they were asked for, failed ones last, see [DownloadsOverview.unfinished]. */
    val unfinished: StateFlow<List<UnfinishedRow>> =
        combine(overview, Preferences.BINARY_SIZES.state) { overview, binary ->
            overview?.unfinished.orEmpty().toRows(binary)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, WHILE_SHOWN, emptyList())

    /** What every download waits for, `null` for nothing. */
    val waitingFor: StateFlow<DownloadWait?> = overview.map { it?.waitingFor }
        .stateIn(viewModelScope, WHILE_SHOWN, null)

    /** Pauses [download] while it runs or waits, resumes it once paused, tries it again once failed. */
    fun tap(download: UnfinishedDownload) {
        val origin = download.item.origin ?: return
        val id = download.item.mediaId
        when (download.stage) {
            DownloadStage.DOWNLOADING, DownloadStage.QUEUED -> downloads.pause(origin, id)
            DownloadStage.PAUSED -> downloads.resume(origin, id)
            DownloadStage.FAILED -> downloads.retry(origin, id)
        }
    }

    /** The active user leaves [download], see [SongDownloads.delete]. */
    fun cancel(download: UnfinishedDownload) {
        downloads.delete(download.item.origin ?: return, download.item.mediaId)
    }

    /** @param binary the units of the sizes, see [fileSize] */
    private fun List<UnfinishedDownload>.toRows(binary: Boolean): List<UnfinishedRow> {
        val size = { bytes: Long -> context.resources.fileSize(bytes, binary) }
        var queued = 0
        return map { download ->
            val place = if (download.stage == DownloadStage.QUEUED) ++queued else 0
            UnfinishedRow(download, download.status(place, size))
        }
    }

    /**
     * See [UnfinishedRow.status]; [place] is its place among the queued ones, from 1, [size] writes
     * a size.
     */
    private fun UnfinishedDownload.status(place: Int, size: (Long) -> String): String =
        when (stage) {
            DownloadStage.DOWNLOADING -> {
                val bytes = size(bytes)
                totalBytes?.let {
                    context.getString(
                        R.string.status_download_progress,
                        bytes,
                        size(it)
                    )
                }
                    ?: bytes
            }

            DownloadStage.QUEUED -> context.getString(
                when (place) {
                    1 -> R.string.status_download_next_in_line
                    2 -> R.string.status_download_in_line_2
                    3 -> R.string.status_download_in_line_3
                    4 -> R.string.status_download_in_line_4
                    5 -> R.string.status_download_in_line_5
                    else -> stage.labelRes
                }
            )

            DownloadStage.PAUSED -> percent?.let {
                context.getString(
                    R.string.status_download_paused_at,
                    context.getString(R.string.percent, it),
                )
            } ?: context.getString(stage.labelRes)

            // The reasons are written to follow a song's name, lowercase
            DownloadStage.FAILED -> failure?.let {
                context.getString(it).replaceFirstChar(Char::uppercase)
            }
                ?: context.getString(R.string.error_download_failed)
        }
}
