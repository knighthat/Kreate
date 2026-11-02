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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.knighthat.kreate3.R
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** How faint a slider is while it's off. */
private const val DISABLED_ALPHA = 0.38f

/** Loudness [LufsSlider] goes between, in LUFS, quietest first, and the one in the middle. */
private const val LUFS_MIN = -18f
private const val LUFS_MAX = -10f
private const val LUFS_MIDDLE = -14f

/** What [LufsSlider] stops at, in LUFS. */
private const val LUFS_STEP = 0.5f

/** The thin track of the sliders here. */
private val THIN_TRACK_HEIGHT = 4.dp

/** The ball with the number in it, larger while held; a pill when the number is wider. */
private val BALL_SIZE = 26.dp
private val HELD_BALL_SIZE = 34.dp

/** Room the ball's number keeps from its round ends. */
private val BALL_PADDING = 6.dp

/** [LufsSlider]'s ball at rest, as wide whatever the number, so it doesn't change as it moves. */
private val LUFS_BALL_WIDTH = 44.dp

/**
 * Room the thumb takes, the widest ball at rest ([LufsSlider]'s); a ball grown while held spills
 * out of it. The slider keeps half of it from each end of the track, the same for every slider
 * here so their tracks line up.
 */
private val THUMB_WIDTH = LUFS_BALL_WIDTH

/** Space between the track and the icons at its ends. */
private val ICON_GAP = 4.dp

/** A small sign over the corner of an icon, like the "+" of [SecondSlider]'s hourglasses. */
private val ICON_BADGE_SIZE = 12.dp

/** The mark of [LufsSlider]'s middle, across the track. */
private val MIDDLE_MARK_WIDTH = 2.dp
private val MIDDLE_MARK_HEIGHT = 12.dp

/**
 * A slider for the loudness songs are evened out to, from [LUFS_MIN] (quieter) at the start to
 * [LUFS_MAX] (louder) at the end, stopping every [LUFS_STEP]. A thin track fills from the middle
 * ([LUFS_MIDDLE], marked) to a pill of one width with the value in it, which grows while held;
 * "LUFS" sits under the middle of the track. [leadingIcon] and
 * [trailingIcon] stand at the ends, a quieter and a louder speaker by default.
 */
@Composable
fun LufsSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = { SliderIcon(R.drawable.volume_down) },
    trailingIcon: (@Composable () -> Unit)? = { SliderIcon(R.drawable.volume_up) },
) = LufsSlider(
    value, onValueChange, null, modifier, enabled, onValueChangeFinished, leadingIcon, trailingIcon,
)

/** The [LufsSlider] held or not as [held] says, or as the finger does while it's `null`. */
@Composable
private fun LufsSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    held: Boolean?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = { SliderIcon(R.drawable.volume_down) },
    trailingIcon: (@Composable () -> Unit)? = { SliderIcon(R.drawable.volume_up) },
) = BallSlider(
    value = value,
    // On a stop exactly, the slider's own snapping leaves float error
    onValueChange = { onValueChange((it / LUFS_STEP).roundToInt() * LUFS_STEP) },
    valueRange = LUFS_MIN..LUFS_MAX,
    // The stops between the two ends
    steps = ((LUFS_MAX - LUFS_MIN) / LUFS_STEP).roundToInt() - 1,
    number = ::lufsNumber,
    unit = { stringResource(R.string.lufs_symbol) },
    fromMiddle = true,
    held = held,
    modifier = modifier,
    enabled = enabled,
    onValueChangeFinished = onValueChangeFinished,
    leadingIcon = leadingIcon,
    trailingIcon = trailingIcon,
    ballWidth = LUFS_BALL_WIDTH,
)

/** [lufs] as a number, like "-14" or "-13.5", one decimal at most. */
private fun lufsNumber(lufs: Float): String {
    val tenths = (lufs * 10).roundToInt()
    return if (tenths % 10 == 0) (tenths / 10).toString() else "%.1f".format(tenths / 10f)
}

