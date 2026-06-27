package app.kreate.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs


/**
 * Calculates the layout width required to render the largest number in a given list
 * using the provided [TextStyle].
 *
 * This function performs the measurement off-screen without rendering a Composable.
 * It identifies the maximum numerical value, measures its pixel width based on the
 * font metrics of the [textStyle], and converts the result into [Dp].
 *
 * @param numbers The list of integers to evaluate. Must not be empty.
 * @param textStyle The styling (font size, family, weight, etc.) applied to the text.
 * @return The width in [Dp] required to display the largest number without clipping.
 * Returns `0.dp` if the list is empty.
 *
 * @throws IllegalArgumentException if [numbers] is empty.
 *
 * @note If using a proportional (non-monospace) font, the numerically largest number
 * might not always be the visually widest string (e.g., "1111" vs "888"). If strict
 * visual width maximums are required for varying digit lengths, consider forcing a
 * monospace font family via the [textStyle].
 */
@Composable
private fun <T> rememberMaxNumberWidth(
    numbers: List<T>,
    textStyle: TextStyle,
    density: Density,
    transform: (T) -> String
): Dp {
    require( numbers.isNotEmpty() )

    val textMeasurer = rememberTextMeasurer()
    val maxNumber = remember( numbers ) {
        // Add another character to get more space
        numbers.map( transform ).maxBy( CharSequence::length ) + "0"
    }
    val textLayoutResult = remember( maxNumber, textStyle ) {
        textMeasurer.measure(
            text = maxNumber,
            style = textStyle,
            maxLines = 1
        )
    }

    return remember( textLayoutResult ) {
        with( density ) { textLayoutResult.size.width.toDp() }
    }
}

/**
 * A scrollable, infinite looping wheel picker designed for numerical selections.
 * * Displays a vertical wheel where exactly three items are visible at a time. It features
 * automatic center-snapping, arrow-driven controls, and a smooth opacity fade that reduces
 * non-centered items to 50% visibility.
 *
 * ### Behavior:
 * - **Bi-directional Infinite Scroll:** The list seamlessly wraps around in both directions.
 * It initializes at a calculated midpoint on load so the user can swipe up or down infinitely
 * from the get-go without hitting boundaries.
 * - **Dynamic Sizing:** The overall widget height is calculated dynamically based on the
 * provided [textStyle] font size to prevent text clipping and scale with system font settings.
 *
 * @param numbers The list of integers to display in the wheel.
 * @param startIndex The initial index within the list that should be centered on first load.
 * @param textStyle The typography style applied to the numbers, which also dictates item height,
 *  and color of selected number.
 * @param onValueChange Callback invoked with the new integer when a value settles in the middle position.
 * @param modifier The modifier to be applied to the outer layout container.
 * @param color For unselected values
 */
@Composable
fun <T> InfinitePicker(
    numbers: List<T>,
    startIndex: Int,
    textStyle: TextStyle,
    onValueChange: (T) -> Unit,
    isScrollingState: MutableState<Boolean>,
    modifier: Modifier = Modifier,
    transform: (T) -> String = { it.toString() },
    color: Color = Color.Unspecified,
    density: Density = LocalDensity.current
) {
    val listSize = numbers.size
    val midPoint = Int.MAX_VALUE / 2

    val coroutineScope = rememberCoroutineScope()
    // Convert text size to DP + padding to dynamic calculate item height
    val itemHeightDp = with(density) { textStyle.fontSize.toDp() } + 12.dp
    // Tracks the internal layout center to calculate alpha shifts
    var containerCenterY by rememberSaveable { mutableFloatStateOf(0f) }
    // Keep the rendered width consistent by providing width to render the largest number
    val maxWidth = rememberMaxNumberWidth( numbers, textStyle, density, transform )
    // Start at [startIndex] item, but offset it backwards by 1 full item height.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = midPoint - (midPoint % listSize) + startIndex,
        // Calculate the exact height of 1 item in pixels
        initialFirstVisibleItemScrollOffset = -(with(density) { itemHeightDp.roundToPx() }),
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        // The Wheel
        LazyColumn(
            state = listState,
            // Snap behavior ensuring items lock into the exact center
            flingBehavior = rememberSnapFlingBehavior( listState ),
            modifier = Modifier.height( itemHeightDp * 3 )  // Exactly 3 items visible
                               .wrapContentWidth()
                               .onGloballyPositioned { coordinates ->
                                   containerCenterY = coordinates.size.height / 2f
                               },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(Int.MAX_VALUE) { index ->
                val actualIndex = index % listSize
                val number = numbers[actualIndex]
                // Position trackers
                var itemCenterY by remember { mutableFloatStateOf(0f) }
                val distanceFromCenter = abs(itemCenterY - containerCenterY)
                val maxDistance = with(density) { itemHeightDp.toPx() }
                // Calculate animation progress (0f = absolute center, 1f = edge boundary)
                val transitionProgress = if (containerCenterY > 0f && maxDistance > 0f) {
                    (distanceFromCenter / maxDistance).coerceIn(0f, 1f)
                } else {
                    1f
                }
                val animatedColor = lerp(
                    start = textStyle.color,
                    stop = color,
                    fraction = transitionProgress
                )
                // Smooth dynamic scale (1.0f center down to 0.7f edges)
                val targetScale = 1f - (transitionProgress * 0.3f)
                val animatedScale by animateFloatAsState(targetValue = targetScale, label = "scale")

                Box(
                    modifier = Modifier.width( maxWidth )
                                       .height( itemHeightDp )
                                       .onGloballyPositioned { coords ->
                                           // Dynamically track this item's center relative to the LazyColumn
                                           itemCenterY = coords.positionInParent().y + (coords.size.height / 2f)
                                       }
                                       .scale( animatedScale )
                                       .clickable(
                                           interactionSource = remember { MutableInteractionSource() },
                                           indication = null // Disables the standard ripple effect for a cleaner wheel feel
                                       ) {
                                           coroutineScope.launch {
                                               // Calculate item height in pixels
                                               val itemHeightPx = with(density) { itemHeightDp.roundToPx() }

                                               // By scrolling with a negative offset of exactly 1 item height,
                                               // the target item aligns perfectly with the middle slot (slot index 1)
                                               listState.animateScrollToItem(
                                                   index = index,
                                                   scrollOffset = -itemHeightPx
                                               )
                                           }
                                       },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = transform( number ),
                        fontSize = textStyle.fontSize,
                        color = animatedColor,
                        style = textStyle
                    )
                }
            }
        }
    }

    // Side effect to always correct position to middle item
    LaunchedEffect( listState.isScrollInProgress ) {
        if( listState.isScrollInProgress ) {
            isScrollingState.value = true
            return@LaunchedEffect
        }

        val layoutInfo = listState.layoutInfo
        // Find the item closest to the center of the viewport
        val closestItem = layoutInfo.visibleItemsInfo.minByOrNull { item ->
            val itemCenter = item.offset + (item.size / 2f)
            abs(itemCenter - containerCenterY)
        }

        closestItem?.let {
            val actualIndex = it.index % listSize
            onValueChange( numbers[actualIndex] )
        }

        isScrollingState.value = false
    }
}