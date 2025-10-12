package it.fast4x.rimusic.utils

import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import app.kreate.android.Preferences
import app.kreate.android.themed.rimusic.screen.player.timeline.DurationIndicator
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.PlayerTimelineType
import it.fast4x.rimusic.ui.components.ProgressPercentage
import it.fast4x.rimusic.ui.components.SeekBar
import it.fast4x.rimusic.ui.components.SeekBarAudioWaves
import it.fast4x.rimusic.ui.components.SeekBarColored
import it.fast4x.rimusic.ui.components.SeekBarCustom
import it.fast4x.rimusic.ui.components.SeekBarThin
import it.fast4x.rimusic.ui.components.SeekBarWaved
import it.fast4x.rimusic.ui.styling.collapsedPlayerProgressBar
import kotlinx.coroutines.launch

const val DURATION_INDICATOR_HEIGHT = 20

@OptIn(UnstableApi::class)
@Composable
fun GetSeekBar(
    mediaItem: MediaItem,
    positionAndDuration: Pair<Long, Long>
) {
    val binder = LocalPlayerServiceBinder.current
    binder?.player ?: return
    val playerTimelineType by Preferences.PLAYER_TIMELINE_TYPE
    var scrubbingPosition by remember( mediaItem.mediaId ) {
        mutableStateOf<Long?>(null)
    }
    var transparentbar by Preferences.TRANSPARENT_TIMELINE
    val scope = rememberCoroutineScope()
    val (position, duration) = positionAndDuration
    val animatedPosition = remember { Animatable(position.toFloat()) }
    var isSeeking by remember { mutableStateOf(false) }

    val compositionLaunched = isCompositionLaunched()
    LaunchedEffect( mediaItem.mediaId ) {
        if (compositionLaunched) animatedPosition.animateTo(0f)
    }
    LaunchedEffect(position) {
        if (!isSeeking && !animatedPosition.isRunning)
            animatedPosition.animateTo(
                position.toFloat(), tween(
                    durationMillis = 1000,
                    easing = LinearEasing
                )
            )
    }

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .fillMaxWidth()
    ) {

        if (duration == C.TIME_UNSET)
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = colorPalette().collapsedPlayerProgressBar
            )

        if (playerTimelineType != PlayerTimelineType.Default
            && playerTimelineType != PlayerTimelineType.Wavy
            && playerTimelineType != PlayerTimelineType.FakeAudioBar
            && playerTimelineType != PlayerTimelineType.ThinBar
            && playerTimelineType != PlayerTimelineType.ColoredBar
            )
            SeekBarCustom(
                type = playerTimelineType,
                value = scrubbingPosition ?: position,
                minimumValue = 0,
                maximumValue = duration,
                onDragStart = {
                    scrubbingPosition = it
                },
                onDrag = { delta ->
                    scrubbingPosition = if (duration != C.TIME_UNSET) {
                        scrubbingPosition?.plus(delta)?.coerceIn(0, duration)
                    } else {
                        null
                    }
                },
                onDragEnd = {
                    scrubbingPosition?.let(binder.player::seekTo)
                    scrubbingPosition = null
                },
                color = colorPalette().collapsedPlayerProgressBar,
                backgroundColor = if (transparentbar) Color.Transparent else colorPalette().textSecondary,
                shape = RoundedCornerShape(8.dp),
                //modifier = Modifier.pulsatingEffect(currentValue = scrubbingPosition?.toFloat() ?: position.toFloat(), isVisible = true)
            )

        if (playerTimelineType == PlayerTimelineType.Default)
            SeekBar(
                value = scrubbingPosition ?: position,
                minimumValue = 0,
                maximumValue = duration,
                onDragStart = {
                    scrubbingPosition = it
                },
                onDrag = { delta ->
                    scrubbingPosition = if (duration != C.TIME_UNSET) {
                        scrubbingPosition?.plus(delta)?.coerceIn(0, duration)
                    } else {
                        null
                    }
                },
                onDragEnd = {
                    scrubbingPosition?.let(binder.player::seekTo)
                    scrubbingPosition = null
                },
                color = colorPalette().collapsedPlayerProgressBar,
                backgroundColor = if (transparentbar) Color.Transparent else colorPalette().textSecondary,
                shape = RoundedCornerShape(8.dp),
                //modifier = Modifier.pulsatingEffect(currentValue = scrubbingPosition?.toFloat() ?: position.toFloat(), isVisible = true)
            )

        if (playerTimelineType == PlayerTimelineType.ThinBar)
            SeekBarThin(
                value = scrubbingPosition ?: position,
                minimumValue = 0,
                maximumValue = duration,
                onDragStart = {
                    scrubbingPosition = it
                },
                onDrag = { delta ->
                    scrubbingPosition = if (duration != C.TIME_UNSET) {
                        scrubbingPosition?.plus(delta)?.coerceIn(0, duration)
                    } else {
                        null
                    }
                },
                onDragEnd = {
                    scrubbingPosition?.let(binder.player::seekTo)
                    scrubbingPosition = null
                },
                color = colorPalette().collapsedPlayerProgressBar,
                backgroundColor = if (transparentbar) Color.Transparent else colorPalette().textSecondary,
                shape = RoundedCornerShape(8.dp),
                //modifier = Modifier.pulsatingEffect(currentValue = scrubbingPosition?.toFloat() ?: position.toFloat(), isVisible = true)
            )

        if (playerTimelineType == PlayerTimelineType.Wavy) {
            SeekBarWaved(
                position = { animatedPosition.value },
                range = 0f..duration.toFloat(),
                onSeekStarted = {
                    scrubbingPosition = it.toLong()

                    //isSeeking = true
                    scope.launch {
                        animatedPosition.animateTo(it)
                    }

                },
                onSeek = { delta ->
                    scrubbingPosition = if (duration != C.TIME_UNSET) {
                        scrubbingPosition?.plus(delta)?.coerceIn(0F, duration.toFloat())
                            ?.toLong()
                    } else {
                        null
                    }

                    if (duration != C.TIME_UNSET) {
                        //isSeeking = true
                        scope.launch {
                            animatedPosition.snapTo(
                                animatedPosition.value.plus(delta)
                                    .coerceIn(0f, duration.toFloat())
                            )
                        }
                    }

                },
                onSeekFinished = {
                    scrubbingPosition?.let(binder.player::seekTo)
                    scrubbingPosition = null
                    /*
                isSeeking = false
                animatedPosition.let {
                    binder.player.seekTo(it.targetValue.toLong())
                }
                 */
                },
                color = colorPalette().collapsedPlayerProgressBar,
                isActive = binder.player.isPlaying,
                backgroundColor = if (transparentbar) Color.Transparent else colorPalette().textSecondary,
                shape = RoundedCornerShape(8.dp),
                //modifier = Modifier.pulsatingEffect(currentValue = scrubbingPosition?.toFloat() ?: position.toFloat(), isVisible = true)
            )
        }

        if (playerTimelineType == PlayerTimelineType.FakeAudioBar)
            SeekBarAudioWaves(
                progressPercentage = ProgressPercentage((position.toFloat() / duration.toFloat()).coerceIn(0f,1f)),
                playedColor = colorPalette().accent,
                notPlayedColor = if (transparentbar) Color.Transparent else colorPalette().textSecondary,
                waveInteraction = {
                    scrubbingPosition = (it.value * duration.toFloat()).toLong()
                    binder.player.seekTo(scrubbingPosition!!)
                    scrubbingPosition = null
                },
                modifier = Modifier
                    .height(40.dp)
                    //.pulsatingEffect(currentValue = position.toFloat() / duration.toFloat(), isVisible = true)
            )


        if (playerTimelineType == PlayerTimelineType.ColoredBar)
            SeekBarColored(
                value = scrubbingPosition ?: position,
                minimumValue = 0,
                maximumValue = duration,
                onDragStart = {
                    scrubbingPosition = it
                },
                onDrag = { delta ->
                    scrubbingPosition = if (duration != C.TIME_UNSET) {
                        scrubbingPosition?.plus(delta)?.coerceIn(0, duration)
                    } else {
                        null
                    }
                },
                onDragEnd = {
                    scrubbingPosition?.let(binder.player::seekTo)
                    scrubbingPosition = null
                },
                color = colorPalette().collapsedPlayerProgressBar,
                backgroundColor = colorPalette().textSecondary,
                shape = RoundedCornerShape(8.dp)
            )


    }

    Spacer( modifier = Modifier.height( 8.dp ) )

    DurationIndicator( binder.player, scrubbingPosition, position, duration )
}