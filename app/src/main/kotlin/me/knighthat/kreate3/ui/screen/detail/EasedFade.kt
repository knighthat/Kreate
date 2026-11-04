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

package me.knighthat.kreate3.ui.screen.detail

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.exp
import kotlin.math.roundToInt

/** Share of the height where [fadeCurve] is at [FADE_KNEE_VALUE], after it the curve takes off. */
private const val FADE_KNEE = 0.8f

/** How much of the color is there at [FADE_KNEE]. */
private const val FADE_KNEE_VALUE = 0.3f

/** How sharply the last part of [fadeCurve] rises. */
private const val FADE_SHARPNESS = 4f

private const val FADE_STEPS = 32

/**
 * How much of a fade is there at [t], 0 at the top to 1 at the bottom of what it covers: slowly up
 * to 30% at 80% of the height, then exponentially to the whole of it.
 */
internal fun fadeCurve(t: Float): Float =
    if (t <= FADE_KNEE) {
        FADE_KNEE_VALUE * t / FADE_KNEE
    } else {
        val u = ((t - FADE_KNEE) / (1f - FADE_KNEE)).coerceAtMost(1f)
        FADE_KNEE_VALUE + (1f - FADE_KNEE_VALUE) *
                (exp(FADE_SHARPNESS * u) - 1f) / (exp(FADE_SHARPNESS) - 1f)
    }

/**
 * [fadeCurve] with a tail that levels off instead of ending steeply: the same slow start to 30% at
 * 80%, then a smooth S to the whole of it that arrives flat. For a fade between two flat colors,
 * where the steep end of [fadeCurve] shows as an edge; over a picture it doesn't.
 */
internal fun softFadeCurve(t: Float): Float {
    if (t <= FADE_KNEE) return FADE_KNEE_VALUE * t / FADE_KNEE
    val width = 1f - FADE_KNEE
    val u = ((t - FADE_KNEE) / width).coerceAtMost(1f)
    // Cubic Hermite from (knee, 30%) leaving at the slope of the slow start, to (1, 100%) flat
    val startSlope = FADE_KNEE_VALUE / FADE_KNEE * width
    val u2 = u * u
    val u3 = u2 * u
    return (2 * u3 - 3 * u2 + 1) * FADE_KNEE_VALUE +
            (u3 - 2 * u2 + u) * startSlope +
            (-2 * u3 + 3 * u2)
}

/**
 * Draws a top-to-bottom fade of [color] following [fadeCurve], or [softFadeCurve] with [soft]:
 * see-through at the top, whole at the bottom, or the other way round with [reversed].
 *
 * It's drawn as [FADE_STEPS] pieces of two-stop gradients, each on whole pixels. One gradient
 * with that many stops is a texture on the GPU, and its last row can come out as the first
 * color: a one pixel line at the bottom while the header moves.
 */
internal fun DrawScope.drawEasedFade(
    color: Color,
    reversed: Boolean = false,
    soft: Boolean = false
) {
    fun alphaAt(t: Float): Float {
        val share = if (soft) softFadeCurve(t) else fadeCurve(t)
        return color.alpha * if (reversed) 1f - share else share
    }

    var top = 0f
    for (step in 0 until FADE_STEPS) {
        val bottom = if (step == FADE_STEPS - 1) size.height else
            ((step + 1) * size.height / FADE_STEPS).roundToInt().toFloat()
        if (bottom > top) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to color.copy(alpha = alphaAt(step / FADE_STEPS.toFloat())),
                    1f to color.copy(alpha = alphaAt((step + 1) / FADE_STEPS.toFloat())),
                    startY = top,
                    endY = bottom,
                ),
                topLeft = Offset(0f, top),
                size = Size(size.width, bottom - top),
            )
        }
        top = bottom
    }
}
