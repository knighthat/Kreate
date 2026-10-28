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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.knighthat.kreate3.R
import me.knighthat.kreate3.ui.component.ComponentPreviews
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.theme.ChartColors
import me.knighthat.kreate3.ui.theme.chartColors

/** What the app keeps on the device, a part of [DeviceStorage]. */
enum class StoragePart { IMAGE_CACHE, SONG_CACHE, SONG_DOWNLOADS, DOWNLOAD_COVERS, LYRICS }

/** The room [part] takes, as a number and as text. */
@Immutable
data class PartUse(val part: StoragePart, val bytes: Long, val size: String)

/**
 * The device's storage: [usedBytes] of [totalBytes] taken, [parts] of it by the app, the rest by
 * other apps and the system.
 *
 * @property app the room every part takes together, as text
 */
@Immutable
data class DeviceStorage(
    val totalBytes: Long,
    val usedBytes: Long,
    val parts: List<PartUse>,
    val total: String,
    val used: String,
    val app: String,
) {

    /** The room other apps and the system take. */
    val otherBytes: Long
        get() = (usedBytes - parts.sumOf { it.bytes }).coerceAtLeast(0)
}

/**
 * How full the device is, like a phone's storage page: one bar of the app's parts, each in its own
 * color, then the rest of the device in grey, then what is free; under it the parts named with
 * their size.
 */
@Composable
internal fun DeviceStorageUsage(storage: DeviceStorage, modifier: Modifier = Modifier) =
    Column(modifier) {
        val colors = MaterialTheme.colorScheme
        val chartColors = MaterialTheme.chartColors
        val other = colors.onSurfaceVariant.copy(alpha = .4f)

        Text(
            stringResource(R.string.statistics_storage_device_used, storage.used, storage.total),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            stringResource(
                R.string.statistics_storage_app_uses,
                stringResource(R.string.app_name),
                storage.app,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
        )

        Spacer(Modifier.height(8.dp))

        // Each part as wide as its share, a part with any room at least a sliver to be seen, a
        // gap between parts so neighbors don't run together
        val total = storage.totalBytes.coerceAtLeast(1).toFloat()
        Row(
            horizontalArrangement = Arrangement.spacedBy(PART_GAP),
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHighest)
        ) {
            storage.parts.filter { it.bytes > 0 }.forEach {
                Box(
                    Modifier
                        .weight((it.bytes / total).coerceAtLeast(MIN_SHARE))
                        .fillMaxHeight()
                        .background(it.part.color(chartColors))
                )
            }
            if (storage.otherBytes > 0)
                Box(
                    Modifier
                        .weight(storage.otherBytes / total)
                        .fillMaxHeight()
                        .background(other)
                )
            val free = (storage.totalBytes - storage.usedBytes).coerceAtLeast(0)
            if (free > 0) Spacer(Modifier.weight(free / total))
        }

        Spacer(Modifier.height(8.dp))

        // Two columns of equal width, so the second one lines up whatever the first one's labels
        FlowRow(
            maxItemsInEachRow = 2,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            storage.parts.forEach {
                Legend(
                    it.part.color(chartColors),
                    stringResource(it.part.labelRes),
                    it.size,
                    Modifier.weight(1f),
                )
            }
            Legend(
                other,
                stringResource(R.string.statistics_storage_other),
                null,
                Modifier.weight(1f),
            )
        }
    }

/** A dot of [color], the [label] of what it stands for and its [size]. */
@Composable
private fun Legend(color: Color, label: String, size: String?, modifier: Modifier = Modifier) =
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            if (size == null) label else "$label $size",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 6.dp),
        )
    }

/** The smallest share a part with any room is drawn with. */
private const val MIN_SHARE = .005f

/** The gap between two parts of the bar. */
private val PART_GAP = 2.dp

/** A part keeps its color whatever its size, in the bar's order so neighbors stay apart. */
private fun StoragePart.color(colors: ChartColors): Color = when (this) {
    StoragePart.IMAGE_CACHE -> colors.blue
    StoragePart.SONG_CACHE -> colors.orange
    StoragePart.SONG_DOWNLOADS -> colors.aqua
    StoragePart.DOWNLOAD_COVERS -> colors.yellow
    StoragePart.LYRICS -> colors.pink
}

private val StoragePart.labelRes: Int
    get() = when (this) {
        StoragePart.IMAGE_CACHE -> R.string.statistics_storage_image_cache
        StoragePart.SONG_CACHE -> R.string.statistics_storage_song_cache
        StoragePart.SONG_DOWNLOADS -> R.string.statistics_storage_song_downloads
        StoragePart.DOWNLOAD_COVERS -> R.string.statistics_storage_download_covers
        StoragePart.LYRICS -> R.string.statistics_storage_lyrics
    }

/** A device of 128 GB, 71 GB of it used, 9.6 GB by the app. */
internal fun previewDeviceStorage() = DeviceStorage(
    totalBytes = 128_000_000_000,
    usedBytes = 71_000_000_000,
    parts = listOf(
        PartUse(StoragePart.IMAGE_CACHE, 96_000_000, "96 MB"),
        PartUse(StoragePart.SONG_CACHE, 6_200_000_000, "6.2 GB"),
        PartUse(StoragePart.SONG_DOWNLOADS, 2_900_000_000, "2.9 GB"),
        PartUse(StoragePart.DOWNLOAD_COVERS, 410_000_000, "410 MB"),
        PartUse(StoragePart.LYRICS, 1_200_000, "1.2 MB"),
    ),
    total = "128 GB",
    used = "71 GB",
    app = "9.6 GB",
)

@ComponentPreviews
@Composable
private fun DeviceStorageUsagePreview() = PreviewTheme {
    Surface { DeviceStorageUsage(previewDeviceStorage(), Modifier.padding(16.dp)) }
}
