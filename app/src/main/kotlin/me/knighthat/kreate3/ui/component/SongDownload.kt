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

package me.knighthat.kreate3.ui.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import kotlinx.coroutines.launch
import me.knighthat.kreate3.R
import me.knighthat.kreate3.constant.StreamQuality
import me.knighthat.kreate3.download.DownloadPlan
import me.knighthat.kreate3.download.SongDownloads
import me.knighthat.kreate3.download.SongStorage
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import org.koin.compose.koinInject

/**
 * Where the songs are kept on the device, by their key ([SongDownloads.key]), see
 * [SongDownloads.songs]. Empty unless provided, like in previews.
 */
val LocalSongStorage = compositionLocalOf<Map<String, SongStorage>> { emptyMap() }

/**
 * Where the song of [item] is kept, `null` for one kept nowhere and for anything not a song of a
 * server or YouTube.
 */
val MediaItem.storage: SongStorage?
    @Composable
    get() {
        val origin = origin?.takeIf { it != Origin.LOCAL } ?: return null
        if (mediaMetadata.isPlayable != true) return null
        return LocalSongStorage.current[SongDownloads.key(origin, mediaId)]
    }

/** The [ItemBadge] of a song downloaded or cached, nothing for any other [storage]. */
@Composable
fun StorageBadge(storage: SongStorage?, size: Dp = BADGE_SIZE) = when (storage) {
    SongStorage.DOWNLOADED ->
        ItemBadge(R.drawable.downloaded, stringResource(R.string.semantic_downloaded), size = size)

    SongStorage.CACHED ->
        ItemBadge(R.drawable.cloud_done, stringResource(R.string.semantic_cached), size = size)

    SongStorage.DOWNLOADING, SongStorage.PAUSED, null -> Unit
}

/**
 * The download button of the song menu for [item], drawn by [button], with the dialogs it opens.
 * Nothing for a song on the device or anything not a song.
 *
 * Not kept: downloads it. Cached: asks whether to move the copy to the downloads (downloading
 * it again when it is worse than the quality set now) or remove it from the cache. Downloading
 * or downloaded: asks to stop or delete the download. While downloads are off
 * ([Preferences.MEDIA3_DOWNLOAD_SIZE] is `0`) the button is disabled for a song not kept, and a
 * cached one can only be removed from the cache.
 */
@Composable
internal fun SongDownloadAction(
    item: MediaItem,
    button: @Composable (icon: Int, label: String, enabled: Boolean, onClick: () -> Unit) -> Unit,
) {
    val origin = item.origin?.takeIf { it != Origin.LOCAL } ?: return
    if (item.mediaMetadata.isPlayable != true) return
    val downloads = koinInject<SongDownloads>()
    val songs by downloads.songs.collectAsStateWithLifecycle()
    val storage = songs[SongDownloads.key(origin, item.mediaId)]
    val downloadLimit by Preferences.MEDIA3_DOWNLOAD_SIZE.collectAsStateWithLifecycle()
    val downloadsOff = downloadLimit <= 0
    val scope = rememberCoroutineScope()
    var plan by remember { mutableStateOf<DownloadPlan?>(null) }
    var deleting by remember { mutableStateOf(false) }
    val title = item.mediaMetadata.title?.toString().orEmpty()

    fun download() = downloads.download(item)

    button(storage.icon, stringResource(storage.labelRes), !downloadsOff || storage != null) {
        when (storage) {
            null -> download()
            SongStorage.CACHED -> scope.launch { plan = downloads.plan(origin, item.mediaId) }
            SongStorage.DOWNLOADING, SongStorage.PAUSED, SongStorage.DOWNLOADED -> deleting = true
        }
    }

    plan?.let {
        CachedSongDialog(
            plan = it,
            qualityLabel = { quality -> stringResource(quality.labelRes(origin)) },
            onDownload = if (downloadsOff) null else ({
                plan = null
                download()
            }),
            onRemove = {
                plan = null
                downloads.removeCached(origin, item.mediaId)
            },
            onDismiss = { plan = null },
        )
    }
    if (deleting)
        DeleteDownloadDialog(
            title = title,
            isRunning = storage == SongStorage.DOWNLOADING || storage == SongStorage.PAUSED,
            onResume = if (storage == SongStorage.PAUSED) {
                {
                    deleting = false
                    downloads.resume(origin, item.mediaId)
                }
            } else null,
            onDelete = {
                deleting = false
                downloads.delete(origin, item.mediaId)
            },
            onDismiss = { deleting = false },
        )
}

/** The icon of the download button for a song kept as [this], see [SongDownloadAction]. */
@get:DrawableRes
internal val SongStorage?.icon: Int
    get() = when (this) {
        null -> R.drawable.download
        SongStorage.CACHED -> R.drawable.cloud_done
        SongStorage.DOWNLOADING, SongStorage.PAUSED -> R.drawable.downloading
        SongStorage.DOWNLOADED -> R.drawable.downloaded
    }

/** The label of the download button for a song kept as [this], see [SongDownloadAction]. */
@get:StringRes
internal val SongStorage?.labelRes: Int
    get() = when (this) {
        null -> R.string.menu_download
        SongStorage.CACHED -> R.string.menu_cached
        SongStorage.DOWNLOADING -> R.string.menu_downloading
        SongStorage.PAUSED -> R.string.menu_paused
        SongStorage.DOWNLOADED -> R.string.menu_downloaded
    }

