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

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SharedTransitionScope.OverlayClip
import androidx.compose.animation.SharedTransitionScope.SharedContentConfig
import androidx.compose.animation.SharedTransitionScope.SharedContentState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import kotlin.math.abs

/**
 * Where a picture on one screen and the same picture on the next move from one to the other,
 * around the screens. `null` outside them, like in previews or the player, where nothing moves.
 */
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** How long screens take to change. */
private const val SCREEN_TRANSITION_MILLIS = 400

/** How long a shared picture takes to move from one screen to the other. */
private const val PICTURE_MOVE_MILLIS = 350

/** How long the rest of a screen whose picture moved takes to show, see [pictureFirst]. */
private const val CONTENTS_FADE_MILLIS = 200

/** How one screen changes into another: one fades out while the other fades in. */
internal fun screenTransform() =
    fadeIn(tween(SCREEN_TRANSITION_MILLIS, easing = FastOutSlowInEasing)) togetherWith
            fadeOut(tween(SCREEN_TRANSITION_MILLIS, easing = FastOutSlowInEasing))

/** How a shared picture moves, and how the screens around it change meanwhile. */
private fun <T> moveSpec(): FiniteAnimationSpec<T> =
    tween(PICTURE_MOVE_MILLIS, easing = FastOutSlowInEasing)

/**
 * The `NavEntry` metadata of a screen opened from a shared picture, like an artist's or album's
 * page from its card. Opening it, the picture moves into place while the screens fade, then the rest of
 * the screen shows ([rememberContentsAfterPicture]). Going back is a plain [screenTransform], the
 * picture stays with its screen (see `onlyAway` of [rememberSharedPicture]).
 */
internal fun pictureFirst(): Map<String, Any> =
    NavDisplay.transitionSpec { fadeIn(moveSpec()) togetherWith fadeOut(moveSpec()) }

/** Key of an artist's picture, on their cards and at the top of their page. */
internal fun artistPictureKey(artistId: String): String = "artist-picture:$artistId"

/** Key of an album's cover, on its cards and in the header of its page. */
internal fun albumPictureKey(albumId: String): String = "album-picture:$albumId"

/**
 * A picture shared with the screen opened or gone back to, see [rememberSharedPicture].
 *
 * @property modifier moves the picture from where it was on the other screen to where it is, and
 * clips it to its shape; the picture itself draws without one
 * @property overlay for what lies over the picture on its screen, like a bar: drawn over the
 * picture while it moves
 */
internal class SharedPicture(val modifier: Modifier, val overlay: Modifier)

/**
 * The picture with [key], also on the screen opened or gone back to: while the screens change,
 * it moves and grows from where it was there to where it is here, its corners turning from
 * [otherCorners] into [corners] as it goes, percents of its shorter side like
 * [RoundedCornerShape]'s. Nothing moves where there is no [LocalSharedTransitionScope].
 *
 * @param onlyAway whether it only moves away from its screen, as that screen is left, and never
 * back as it comes back: going back to it is then a plain fade
 */
@Composable
internal fun rememberSharedPicture(
    key: String,
    corners: Int,
    otherCorners: Int,
    onlyAway: Boolean = false,
): SharedPicture {
    val shape = remember(corners) { RoundedCornerShape(corners) }
    val shared = LocalSharedTransitionScope.current
        ?: return remember(shape) { SharedPicture(Modifier.clip(shape), Modifier) }
    val screen = LocalNavAnimatedContentScope.current
    val morph = remember(corners, otherCorners) { MorphingClip(otherCorners, corners) }

    return with(shared) {
        val state = rememberSharedContentState(key, rememberConfig(screen, onlyAway))
        SharedPicture(
            modifier = Modifier
                // Outside the shared element, the size it lands at
                .onSizeChanged { morph.end = it.toSize() }
                .sharedElement(
                    state,
                    screen,
                    boundsTransform = { _, _ -> moveSpec() },
                    clipInOverlayDuringTransition = morph,
                )
                // Read when drawn, so it changes in the frame the picture starts to move: the clip
                // in the overlay shapes it meanwhile, its own would cut that
                .graphicsLayer {
                    clip = true
                    val moving = isTransitionActive && state.isMatchFound
                    if (!moving) morph.start = null
                    this.shape = if (moving) RectangleShape else shape
                },
            overlay = Modifier.renderInSharedTransitionScopeOverlay(
                zIndexInOverlay = 1f,
                renderInOverlay = { isTransitionActive && state.isMatchFound },
            ),
        )
    }
}

