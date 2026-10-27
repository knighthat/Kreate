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
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.knighthat.kreate3.R
import me.knighthat.kreate3.ui.component.SectionTitle
import kotlin.time.Duration

// The parts the statistics of a song, an album and an artist share

/** "Nothing played yet" in the middle of the list. */
internal fun LazyListScope.nothingPlayed() = item(key = "empty") {
    Text(
        stringResource(R.string.screen_statistics_status_nothing_played),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillParentMaxSize()
            .wrapContentSize(Alignment.Center),
    )
}

/** The three charts of [charts], each under its heading. */
internal fun LazyListScope.listeningCharts(charts: ListeningCharts) {
    item(key = "over-time") {
        SectionTitle(stringResource(R.string.section_header_listening_over_time))
        TimeColumnChart(
            charts.overTime,
            columnWidth = 10.dp,
            labelEvery = (charts.overTime.size / 4).coerceAtLeast(1),
        )
    }
    item(key = "by-hour") {
        SectionTitle(stringResource(R.string.section_header_listening_by_hour))
        TimeColumnChart(charts.byHour, columnWidth = 8.dp, labelEvery = 6)
    }
    item(key = "by-weekday") {
        SectionTitle(stringResource(R.string.section_header_listening_by_weekday))
        TimeColumnChart(charts.byWeekday, columnWidth = 28.dp)
        Spacer(Modifier.height(16.dp))
    }
}

/** Numbers, a label and its value on each line. */
@Composable
internal fun NumberLines(lines: List<Pair<String, String>>) = Column(
    verticalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
) {
    lines.forEach { (label, value) ->
        Row {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The whole minutes something was listened to. */
@Composable
internal fun MinutesText(time: Duration) {
    val minutes = time.inWholeMinutes.toInt()
    Text(
        pluralStringResource(R.plurals.minute, minutes, minutes),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The day of a play, [time] in epoch millis, with its year; empty for `null`. */
internal fun Context.playDate(time: Long?): String = time?.let {
    DateUtils.formatDateTime(this, it, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR)
}.orEmpty()

/** Made-up charts for the previews of the statistics screens. */
internal fun previewListeningCharts(): ListeningCharts {
    val hours = listOf(0, 0, 0, 0, 0, 0, 1, 3, 5, 2, 1, 0, 4, 2, 1, 1, 3, 6, 9, 12, 8, 5, 2, 1)
    return ListeningCharts(
        overTime = listOf(3f, 8f, 2f, 0f, 12f, 6f, 9f, 4f, 7f, 10f).mapIndexed { i, value ->
            ChartBar(value, "Month ${i + 1}", "Month ${i + 1} · ${value.toInt()} minutes")
        },
        byHour = hours.mapIndexed { hour, value ->
            ChartBar(value.toFloat(), "$hour:00", "$hour:00 · $value minutes")
        },
        byWeekday = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun").mapIndexed { i, day ->
            ChartBar((i * 7 % 5 + 1).toFloat(), day, "$day · ${i + 1} minutes")
        },
    )
}