/** What [this] quality means for the songs of [origin]. */
private fun StreamQuality.labelRes(origin: Origin): Int =
    if (origin == Origin.YOUTUBE) youTubeLabelRes else openSubsonicLabelRes

/**
 * Asks what to do with a cached song: move it to the downloads, or remove it from the cache.
 * When the cached copy is worse than the quality set now, it warns that the song is downloaded
 * again in that quality.
 */
@Composable
private fun CachedSongDialog(
    plan: DownloadPlan,
    qualityLabel: @Composable (StreamQuality) -> String,
    onDownload: (() -> Unit)?,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) = AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.dialog_title_cached_song)) },
    text = {
        val cached = plan.cached
        if (plan.movesCache || cached == null)
            Text(stringResource(R.string.dialog_body_cached_song_move))
        else
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.dialog_body_cached_song_worse_1),
                    color = MaterialTheme.colorScheme.error,
                )
                Text(
                    stringResource(
                        R.string.dialog_body_cached_song_worse_2,
                        qualityLabel(cached),
                        qualityLabel(plan.quality),
                    )
                )
            }
    },
    confirmButton = {
        DialogButtons(onDismiss) {
            if (onDownload != null)
                TextButton(onClick = onDownload) {
                    Text(
                        stringResource(
                            if (plan.movesCache) R.string.action_move_to_downloads
                            else R.string.action_download
                        )
                    )
                }
            TextButton(onClick = onRemove) {
                Text(stringResource(R.string.action_remove_from_cache))
            }
        }
    },
)

/**
 * Asks to delete the download of the song called [title], or to stop it while [isRunning]. A
 * paused one can be resumed instead, with [onResume].
 */
@Composable
private fun DeleteDownloadDialog(
    title: String,
    isRunning: Boolean,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onResume: (() -> Unit)? = null,
) = AlertDialog(
    onDismissRequest = onDismiss,
    title = {
        Text(
            stringResource(
                if (isRunning) R.string.dialog_title_stop_download
                else R.string.dialog_title_delete_download
            )
        )
    },
    text = {
        Text(
            stringResource(
                if (isRunning) R.string.dialog_body_stop_download
                else R.string.dialog_body_delete_download,
                stringResource(R.string.quoted, title),
            )
        )
    },
    confirmButton = {
        DialogButtons(onDismiss) {
            if (onResume != null)
                TextButton(onClick = onResume) { Text(stringResource(R.string.action_resume)) }
            TextButton(onClick = onDelete) {
                Text(
                    stringResource(if (isRunning) R.string.action_stop else R.string.action_delete)
                )
            }
        }
    },
)

/**
 * The [actions] of a dialog one under the other, at its end, the main one first, then Cancel,
 * which calls [onDismiss]. Side by side they wrapped unevenly.
 */
@Composable
private fun DialogButtons(onDismiss: () -> Unit, actions: @Composable () -> Unit) = Column(
    horizontalAlignment = Alignment.End,
    modifier = Modifier.fillMaxWidth(),
) {
    actions()
    TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
}

//<editor-fold defaultstate="collapsed" desc="Previews">
@ComponentPreviews
@Composable
private fun StorageBadgePreview() = PreviewTheme {
    Surface {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StorageBadge(SongStorage.DOWNLOADED)
            StorageBadge(SongStorage.CACHED)
        }
    }
}

@ComponentPreviews
@Composable
private fun CachedSongDialogPreview() = PreviewTheme {
    CachedSongDialog(
        plan = DownloadPlan(cached = StreamQuality.HIGH, quality = StreamQuality.HIGH),
        qualityLabel = { stringResource(it.youTubeLabelRes) },
        onDownload = {},
        onRemove = {},
        onDismiss = {},
    )
}

/** Downloads are off, the song can only leave the cache. */
@ComponentPreviews
@Composable
private fun CachedSongDownloadsOffDialogPreview() = PreviewTheme {
    CachedSongDialog(
        plan = DownloadPlan(cached = StreamQuality.HIGH, quality = StreamQuality.HIGH),
        qualityLabel = { stringResource(it.youTubeLabelRes) },
        onDownload = null,
        onRemove = {},
        onDismiss = {},
    )
}

@ComponentPreviews
@Composable
private fun CachedSongWorseDialogPreview() = PreviewTheme {
    CachedSongDialog(
        plan = DownloadPlan(cached = StreamQuality.LOW, quality = StreamQuality.HIGH),
        qualityLabel = { stringResource(it.youTubeLabelRes) },
        onDownload = {},
        onRemove = {},
        onDismiss = {},
    )
}

@ComponentPreviews
@Composable
private fun DeleteDownloadDialogPreview() = PreviewTheme {
    DeleteDownloadDialog(title = "Neon Coast", isRunning = false, onDelete = {}, onDismiss = {})
}

@ComponentPreviews
@Composable
private fun PausedDownloadDialogPreview() = PreviewTheme {
    DeleteDownloadDialog(
        title = "Neon Coast",
        isRunning = true,
        onDelete = {},
        onDismiss = {},
        onResume = {},
    )
}
//</editor-fold>