/**
 * Like the other [rememberSharedPicture], for a picture of the same [shape] on both screens, like
 * an album's cover: it only moves and grows. The picture clips itself to [shape], its modifier
 * doesn't.
 */
@Composable
internal fun rememberSharedPicture(
    key: String,
    shape: Shape,
    onlyAway: Boolean = false,
): SharedPicture {
    val shared = LocalSharedTransitionScope.current
        ?: return remember { SharedPicture(Modifier, Modifier) }
    val screen = LocalNavAnimatedContentScope.current
    val clip = remember(shape) { ShapeClip(shape) }

    return with(shared) {
        val state = rememberSharedContentState(key, rememberConfig(screen, onlyAway))
        SharedPicture(
            modifier = Modifier.sharedElement(
                state,
                screen,
                boundsTransform = { _, _ -> moveSpec() },
                clipInOverlayDuringTransition = clip,
            ),
            overlay = Modifier.renderInSharedTransitionScopeOverlay(
                zIndexInOverlay = 1f,
                renderInOverlay = { isTransitionActive && state.isMatchFound },
            ),
        )
    }
}

/** Turns the match off while [screen] comes back, when the picture moves [onlyAway]. */
@Composable
private fun rememberConfig(screen: AnimatedVisibilityScope, onlyAway: Boolean) =
    remember(screen, onlyAway) {
        object : SharedContentConfig {
            // Coming back, its screen enters from PreEnter; leaving, it is Visible
            override val SharedContentState.isEnabled: Boolean
                get() = !onlyAway || screen.transition.currentState == EnterExitState.Visible
        }
    }

/**
 * The rest of a screen of [pictureFirst], see [rememberContentsAfterPicture].
 *
 * @property modifier fades what it is on in once the picture landed; leaving the screen, that goes
 * with it
 * @property picture for a picture inside what [modifier] fades, like a cover in a list: it stays
 * drawn over the screen until the rest shows, instead of fading in again with it
 */
internal class ContentsAfterPicture(val modifier: Modifier, val picture: Modifier)

/** For the parts of a screen of [pictureFirst] other than its picture, see [ContentsAfterPicture]. */
@Composable
internal fun rememberContentsAfterPicture(): ContentsAfterPicture {
    val shared = LocalSharedTransitionScope.current
        ?: return remember { ContentsAfterPicture(Modifier, Modifier) }
    val alpha = LocalNavAnimatedContentScope.current.transition.animateFloat(
        transitionSpec = { tween(CONTENTS_FADE_MILLIS, delayMillis = PICTURE_MOVE_MILLIS) },
        label = "contents after picture",
    ) { if (it == EnterExitState.PreEnter) 0f else 1f }
    return remember(alpha, shared) {
        ContentsAfterPicture(
            modifier = Modifier.graphicsLayer { this.alpha = alpha.value },
            picture = with(shared) {
                Modifier.renderInSharedTransitionScopeOverlay(renderInOverlay = { alpha.value < 1f })
            },
        )
    }
}

/** Clips a moving picture to [shape], at the size it has at each moment. */
private class ShapeClip(private val shape: Shape) : OverlayClip {

    private val path = Path()

    override fun getClipPath(
        sharedContentState: SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path {
        path.reset()
        path.addOutline(shape.createOutline(bounds.size, layoutDirection, density))
        path.translate(bounds.topLeft)
        return path
    }
}

/**
 * Rounds a moving picture's corners from [from] to [to] percent of its shorter side, as far as
 * it got from where it started to the size it lands at, [end].
 */
private class MorphingClip(private val from: Int, private val to: Int) : OverlayClip {

    /** Where the picture was when it started to move, `null` while it doesn't. */
    var start: Rect? = null
    var end: Size = Size.Zero

    private val path = Path()

    override fun getClipPath(
        sharedContentState: SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path {
        val start = start ?: bounds.also { start = it }
        val percent = lerp(from.toFloat(), to.toFloat(), progress(start.size, bounds.size))
        val radius = bounds.minDimension * percent / 100f
        path.reset()
        path.addRoundRect(RoundRect(bounds, CornerRadius(radius)))
        return path
    }

    /** How far from [start] to [end] the picture is at [now], by the side that changes most. */
    private fun progress(start: Size, now: Size): Float {
        val width = end.width - start.width
        val height = end.height - start.height
        val fraction = when {
            abs(width) >= abs(height) && width != 0f -> (now.width - start.width) / width
            height != 0f -> (now.height - start.height) / height
            else -> 1f
        }
        return fraction.coerceIn(0f, 1f)
    }
}
