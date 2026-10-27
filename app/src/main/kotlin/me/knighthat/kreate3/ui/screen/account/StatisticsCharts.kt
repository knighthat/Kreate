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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModel
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.ColumnCartesianLayerModel
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarkerController
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.pie.PieChart
import com.patrykandpatrick.vico.compose.pie.PieChartHost
import com.patrykandpatrick.vico.compose.pie.PieSize
import com.patrykandpatrick.vico.compose.pie.data.PieChartModel
import com.patrykandpatrick.vico.compose.pie.rememberPieChart
import me.knighthat.kreate3.R
import me.knighthat.kreate3.database.model.OriginTime
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.ui.component.Thumbnail
import me.knighthat.kreate3.ui.component.icon
import me.knighthat.kreate3.ui.component.sourceRes
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Columns of time listened, one per [ChartBar], drawn by Vico. A tap on a column shows its
 * [ChartBar.detail] above it, another tap hides it. Columns that don't fit scroll, the chart opens
 * on the last ones.
 *
 * @param columnWidth the width of a column, the chart stretches when they take less than its width
 * @param labelEvery how many columns apart the axis names them, from the first
 */
@Composable
internal fun TimeColumnChart(
    bars: List<ChartBar>,
    modifier: Modifier = Modifier,
    columnWidth: Dp = 8.dp,
    labelEvery: Int = 1,
) {
    if (bars.isEmpty()) return

    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    // The formatters are remembered once, they read the bars shown now
    val shown by rememberUpdatedState(bars)

    val chart = rememberCartesianChart(
        rememberColumnCartesianLayer(
            columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                rememberLineComponent(
                    fill = Fill(colors.primary),
                    thickness = columnWidth,
                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                )
            ),
            columnCollectionSpacing = COLUMN_GAP,
            rangeProvider = remember { CartesianLayerRangeProvider.fixed(minY = 0.0) },
        ),
        bottomAxis = HorizontalAxis.rememberBottom(
            line = rememberLineComponent(Fill(colors.outlineVariant), 1.dp),
            label = rememberTextComponent(typography.labelSmall.copy(color = colors.onSurfaceVariant)),
            valueFormatter = remember {
                // Vico refuses blank labels, an x past the bars still gets a name
                CartesianValueFormatter { _, x, _ ->
                    shown.getOrNull(x.roundToInt())?.label ?: "${x.roundToInt()}"
                }
            },
            itemPlacer = remember(labelEvery) { HorizontalAxis.ItemPlacer.aligned(spacing = { labelEvery }) },
            tick = null,
            guideline = null,
        ),
        marker = rememberDefaultCartesianMarker(
            label = rememberTextComponent(
                style = typography.labelLarge.copy(color = colors.onSurface),
                padding = Insets(horizontal = 8.dp, vertical = 4.dp),
                background = rememberShapeComponent(
                    Fill(colors.surfaceContainerHighest),
                    RoundedCornerShape(8.dp),
                ),
            ),
            valueFormatter = remember {
                DefaultCartesianMarker.ValueFormatter { _, targets ->
                    targets.firstOrNull()?.let { shown.getOrNull(it.x.roundToInt())?.detail }
                        .orEmpty()
                }
            },
        ),
        markerController = CartesianMarkerController.rememberToggleOnTap(),
    )
    val model = remember(bars) {
        CartesianChartModel(ColumnCartesianLayerModel.build { series(bars.map { it.value }) })
    }
    CartesianChartHost(
        chart = chart,
        model = model,
        scrollState = rememberVicoScrollState(initialScroll = Scroll.Absolute.End),
        modifier = modifier
            .padding(horizontal = 16.dp)
            .height(CHART_HEIGHT),
    )
}

/**
 * The [artists] as columns as tall as the time they were listened to, on a time axis of even
 * steps with faint lines across. The first, the most listened to, wears a crown. Each column
 * stands under the artist's picture; a tap on it opens the artist.
 */
@Composable
internal fun ArtistColumns(artists: List<RankedItem>, onOpen: (MediaItem) -> Unit) {
    if (artists.isEmpty()) return

    val resources = LocalResources.current
    val ticks = remember(artists) { timeTicks(artists.maxOf { it.timeSpent }) }
    val top = ticks.last()
    val color = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .height(BAR_HEIGHT + PICTURE_ROOM),
    ) {
        TimeAxis(ticks, top)
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .drawBehind {
                    ticks.forEach { tick ->
                        val y = size.height - BAR_HEIGHT.toPx() * (tick / top).toFloat()
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                    }
                }
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ARTIST_GAP),
                verticalAlignment = Alignment.Bottom,
                modifier = Modifier.fillMaxSize(),
            ) {
                artists.forEachIndexed { index, artist ->
                    val name = artist.item.mediaMetadata.title?.toString().orEmpty()
                    val said = "$name, ${resources.timeSpent(artist.timeSpent)}"
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpen(artist.item) }
                            .semantics(mergeDescendants = true) { contentDescription = said },
                    ) {
                        if (index == 0)
                            Icon(
                                painterResource(R.drawable.crown),
                                contentDescription = stringResource(R.string.semantic_most_listened),
                                tint = CROWN_COLOR,
                                modifier = Modifier.size(CROWN_SIZE),
                            )
                        Thumbnail(
                            uri = artist.item.mediaMetadata.artworkUri,
                            shape = CircleShape,
                            modifier = Modifier
                                .widthIn(max = PICTURE_SIZE)
                                .fillMaxWidth()
                                .aspectRatio(1f),
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(
                                    BAR_HEIGHT * (artist.timeSpent / top).toFloat()
                                        .coerceIn(MIN_BAR, 1f)
                                )
                                .background(
                                    color,
                                    RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                )
                        )
                    }
                }
            }
        }
    }
}

