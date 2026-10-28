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

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.knighthat.kreate3.R
import me.knighthat.kreate3.download.DownloadStage
import me.knighthat.kreate3.download.DownloadWait
import me.knighthat.kreate3.download.UnfinishedDownload
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.SongItem
import me.knighthat.kreate3.ui.component.labelRes
import me.knighthat.kreate3.ui.component.previewSongs
import me.knighthat.kreate3.ui.screen.detail.DetailScaffold
import org.koin.compose.viewmodel.koinViewModel

/**
 * Every download of the active user that isn't whole yet, opened from [DownloadedScreen], in the
 * order they were asked for, failed ones last. A tap pauses, resumes or retries one, see
 * [UnfinishedItem]; the rows show no badges, they're all songs being downloaded.
 *
 * @param contentPadding room the list adds after its rows, e.g. so the last one can scroll clear
 * of what floats over the screen
 */
@Composable
fun DownloadQueueScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: DownloadQueueViewModel = koinViewModel(),
) {
    val unfinished by viewModel.unfinished.collectAsStateWithLifecycle()
    val waitingFor by viewModel.waitingFor.collectAsStateWithLifecycle()

    DownloadQueueContent(
        unfinished = unfinished,
        waitingFor = waitingFor,
        onBack = onBack,
        actions = unfinishedActions(viewModel),
        modifier = modifier,
        contentPadding = contentPadding,
    )
}

@Composable
private fun DownloadQueueContent(
    unfinished: List<UnfinishedRow>,
    waitingFor: DownloadWait?,
    onBack: () -> Unit,
    actions: UnfinishedActions,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) = DetailScaffold(
    title = stringResource(R.string.screen_title_download_queue),
    onBack = onBack,
    modifier = modifier,
    contentPadding = contentPadding,
) { padding ->
    LazyColumn(
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding(),
            bottom = padding.calculateBottomPadding(),
        ),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (unfinished.isEmpty())
            item(key = "empty") {
                Text(
                    stringResource(R.string.screen_download_queue_status_nothing),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillParentMaxSize()
                        .wrapContentSize(Alignment.Center),
                )
            }
        else waitingFor?.let { item(key = "waiting") { WaitingLine(it) } }

        items(unfinished, key = { it.download.item.mediaId }) {
            UnfinishedItem(it, actions, showBadges = false)
        }
    }
}

/** What the rows of unfinished downloads do, see [UnfinishedItem]. */
internal class UnfinishedActions(
    val tap: (UnfinishedDownload) -> Unit,
    val cancel: (UnfinishedDownload) -> Unit,
)

/** The actions of [viewModel]. */
internal fun unfinishedActions(viewModel: DownloadQueueViewModel) =
    UnfinishedActions(tap = viewModel::tap, cancel = viewModel::cancel)

/** What every download waits for, above the rows. */
@Composable
internal fun WaitingLine(waitingFor: DownloadWait, modifier: Modifier = Modifier) = Text(
    stringResource(
        when (waitingFor) {
            DownloadWait.WIFI -> R.string.status_downloads_waiting_for_wifi
            DownloadWait.BATTERY -> R.string.status_downloads_waiting_for_battery
        }
    ),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = modifier.padding(horizontal = 16.dp, vertical = 4.dp),
)

/**
 * A download that isn't whole yet: where it stands over its cover ([DownloadState]) and in
 * words above its title ([UnfinishedRow.status], in the error color once it failed), and a
 * button to cancel it. A tap pauses it while it runs or waits, resumes it once paused, and tries
 * it again once it failed; a long press opens the song's menu.
 *
 * @param showBadges whether the song's icons show above its title, beside the status
 */
@Composable
internal fun UnfinishedItem(row: UnfinishedRow, actions: UnfinishedActions, showBadges: Boolean) {
    val download = row.download
    val failed = download.stage == DownloadStage.FAILED

    SongItem(
        item = download.item,
        onClick = { actions.tap(download) },
        showAlbum = false,
        thumbnailOverlay = { DownloadState(download, Modifier.matchParentSize()) },
        overline = row.status,
        overlineColor = if (failed) MaterialTheme.colorScheme.error else Color.Unspecified,
        showBadges = showBadges,
        trailing = {
            IconButton(onClick = { actions.cancel(download) }) {
                Icon(
                    painterResource(R.drawable.close),
                    contentDescription = stringResource(R.string.semantic_cancel_download),
                )
            }
        },
    )
}