/**
 * A slider for a number of seconds from the start of [range] to its end, stopping at every whole
 * second: a thin track filled from the start to a ball with [number] of the seconds in it (the
 * seconds themselves by default), which grows while held; "seconds" sits under the middle of the
 * track. [leadingIcon] and [trailingIcon] stand at the ends, an hourglass with "-" and one with
 * "+" by default.
 */
@Composable
fun SecondSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    number: (Int) -> String = Int::toString,
    leadingIcon: (@Composable () -> Unit)? = {
        SliderIcon(R.drawable.hourglass_empty, R.drawable.remove)
    },
    trailingIcon: (@Composable () -> Unit)? = {
        SliderIcon(R.drawable.hourglass_empty, R.drawable.add)
    },
) = SecondSlider(
    value, onValueChange, range, null, modifier, enabled, onValueChangeFinished, number,
    leadingIcon, trailingIcon,
)

/** The [SecondSlider] held or not as [held] says, or as the finger does while it's `null`. */
@Composable
private fun SecondSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    held: Boolean?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    number: (Int) -> String = Int::toString,
    leadingIcon: (@Composable () -> Unit)? = {
        SliderIcon(R.drawable.hourglass_empty, R.drawable.remove)
    },
    trailingIcon: (@Composable () -> Unit)? = {
        SliderIcon(R.drawable.hourglass_empty, R.drawable.add)
    },
) = WholeSlider(
    value = value,
    onValueChange = onValueChange,
    range = range,
    number = number,
    unit = { pluralStringResource(R.plurals.unit_second, it) },
    held = held,
    modifier = modifier,
    enabled = enabled,
    onValueChangeFinished = onValueChangeFinished,
    leadingIcon = leadingIcon,
    trailingIcon = trailingIcon,
)

/**
 * A slider for a percentage from the start of [range] to its end, stopping at every whole
 * percent, laid out like [SecondSlider]: the percentage in the ball, "%" under the middle of the
 * track, [leadingIcon] and [trailingIcon] at the ends (none by default).
 */
@Composable
fun PercentSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) = PercentSlider(
    value, onValueChange, range, null, modifier, enabled, onValueChangeFinished, leadingIcon,
    trailingIcon,
)

/** The [PercentSlider] held or not as [held] says, or as the finger does while it's `null`. */
@Composable
private fun PercentSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    held: Boolean?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) = WholeSlider(
    value = value,
    onValueChange = onValueChange,
    range = range,
    number = Int::toString,
    unit = { stringResource(R.string.percent_sign) },
    held = held,
    modifier = modifier,
    enabled = enabled,
    onValueChangeFinished = onValueChangeFinished,
    leadingIcon = leadingIcon,
    trailingIcon = trailingIcon,
)

/**
 * A slider through [stops] stops in order, [value] being the one it's on (from `0`), laid out like
 * [SecondSlider] with an empty ball and nothing under the track: for values that have no number,
 * like a quality. [leadingIcon] and [trailingIcon] stand at the ends (none by default).
 */
@Composable
fun StopSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    stops: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) = StopSlider(
    value, onValueChange, stops, null, modifier, enabled, onValueChangeFinished, leadingIcon,
    trailingIcon,
)

/** The [StopSlider] held or not as [held] says, or as the finger does while it's `null`. */
@Composable
private fun StopSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    stops: Int,
    held: Boolean?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onValueChangeFinished: (() -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) = WholeSlider(
    value = value,
    onValueChange = onValueChange,
    // One stop alone still needs a range to slide over
    range = 0..(stops - 1).coerceAtLeast(1),
    number = null,
    unit = null,
    held = held,
    modifier = modifier,
    enabled = enabled && stops > 1,
    onValueChangeFinished = onValueChangeFinished,
    leadingIcon = leadingIcon,
    trailingIcon = trailingIcon,
)

/**
 * A [BallSlider] of the whole numbers of [range], filled from the start, with [number] of the
 * value in the ball and [unit] of it under the track, each left out while `null`.
 */