/**
 * The labels of [ticks] up the left of a chart, each centered on the height its time takes of
 * [BAR_HEIGHT] against [top], from the bottom.
 */
@Composable
private fun TimeAxis(ticks: List<Duration>, top: Duration) {
    val resources = LocalResources.current
    val inHours = ticks.getOrNull(1)?.let { it >= 1.hours } == true
    Layout(
        content = {
            ticks.forEach { tick ->
                Text(
                    if (inHours) resources.getString(R.string.h, tick.inWholeHours.toInt())
                    else resources.getString(R.string.min, tick.inWholeMinutes.toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        },
        modifier = Modifier.fillMaxHeight(),
    ) { measurables, constraints ->
        val placeables =
            measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val width = placeables.maxOf { it.width }
        val height = constraints.maxHeight
        val bars = BAR_HEIGHT.roundToPx()
        layout(width, height) {
            placeables.forEachIndexed { i, label ->
                val y = height - bars * (ticks[i] / top).toFloat() - label.height / 2f
                // The zero centers on the baseline too, half of it below the chart
                label.place(width - label.width, y.roundToInt().coerceAtLeast(0))
            }
        }
    }
}

/**
 * Ticks of a time axis from zero reaching at least [most], in at most [MAX_STEPS] even steps of
 * a round number of minutes, or of hours past them.
 */
internal fun timeTicks(most: Duration): List<Duration> {
    val step = TIME_STEPS.firstOrNull { it * MAX_STEPS >= most }
        ?: ((most / MAX_STEPS).inWholeHours + 1).hours
    val count = (most / step).let { kotlin.math.ceil(it).toInt() }.coerceAtLeast(1)
    return (0..count).map { step * it }
}

private val TIME_STEPS: List<Duration> =
    listOf(1, 2, 5, 10, 15, 20, 30).map { it.minutes } +
            listOf(1, 2, 3, 5, 10, 20, 50, 100, 200, 500).map { it.hours }
private const val MAX_STEPS = 4

/**
 * The share of the time listened of each origin as a ring, the total in its middle, with a legend
 * naming each origin, its time and share. An origin keeps its color whatever its rank.
 */
@Composable
internal fun SourcesPie(origins: List<OriginTime>) {
    if (origins.isEmpty()) return

    val resources = LocalResources.current
    val colors = MaterialTheme.colorScheme
    fun colorOf(origin: Origin) = when (origin) {
        Origin.YOUTUBE -> colors.primary
        Origin.OPEN_SUBSONIC -> colors.tertiary
        Origin.LOCAL -> colors.secondary
    }

    val total = origins.fold(Duration.ZERO) { sum, it -> sum + it.timeSpent }

    // Vico 3.3.1 fills the hole of a slice that goes all the way round, so a lone origin is drawn
    // as two halves of its color, with no gap to cut the ring
    val lone = origins.size == 1
    val slices = origins.map { PieChart.Slice(fill = Fill(colorOf(it.origin))) }
    val chart = rememberPieChart(
        sliceProvider = remember(slices) { PieChart.SliceProvider.series(slices) },
        spacing = if (lone) 0.dp else SLICE_GAP,
        outerSize = PieSize.Outer.fixed(PIE_SIZE),
        innerSize = PieSize.Inner.fixed(PIE_HOLE),
    )
    val model = remember(origins) {
        val values = origins.map { it.timeSpent.inWholeSeconds.toFloat() }
        PieChartModel.build(*(if (lone) values + values else values).toTypedArray())
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(PIE_SIZE)) {
            PieChartHost(chart, model, Modifier.size(PIE_SIZE))
            Text(
                resources.timeSpent(total),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(PIE_HOLE - 16.dp),
            )
        }
        Spacer(Modifier.width(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            origins.forEach { origin ->
                val share =
                    if (total.isPositive()) (origin.timeSpent / total * 100).roundToInt() else 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(colorOf(origin.origin), CircleShape)
                    )
                    Spacer(Modifier.width(8.dp))
                    // The name says the same, nothing more for screen readers
                    Icon(
                        painterResource(origin.origin.icon),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            stringResource(origin.origin.sourceRes),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${resources.timeSpent(origin.timeSpent)} · " +
                                    stringResource(R.string.percent, share),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private val CHART_HEIGHT = 180.dp
private val COLUMN_GAP = 4.dp

private val BAR_HEIGHT = 120.dp
private val ARTIST_GAP = 4.dp
private val CROWN_SIZE = 20.dp
private val CROWN_COLOR = Color(0xFFF4B400)
private val PICTURE_SIZE = 40.dp

/** Room above the tallest column for its picture and the crown. */
private val PICTURE_ROOM = PICTURE_SIZE + CROWN_SIZE + 8.dp

private val PIE_SIZE = 140.dp
private val PIE_HOLE = 96.dp
private val SLICE_GAP = 2.dp

/** Shortest share of a bar drawn, so a small value still shows. */
private const val MIN_BAR = 0.04f
