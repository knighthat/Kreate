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

package me.knighthat.kreate3.utils

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * Draws a thin scrollbar on the right edge. It fades in when first shown, then fades out,
 * and shows again while [state] is scrolling.
 *
 * Place it **before** `verticalScroll(state)` so it is drawn over the visible area
 * and not over the whole content.
 *
 * @param state the same state given to `verticalScroll`
 * @param width thickness of the thumb
 * @param minThumbHeight keeps the thumb easy to see on very long content
 */
@Composable
fun Modifier.verticalScrollbar(
    state: ScrollState,
    width: Dp = 4.dp,
    minThumbHeight: Dp = 24.dp,
): Modifier {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(state) {
        // Shows itself once when it first appears, so the user knows the content scrolls
        alpha.animateTo(1f, tween(durationMillis = 150))

        snapshotFlow { state.isScrollInProgress }.collectLatest { scrolling ->
            if (scrolling)
                alpha.animateTo(1f, tween(durationMillis = 150))
            else {
                delay(800)
                alpha.animateTo(0f, tween(durationMillis = 500))
            }
        }
    }

    return drawWithContent {
        drawContent()

        // Nothing to scroll, or not measured yet
        if (state.maxValue <= 0 || state.maxValue == Int.MAX_VALUE || alpha.value <= 0f) return@drawWithContent

        val viewport = size.height
        val content = viewport + state.maxValue
        val thumbHeight = (viewport * viewport / content).coerceAtLeast(minThumbHeight.toPx())
        val thumbTop = (viewport - thumbHeight) * state.value / state.maxValue
        val thumbWidth = width.toPx()

        drawRoundRect(
            color = color,
            topLeft = Offset(size.width - thumbWidth, thumbTop),
            size = Size(thumbWidth, thumbHeight),
            cornerRadius = CornerRadius(thumbWidth / 2),
            alpha = alpha.value,
        )
    }
}
