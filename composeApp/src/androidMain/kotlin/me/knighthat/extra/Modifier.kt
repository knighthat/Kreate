package me.knighthat.extra

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import it.fast4x.rimusic.ui.styling.shimmer
import me.knighthat.colorPalette

/**
 * A loading effect that goes from top left
 * to bottom right in 2000 millis (2s).
 */
fun Modifier.shimmerEffect(): Modifier = composed {
    var size by remember { mutableStateOf( IntSize.Zero ) }
    val transition = rememberInfiniteTransition( "infiniteTransition" )
    val startOffsetX by transition.animateFloat(
        initialValue = -2 * size.width.toFloat(),
        targetValue = 2 * size.width.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 2000,
                easing = FastOutLinearInEasing
            ),
        ),
        label = "offsetXAnimatedTransition"
    )

    background(
        brush = Brush.linearGradient(
            colors = listOf(
                colorPalette().background1,
                colorPalette().shimmer.copy( alpha = .3f ),
                colorPalette().background1
            ),
            start = Offset(startOffsetX, 0f),
            end = Offset(startOffsetX + size.width.toFloat(), size.height.toFloat())
        )
    ).onGloballyPositioned {
        size = it.size
    }
}

fun Modifier.vertical( enabled: Boolean = true ) =
    if ( enabled )
        layout { measurable, constraints ->
            val c: Constraints = constraints.copy( maxWidth = Int.MAX_VALUE )
            val placeable = measurable.measure( c )

            layout( placeable.height, placeable.width ) {
                placeable.place(
                    x = -(placeable.width / 2 - placeable.height / 2),
                    y = -(placeable.height / 2 - placeable.width / 2)
                )
            }
        }
    else this