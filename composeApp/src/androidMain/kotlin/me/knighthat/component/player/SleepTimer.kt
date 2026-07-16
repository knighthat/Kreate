package me.knighthat.component.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationResult
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.kreate.compose.R
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import kotlinx.coroutines.launch
import me.knighthat.component.dialog.Dialog
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.hours

class SleepTimer private constructor(
    activeState: MutableState<Boolean>,
    timeState: MutableIntState,
    val timeRange: IntRange
): Dialog, MenuIcon, Descriptive {
    
    companion object {
        @Composable
        operator fun invoke( timeRange: IntRange = IntRange(0, 24.hours.inWholeSeconds.toInt()) ) = SleepTimer(
            remember { mutableStateOf( false ) },
            rememberSaveable { mutableIntStateOf(0) },
            timeRange
        )
    }

    override val iconId: Int = R.drawable.time
    override val messageId: Int = R.string.info_sleep_timer_delayed_at_end_of_song
    override val menuIconTitle: String
        @Composable
        get() = stringResource( R.string.sleep_timer )
    override val dialogTitle: String
        @Composable
        get() = stringResource( R.string.set_sleep_timer )

    var seconds: Int by timeState
    override var isActive: Boolean by activeState

    @Composable
    private fun Label(text: String, modifier: Modifier) {
        Text(
            text = text,
            modifier = modifier.pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    // FIXME: Empty to disable text selection
                })
            }
        )
    }

    private suspend fun Animatable<Float, AnimationVector1D>.fling(
        initialVelocity: Float,
        animationSpec: DecayAnimationSpec<Float>,
        adjustTarget: ((Float) -> Float)?,
        block: (Animatable<Float, AnimationVector1D>.() -> Unit)? = null,
    ): AnimationResult<Float, AnimationVector1D> {
        val targetValue = animationSpec.calculateTargetValue( value, initialVelocity )
        val adjustedTarget = adjustTarget?.invoke( targetValue )

        return if (adjustedTarget != null) {
            animateTo(
                targetValue = adjustedTarget,
                initialVelocity = initialVelocity,
                block = block
            )
        } else {
            animateDecay(
                initialVelocity = initialVelocity,
                animationSpec = animationSpec,
                block = block,
            )
        }
    }

    override fun onShortClick() = super.showDialog()

    @Composable
    override fun DialogBody() {
        val coroutineScope = rememberCoroutineScope()
        val numbersColumnHeight = 36.dp
        val halvedNumbersColumnHeight = numbersColumnHeight / 2
        val halvedNumbersColumnHeightPx =
            with(LocalDensity.current) { halvedNumbersColumnHeight.toPx() }

        fun animatedStateValue(offset: Float): Int =
            seconds - (offset / halvedNumbersColumnHeightPx).toInt()

        val animatedOffset = remember { Animatable(0f) }.apply {
            val offsetRange = remember(seconds, timeRange) {
                val value = seconds
                val first = -(timeRange.last - value) * halvedNumbersColumnHeightPx
                val last = -(timeRange.first - value) * halvedNumbersColumnHeightPx
                first..last
            }
            updateBounds(offsetRange.start, offsetRange.endInclusive)
        }
        val coercedAnimatedOffset = animatedOffset.value % halvedNumbersColumnHeightPx
        val animatedStateValue = animatedStateValue(animatedOffset.value)

        Column(
            modifier = modifier
                .wrapContentSize()
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { deltaY ->
                        coroutineScope.launch {
                            animatedOffset.snapTo(animatedOffset.value + deltaY)
                        }
                    },
                    onDragStopped = { velocity ->
                        coroutineScope.launch {
                            val endValue = animatedOffset.fling(
                                initialVelocity = velocity,
                                animationSpec = exponentialDecay(frictionMultiplier = 20f),
                                adjustTarget = { target ->
                                    val coercedTarget = target % halvedNumbersColumnHeightPx
                                    val coercedAnchors = listOf(
                                        -halvedNumbersColumnHeightPx,
                                        0f,
                                        halvedNumbersColumnHeightPx
                                    )
                                    val coercedPoint =
                                        coercedAnchors.minByOrNull { abs(it - coercedTarget) }!!
                                    val base =
                                        halvedNumbersColumnHeightPx * (target / halvedNumbersColumnHeightPx).toInt()
                                    coercedPoint + base
                                }
                            ).endState.value

                            seconds = animatedStateValue(endValue)
                            animatedOffset.snapTo(0f)
                        }
                    }
                )
        ) {
            val spacing = 4.dp

            val arrowColor = colorPalette().text
            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = "arrow_up",
                tint = arrowColor
            )

            Spacer(modifier = Modifier.height(spacing))


            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .offset { IntOffset(x = 0, y = coercedAnimatedOffset.roundToInt()) }
            ) {
                val baseLabelModifier = Modifier.align(Alignment.Center)
                ProvideTextStyle(typography().m) {
                    Label(
                        text = (animatedStateValue - 1).toString(),
                        modifier = baseLabelModifier
                            .offset(y = -halvedNumbersColumnHeight)
                            .alpha(coercedAnimatedOffset / halvedNumbersColumnHeightPx)
                    )
                    Label(
                        text = animatedStateValue.toString(),
                        modifier = baseLabelModifier
                            .alpha(1 - abs(coercedAnimatedOffset) / halvedNumbersColumnHeightPx)
                    )
                    Label(
                        text = (animatedStateValue + 1).toString(),
                        modifier = baseLabelModifier
                            .offset(y = halvedNumbersColumnHeight)
                            .alpha(-coercedAnimatedOffset / halvedNumbersColumnHeightPx)
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing))

            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = "arrow_down",
                tint = arrowColor
            )
        }
    }
}