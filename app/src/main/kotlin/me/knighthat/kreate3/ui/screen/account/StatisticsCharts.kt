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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
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
import me.knighthat.kreate3.ui.theme.chartColors
import me.knighthat.kreate3.ui.theme.onChart
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
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
 * steps with faint lines across, its unit written up its side. The first, the most listened to, wears a crown. Each column
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
        TimeAxisTitle(ticks, Modifier.align(Alignment.Bottom))
        Spacer(Modifier.width(4.dp))
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

/** Whether [ticks] count hours rather than minutes, a step of an hour or more. */
private fun inHours(ticks: List<Duration>) = ticks.getOrNull(1)?.let { it >= 1.hours } == true

/**
 * The unit of [ticks], minutes or hours, read bottom to top along [BAR_HEIGHT].
 */
@Composable
private fun TimeAxisTitle(ticks: List<Duration>, modifier: Modifier = Modifier) {
    val top = ticks.last()
    val title =
        if (inHours(ticks)) top.inWholeHours.toInt()
            .let { pluralStringResource(R.plurals.unit_hour, it) }
        else top.inWholeMinutes.toInt().let { pluralStringResource(R.plurals.unit_minute, it) }
    Box(contentAlignment = Alignment.Center, modifier = modifier.height(BAR_HEIGHT)) {
        Text(
            title.replaceFirstChar { it.titlecase() },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.layout { measurable, constraints ->
                // Measured lying down, as long as the axis is tall, then turned a quarter left
                val text = measurable.measure(Constraints(maxWidth = constraints.maxHeight))
                layout(text.height, text.width) {
                    text.placeWithLayer(
                        (text.height - text.width) / 2,
                        (text.width - text.height) / 2,
                    ) { rotationZ = -90f }
                }
            },
        )
    }
}

/**
 * The numbers of [ticks] up the left of a chart, each centered on the height its time takes of
 * [BAR_HEIGHT] against [top], from the bottom. [TimeAxisTitle] names their unit.
 */
@Composable
private fun TimeAxis(ticks: List<Duration>, top: Duration) {
    val inHours = inHours(ticks)
    Layout(
        content = {
            ticks.forEach { tick ->
                Text(
                    "${if (inHours) tick.inWholeHours else tick.inWholeMinutes}",
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
 * The share of the time listened of each origin as a pie, each slice with its percentage, and a
 * legend naming each origin and its time. An origin keeps its color whatever its rank.
 */
@Composable
internal fun SourcesPie(origins: List<OriginTime>) {
    if (origins.isEmpty()) return

    val resources = LocalResources.current
    val colors = MaterialTheme.chartColors
    fun colorOf(origin: Origin) = when (origin) {
        Origin.YOUTUBE -> colors.orange
        Origin.OPEN_SUBSONIC -> colors.blue
        Origin.LOCAL -> colors.aqua
    }

    fun onColorOf(origin: Origin) = colorOf(origin).onChart()

    val total = origins.fold(Duration.ZERO) { sum, it -> sum + it.timeSpent }
    val shares = origins.map {
        if (total.isPositive()) (it.timeSpent / total * 100).roundToInt() else 0
    }
    val labels = shares.map { resources.getString(R.string.percent, it) }

    val slices = origins.map { PieChart.Slice(fill = Fill(colorOf(it.origin))) }
    val chart = rememberPieChart(
        sliceProvider = remember(slices) { PieChart.SliceProvider.series(slices) },
        spacing = if (origins.size == 1) 0.dp else SLICE_GAP,
        outerSize = PieSize.Outer.fixed(PIE_SIZE),
    )

    // Vico's labels ellipsize what doesn't fit, these are left out instead
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
    val texts = remember(labels, labelStyle, colors) {
        origins.mapIndexed { i, it ->
            measurer.measure(labels[i], labelStyle.copy(color = onColorOf(it.origin)), maxLines = 1)
        }
    }
    val model = remember(origins) {
        PieChartModel.build(*origins.map { it.timeSpent.inWholeSeconds.toFloat() }.toTypedArray())
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        PieChartHost(
            chart,
            model,
            Modifier
                .size(PIE_SIZE)
                .drawWithContent {
                    drawContent()
                    drawSliceLabels(texts, origins.map { it.timeSpent.inWholeSeconds.toFloat() })
                },
        )
        Spacer(Modifier.width(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            origins.forEach { origin ->
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
                            resources.timeSpent(origin.timeSpent),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Draws each of [texts] in the slice of the pie its value of [values] takes, as the pie is drawn
 * from the top clockwise: across where it fits, else along the slice's middle, else not at all.
 */
private fun DrawScope.drawSliceLabels(texts: List<TextLayoutResult>, values: List<Float>) {
    val sum = values.sum()
    if (sum <= 0f) return
    val radius = size.minDimension / 2
    val center = this.center
    val margin = LABEL_MARGIN.toPx()
    var start = -90f
    texts.forEachIndexed { i, text ->
        val sweep = values[i] / sum * 360f
        val middle = start + sweep / 2
        // Turned along the middle, the text reads outward on the right half, inward on the left
        val along = if (middle > 90f && middle < 270f) middle + 180f else middle
        val place = sequenceOf(0.6f, 0.5f, 0.7f)
            .flatMap { at -> sequenceOf(at to 0f, at to along) }
            .firstOrNull { (at, turn) ->
                fitsSlice(text.size, radius * at, middle, turn, start, sweep, radius, margin)
            }
        if (place != null) {
            val (at, turn) = place
            val angle = Math.toRadians(middle.toDouble())
            val x = center.x + radius * at * cos(angle).toFloat()
            val y = center.y + radius * at * sin(angle).toFloat()
            rotate(turn, Offset(x, y)) {
                drawText(
                    text,
                    topLeft = Offset(x - text.size.width / 2f, y - text.size.height / 2f)
                )
            }
        }
        start += sweep
    }
}

/**
 * Whether a box of [box] centered [distance] from the pie's center at [middle] degrees, turned by
 * [turn] degrees, keeps [margin] inside the circle of [radius] and the slice from [start] degrees
 * over [sweep].
 */
private fun fitsSlice(
    box: IntSize,
    distance: Float,
    middle: Float,
    turn: Float,
    start: Float,
    sweep: Float,
    radius: Float,
    margin: Float,
): Boolean {
    val angle = Math.toRadians(middle.toDouble())
    val cx = distance * cos(angle)
    val cy = distance * sin(angle)
    val t = Math.toRadians(turn.toDouble())
    val startEdge = Math.toRadians(start.toDouble())
    val endEdge = Math.toRadians((start + sweep).toDouble())
    return listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1).all { (sx, sy) ->
        val dx = sx * box.width / 2.0
        val dy = sy * box.height / 2.0
        val x = cx + dx * cos(t) - dy * sin(t)
        val y = cy + dx * sin(t) + dy * cos(t)
        val inCircle = hypot(x, y) <= radius - margin
        // Past half the pie a corner can't cross out of the slice from where the label sits
        inCircle && (sweep >= 180f ||
                (cos(startEdge) * y - sin(startEdge) * x >= margin &&
                        x * sin(endEdge) - y * cos(endEdge) >= margin))
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
private val SLICE_GAP = 2.dp

/** Room kept between a slice's label and the slice's sides. */
private val LABEL_MARGIN = 3.dp

/** Shortest share of a bar drawn, so a small value still shows. */
private const val MIN_BAR = 0.04f