@Composable
private fun WholeSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    number: ((Int) -> String)?,
    unit: (@Composable (Int) -> String)?,
    held: Boolean?,
    modifier: Modifier,
    enabled: Boolean,
    onValueChangeFinished: (() -> Unit)?,
    leadingIcon: (@Composable () -> Unit)?,
    trailingIcon: (@Composable () -> Unit)?,
) = BallSlider(
    value = value.toFloat(),
    onValueChange = { onValueChange(it.roundToInt()) },
    valueRange = range.first.toFloat()..range.last.toFloat(),
    // The whole numbers between the two ends
    steps = (range.last - range.first - 1).coerceAtLeast(0),
    number = number?.let { { value: Float -> it(value.roundToInt()) } },
    unit = unit?.let { { value: Float -> it(value.roundToInt()) } },
    fromMiddle = false,
    held = held,
    modifier = modifier,
    enabled = enabled,
    onValueChangeFinished = onValueChangeFinished,
    leadingIcon = leadingIcon,
    trailingIcon = trailingIcon,
)

/**
 * An icon at an end of a slider, with [badge] small over its bottom corner when given, cut out of
 * the icon by the surface's color.
 */
@Composable
fun SliderIcon(@DrawableRes icon: Int, @DrawableRes badge: Int? = null) = Box {
    Icon(painterResource(icon), contentDescription = null)
    badge?.let {
        Icon(
            painter = painterResource(it),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 2.dp, y = 2.dp)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .size(ICON_BADGE_SIZE),
        )
    }
}

