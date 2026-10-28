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

import android.content.Context
import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.knighthat.kreate3.R
import me.knighthat.kreate3.preferences.Preferences

/**
 * The room something takes on the device out of its limit, as numbers and as text.
 *
 * @property userBytes the active user's part of [allBytes], all of it for what users share
 * @property allBytes every user's
 * @property limitBytes the most it may take, `0` while it is off, [Long.MAX_VALUE] without a limit
 * @property scaleBytes what the bar is drawn out of: [limitBytes], or with no limit the room it
 * takes and the room left on the device
 * @property limit [limitBytes] as text, the status while it is off or has no limit
 */
@Immutable
data class StorageUse(
    val userBytes: Long,
    val allBytes: Long,
    val limitBytes: Long,
    val scaleBytes: Long,
    val user: String,
    val all: String,
    val limit: String,
) {

    /** Whether other users take room this user doesn't. */
    val shared: Boolean
        get() = allBytes > userBytes

    /** Whether [limitBytes] is a size, not off or unlimited. */
    val limited: Boolean
        get() = limitBytes in 1..<Long.MAX_VALUE
}

/**
 * A [StorageUse] of [allBytes] out of [limitBytes], [userBytes] of them the active user's.
 *
 * @param binary the units of the texts, see [fileSize]
 * @param freeBytes the room left where it is kept, asked only without a limit
 */
internal fun Context.storageUse(
    userBytes: Long,
    allBytes: Long,
    limitBytes: Long,
    binary: Boolean,
    freeBytes: () -> Long,
): StorageUse {
    val size = { bytes: Long -> resources.fileSize(bytes, binary) }
    return StorageUse(
        userBytes = userBytes,
        allBytes = allBytes,
        limitBytes = limitBytes,
        scaleBytes = when {
            limitBytes <= 0 -> allBytes
            limitBytes == Long.MAX_VALUE -> allBytes + freeBytes()
            else -> limitBytes
        },
        user = size(userBytes),
        all = size(allBytes),
        limit = when {
            limitBytes <= 0 -> getString(R.string.status_off)
            limitBytes == Long.MAX_VALUE -> getString(R.string.status_unlimited)
            else -> size(limitBytes)
        },
    )
}

/** Unit strings of [fileSize] from bytes up, in units of 1024. */
private val BINARY_UNITS = intArrayOf(
    R.string.bytes, R.string.kib, R.string.mib, R.string.gib, R.string.tib, R.string.pib,
)

/** Unit strings of [fileSize] from bytes up, in units of 1000. */
private val DECIMAL_UNITS = intArrayOf(
    R.string.bytes, R.string.kb, R.string.mb, R.string.gb, R.string.tb, R.string.pb,
)

/**
 * [bytes] as a short size, in units of 1024 (KiB, MiB…) when [binary], of 1000 (kB, MB…)
 * otherwise. Like Android's short file sizes: past 900 it goes to the next unit, and it keeps two
 * digits under 1, one under 10, none above.
 */
fun Resources.fileSize(bytes: Long, binary: Boolean = Preferences.BINARY_SIZES.value): String {
    val units = if (binary) BINARY_UNITS else DECIMAL_UNITS
    val base = if (binary) 1024.0 else 1000.0
    var value = bytes.toDouble()
    var unit = 0
    while (value > 900 && unit < units.lastIndex) {
        value /= base
        unit++
    }
    val format = when {
        unit == 0 || value >= 10 -> "%.0f"
        value < 1 -> "%.2f"
        else -> "%.1f"
    }
    return getString(units[unit], format.format(configuration.locales[0], value))
}

/**
 * How much of its limit [storage] takes: this user's and every user's when other users take more,
 * this user's alone otherwise, over a bar of the two.
 */
@Composable
fun StorageUsage(storage: StorageUse, modifier: Modifier = Modifier) = Column(modifier) {
    val resources = LocalResources.current
    val ofLimit = { used: String ->
        resources.getString(
            if (storage.limited) R.string.statistics_storage_of_limit
            else R.string.statistics_storage_with_status,
            used,
            storage.limit,
        )
    }
    val userColor = MaterialTheme.colorScheme.primary
    val othersColor = MaterialTheme.colorScheme.secondary

    if (storage.shared)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${stringResource(R.string.statistics_storage_this_user)} ${storage.user}",
                style = MaterialTheme.typography.labelLarge,
                color = userColor,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${stringResource(R.string.statistics_storage_all_users)} ${ofLimit(storage.all)}",
                style = MaterialTheme.typography.labelLarge,
                color = othersColor,
            )
        }
    else Text(ofLimit(storage.user), style = MaterialTheme.typography.labelLarge)

    Spacer(Modifier.height(8.dp))

    // This user's part over every user's, both out of the scale
    val scale = storage.scaleBytes.coerceAtLeast(1).toFloat()
    val all = (storage.allBytes / scale).coerceIn(0f, 1f)
    val user = (storage.userBytes / scale).coerceIn(0f, all)
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        Box(
            Modifier
                .fillMaxWidth(all)
                .fillMaxHeight()
                .background(othersColor)
        )
        Box(
            Modifier
                .fillMaxWidth(user)
                .fillMaxHeight()
                .background(userColor)
        )
    }
}

//<editor-fold defaultstate="collapsed" desc="Previews">
@ComponentPreviews
@Composable
private fun StorageUsageShared() = PreviewTheme {
    Surface {
        StorageUsage(
            StorageUse(
                1_400_000_000, 2_900_000_000, 4_000_000_000, 4_000_000_000,
                "1.4 GB", "2.9 GB", "4.0 GB",
            ),
            Modifier.padding(16.dp),
        )
    }
}

@ComponentPreviews
@Composable
private fun StorageUsageAlone() = PreviewTheme {
    Surface {
        StorageUsage(
            StorageUse(
                90_000_000, 90_000_000, 134_000_000, 134_000_000, "90 MB", "90 MB", "134 MB",
            ),
            Modifier.padding(16.dp),
        )
    }
}

@ComponentPreviews
@Composable
private fun StorageUsageUnlimited() = PreviewTheme {
    Surface {
        StorageUsage(
            StorageUse(
                6_200_000_000, 6_200_000_000, Long.MAX_VALUE, 40_000_000_000,
                "6.2 GB", "6.2 GB", "Unlimited",
            ),
            Modifier.padding(16.dp),
        )
    }
}
//</editor-fold>