/**
 * Where a download stands, over its darkened cover in light marks whatever the theme: a ring
 * with the percent inside while it's downloaded (turning without one while that isn't known),
 * an hourglass while it waits, the ring where it stopped under a pause icon once paused, and a
 * warning once it failed.
 */
@Composable
private fun DownloadState(download: UnfinishedDownload, modifier: Modifier = Modifier) = Box(
    contentAlignment = Alignment.Center,
    modifier = modifier.background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
) {
    val percent = download.percent
    val ring = Modifier.size(40.dp)
    when (download.stage) {
        DownloadStage.DOWNLOADING ->
            if (percent == null) CircularProgressIndicator(
                ring,
                color = Color.White,
                strokeWidth = 3.dp
            )
            else {
                ProgressRing(percent, ring)
                Text(
                    stringResource(R.string.percent, percent),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }

        DownloadStage.QUEUED -> StateIcon(R.drawable.hourglass_empty, download.stage)

        DownloadStage.PAUSED -> {
            percent?.let { ProgressRing(it, ring.alpha(0.5f)) }
            StateIcon(R.drawable.pause, download.stage)
        }

        DownloadStage.FAILED -> {
            // The light error color of either theme, the cover is dark under it
            val scheme = MaterialTheme.colorScheme
            val tint =
                if (scheme.surface.luminance() > 0.5f) scheme.errorContainer else scheme.error
            StateIcon(R.drawable.warning, download.stage, tint)
        }
    }
}

@Composable
private fun ProgressRing(percent: Int, modifier: Modifier = Modifier) = CircularProgressIndicator(
    progress = { percent / 100f },
    modifier = modifier,
    color = Color.White,
    trackColor = Color.White.copy(alpha = 0.3f),
    strokeWidth = 3.dp,
)

/** The icon of [stage] over a cover, named after it for screen readers. */
@Composable
private fun StateIcon(@DrawableRes icon: Int, stage: DownloadStage, tint: Color = Color.White) =
    Icon(
        painterResource(icon),
        contentDescription = stringResource(stage.labelRes),
        tint = tint,
        modifier = Modifier.size(20.dp),
    )

/** Rows of every stage, see [UnfinishedItem]. */
internal fun previewUnfinished(): List<UnfinishedRow> =
    (previewSongs() + previewSongs(Origin.YOUTUBE)).take(7).mapIndexed { index, item ->
        when (index) {
            0 -> UnfinishedRow(
                UnfinishedDownload(item, DownloadStage.DOWNLOADING, 62, 4_100_000, 6_600_000),
                "4.1 MB of 6.6 MB",
            )

            1 -> UnfinishedRow(
                UnfinishedDownload(item, DownloadStage.DOWNLOADING, bytes = 2_300_000),
                "2.3 MB",
            )

            2 -> UnfinishedRow(UnfinishedDownload(item, DownloadStage.QUEUED), "Next in line")
            3 -> UnfinishedRow(UnfinishedDownload(item, DownloadStage.QUEUED), "2nd in line")
            4 -> UnfinishedRow(UnfinishedDownload(item, DownloadStage.PAUSED, 34), "Paused at 34%")
            5 -> UnfinishedRow(UnfinishedDownload(item, DownloadStage.PAUSED), "Paused")
            else -> UnfinishedRow(
                UnfinishedDownload(
                    item,
                    DownloadStage.FAILED,
                    failure = R.string.error_playback_no_connection
                ),
                "No connection",
            )
        }
    }

private val noActions = UnfinishedActions({}, {})

@ScreenPreviews
@Composable
private fun DownloadQueuePreview() = PreviewTheme {
    DownloadQueueContent(
        unfinished = previewUnfinished(),
        waitingFor = null,
        onBack = {},
        actions = noActions,
    )
}

/** Every download waits for Wi-Fi, see [WaitingLine]. */
@ScreenPreviews
@Composable
private fun DownloadQueueWaitingPreview() = PreviewTheme {
    DownloadQueueContent(
        unfinished = previewUnfinished().filter { it.download.stage == DownloadStage.QUEUED },
        waitingFor = DownloadWait.WIFI,
        onBack = {},
        actions = noActions,
    )
}