/**
 * The slider the ones here are made of: a thin track filled to a ball from the start, or from the
 * middle (marked) when [fromMiddle], with [leadingIcon] and [trailingIcon] at the ends. The ball
 * has [number] of the value in it and grows while held; [unit] of the value sits under the middle
 * of the track. Without them the ball is empty and nothing is under the track.
 *
 * @param ballWidth the ball's width at rest, held it grows as much as it does in height; `null` for
 * a circle that widens into a pill for a wide number
 * @param held whether it's drawn held, `null` to follow the finger
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BallSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    number: ((Float) -> String)?,
    unit: (@Composable (Float) -> String)?,
    fromMiddle: Boolean,
    held: Boolean?,
    modifier: Modifier,
    enabled: Boolean,
    onValueChangeFinished: (() -> Unit)?,
    leadingIcon: (@Composable () -> Unit)?,
    trailingIcon: (@Composable () -> Unit)?,
    ballWidth: Dp? = null,
) = Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = modifier.alpha(if (enabled) 1f else DISABLED_ALPHA),
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val dragged by interactionSource.collectIsDraggedAsState()
    val ball by animateDpAsState(
        targetValue = if (held ?: (pressed || dragged)) HELD_BALL_SIZE else BALL_SIZE,
        label = "ball",
    )

    // The icons beside the track, the unit under it all
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ICON_GAP),
    ) {
        leadingIcon?.invoke()
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            enabled = enabled,
            onValueChangeFinished = onValueChangeFinished,
            interactionSource = interactionSource,
            thumb = { state ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(THUMB_WIDTH, HELD_BALL_SIZE),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .wrapContentWidth(unbounded = true)
                            .height(ball)
                            .then(
                                if (ballWidth == null)
                                    Modifier.widthIn(min = ball)
                                else
                                    Modifier.width(ballWidth + ball - BALL_SIZE)
                            )
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .padding(horizontal = BALL_PADDING),
                    ) {
                        if (number != null)
                            Text(
                                text = number(state.value),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxLines = 1,
                            )
                    }
                }
            },
            track = { state -> ThinTrack(state, fromMiddle) },
            modifier = Modifier.weight(1f),
        )
        trailingIcon?.invoke()
    }
    if (unit != null)
        Text(
            text = unit(value),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
}

/**
 * The thin track of [BallSlider], round at both ends, filled from its start to the thumb, or from
 * its middle when [fromMiddle], with the middle marked across it then. The thumb covers the track,
 * so there's no gap around it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThinTrack(state: SliderState, fromMiddle: Boolean) {
    val active = MaterialTheme.colorScheme.primary
    val inactive = MaterialTheme.colorScheme.secondaryContainer
    val mark = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(MIDDLE_MARK_HEIGHT)
    ) {
        val range = state.valueRange
        val span = range.endInclusive - range.start
        val fraction = if (span == 0f) 0f else ((state.value - range.start) / span).coerceIn(0f, 1f)
        // The slider lays the track out from the thumb's middle at the start to it at the end
        val thumbX = fraction * size.width
        val middleX = size.width / 2
        val fromX = if (fromMiddle) middleX else 0f
        val height = THIN_TRACK_HEIGHT.toPx()
        val top = (size.height - height) / 2
        val corner = CornerRadius(height / 2)

        drawRoundRect(inactive, Offset(0f, top), Size(size.width, height), corner)
        drawRect(
            color = active,
            topLeft = Offset(min(fromX, thumbX), top),
            size = Size(abs(thumbX - fromX), height),
        )
        if (!fromMiddle) return@Canvas
        val markWidth = MIDDLE_MARK_WIDTH.toPx()
        drawRoundRect(
            color = mark,
            topLeft = Offset(middleX - markWidth / 2, 0f),
            size = Size(markWidth, size.height),
            cornerRadius = CornerRadius(markWidth / 2),
        )
    }
}

//<editor-fold defaultstate="collapsed" desc="Previews">
@ComponentPreviews
@Composable
private fun LufsSliderPreview() = PreviewTheme {
    Surface {
        var value by remember { mutableFloatStateOf(-14f) }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LufsSlider(value = value, onValueChange = { value = it })
            LufsSlider(value = -16.5f, onValueChange = {})
            // Held, the ball grown
            LufsSlider(value = -11.5f, onValueChange = {}, held = true)
            LufsSlider(value = -12f, onValueChange = {}, enabled = false)
        }
    }
}

@ComponentPreviews
@Composable
private fun SecondSliderPreview() = PreviewTheme {
    Surface {
        var value by remember { mutableIntStateOf(5) }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SecondSlider(value = value, onValueChange = { value = it }, range = 0..10)
            SecondSlider(value = 1, onValueChange = {}, range = 1..60)
            // Held, the ball grown
            SecondSlider(value = 30, onValueChange = {}, range = 1..60, held = true)
            SecondSlider(value = 3, onValueChange = {}, range = 0..5, enabled = false)
        }
    }
}

@ComponentPreviews
@Composable
private fun PercentSliderPreview() = PreviewTheme {
    Surface {
        var value by remember { mutableIntStateOf(100) }
        val small = @Composable {
            Icon(painterResource(R.drawable.format_size), null, Modifier.size(18.dp))
        }
        val large = @Composable {
            Icon(painterResource(R.drawable.format_size), null, Modifier.size(28.dp))
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            PercentSlider(
                value = value,
                onValueChange = { value = it },
                range = 80..200,
                leadingIcon = small,
                trailingIcon = large,
            )
            PercentSlider(value = 80, onValueChange = {}, range = 80..200)
            // Held, the ball grown
            PercentSlider(value = 150, onValueChange = {}, range = 80..200, held = true)
            PercentSlider(value = 120, onValueChange = {}, range = 80..200, enabled = false)
        }
    }
}

@ComponentPreviews
@Composable
private fun StopSliderPreview() = PreviewTheme {
    Surface {
        var value by remember { mutableIntStateOf(2) }
        val sd = @Composable { Icon(painterResource(R.drawable.sd), null) }
        val hd = @Composable { Icon(painterResource(R.drawable.hd), null) }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StopSlider(
                value = value,
                onValueChange = { value = it },
                stops = 4,
                leadingIcon = sd,
                trailingIcon = hd,
            )
            StopSlider(value = 0, onValueChange = {}, stops = 8)
            // Held, the ball grown
            StopSlider(value = 1, onValueChange = {}, stops = 3, held = true)
            StopSlider(value = 1, onValueChange = {}, stops = 3, enabled = false)
        }
    }
}
//</editor-fold>
