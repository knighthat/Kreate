package it.fast4x.rimusic.utils

import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import app.kreate.android.R
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.ColorPaletteMode
import it.fast4x.rimusic.enums.PauseBetweenSongs
import it.fast4x.rimusic.enums.PlayerTimelineType
import it.fast4x.rimusic.models.ui.UiMedia
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.ProgressPercentage
import it.fast4x.rimusic.ui.components.SeekBar
import it.fast4x.rimusic.ui.components.SeekBarAudioWaves
import it.fast4x.rimusic.ui.components.SeekBarColored
import it.fast4x.rimusic.ui.components.SeekBarCustom
import it.fast4x.rimusic.ui.components.SeekBarThin
import it.fast4x.rimusic.ui.components.SeekBarWaved
import it.fast4x.rimusic.ui.styling.collapsedPlayerProgressBar
import it.fast4x.rimusic.ui.styling.favoritesIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val DURATION_INDICATOR_HEIGHT = 20

@OptIn(UnstableApi::class)
@Composable
fun GetSeekBar(
    position: Long,
    duration: Long,
    mediaId: String,
    media: UiMedia
    ) {
    val binder = LocalPlayerServiceBinder.current
    binder?.player ?: return
    val playerTimelineType by rememberPreference(playerTimelineTypeKey, PlayerTimelineType.FakeAudioBar)
    var scrubbingPosition by remember(mediaId) {
        mutableStateOf<Long?>(null)
    }
    var transparentbar by rememberPreference(transparentbarKey, true)
    val scope = rememberCoroutineScope()
    val animatedPosition = remember { Animatable(position.toFloat()) }
    var isSeeking by remember { mutableStateOf(false) }
    val showRemainingSongTime by rememberPreference(showRemainingSongTimeKey, true)

    val compositionLaunched = isCompositionLaunched()
    LaunchedEffect(mediaId) {
        if (compositionLaunched) animatedPosition.animateTo(0f)
    }
    val colorPaletteMode by rememberPreference(colorPaletteModeKey, ColorPaletteMode.Dark)
    LaunchedEffect(position) {
        if (!isSeeking && !animatedPosition.isRunning)
            animatedPosition.animateTo(
                position.toFloat(), tween(
                    durationMillis = 1000,
                    easing = LinearEasing
                )
            )
    }
    val textoutline by rememberPreference(textoutlineKey, false)

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
                range = 0f..media.duration.toFloat(),
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

                    if (media.duration != C.TIME_UNSET) {
                        //isSeeking = true
                        scope.launch {
                            animatedPosition.snapTo(
                                animatedPosition.value.plus(delta)
                                    .coerceIn(0f, media.duration.toFloat())
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

    Spacer(
        modifier = Modifier
            .height(8.dp)
    )

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 10.dp)
            .fillMaxWidth()
    ) {
        Icon(
            painter = painterResource( R.drawable.play_forward ),
            tint = colorPalette().favoritesIcon,
            contentDescription = "Rewind 5 seconds",
            modifier = Modifier.rotate( 180f )
                               .size( DURATION_INDICATOR_HEIGHT.dp )
                               .align( Alignment.CenterVertically )
                               .pointerInput( position ) {
                                   detectTapGestures(
                                       onTap = {
                                           binder.player.seekTo( position - 5000 )
                                       },
                                       onDoubleTap = {
                                           binder.player.seekTo( position - 10_000 )
                                       },
                                       onLongPress = {
                                           binder.player.seekTo( position - 30_000 )
                                       }
                                   )
                               }
        )

        Spacer( Modifier.width( 5.dp ) )

        val outlineColor =
            if ( colorPaletteMode == ColorPaletteMode.Light || (colorPaletteMode == ColorPaletteMode.System && !isSystemInDarkTheme()) )
                Color.White.copy( 0.5f )
            else if( !textoutline )
                Color.Transparent
            else
                Color.Black

        // Scrubbing position
        Box(
            modifier = Modifier.weight( 1f )
                               .height( DURATION_INDICATOR_HEIGHT.dp ),
            contentAlignment = Alignment.CenterStart
        ) {
            val toDisplay by remember( position ) {
                derivedStateOf { formatAsDuration( scrubbingPosition ?: position ) }
            }

            // Main text
            BasicText(
                text = toDisplay,
                style = typography().xxs.semiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(false),
                    onClick = {binder.player.seekTo(position - 5000)}
                )
            )

            // Outline (if applicable)
            BasicText(
                text = toDisplay,
                style = typography().xxs
                                    .semiBold
                                    .merge(
                                        TextStyle(
                                            drawStyle = Stroke(width = 1.0f, join = StrokeJoin.Round),
                                            color = outlineColor
                                        )
                                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Remaining duration
        Box(
            modifier = Modifier.weight( 1f )
                               .height( DURATION_INDICATOR_HEIGHT.dp ),
            contentAlignment = Alignment.Center
        ) {
            val positionAndDuration by binder.player.positionAndDurationState()
            val timeRemaining by remember { derivedStateOf {
                positionAndDuration.second - positionAndDuration.first
            } }
            var isPaused by remember { mutableStateOf( false ) }

            val pauseBetweenSongs by rememberPreference( pauseBetweenSongsKey, PauseBetweenSongs.`0` )
            if( pauseBetweenSongs != PauseBetweenSongs.`0` )
                LaunchedEffect( timeRemaining ) {
                    if ( timeRemaining < 500 ) {
                        isPaused = true
                        binder.player.pause()
                        delay( pauseBetweenSongs.asMillis )
                        binder.player.play()
                        isPaused = false
                    }
                }

            if( isPaused ) return@Box

            val toDisplay by remember {
                derivedStateOf { formatAsDuration( timeRemaining ) }
            }

            // Main text
            BasicText(
                text = toDisplay,
                style = typography().xxs.semiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(false),
                    onClick = {binder.player.seekTo(position - 5000)}
                )
            )

            // Outline (if applicable)
            BasicText(
                text = toDisplay,
                style = typography().xxs
                                    .semiBold
                                    .merge(
                                        TextStyle(
                                            drawStyle = Stroke(width = 1.0f, join = StrokeJoin.Round),
                                            color = outlineColor
                                        )
                                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Song's duration
        Box(
            modifier = Modifier.weight( 1f )
                               .height( DURATION_INDICATOR_HEIGHT.dp ),
            contentAlignment = Alignment.CenterEnd
        ) {
            val toDisplay = remember( duration ) {
                if( duration <= 0 ) "--:--" else formatAsDuration( duration )
            }

            // Main text
            BasicText(
                text = toDisplay,
                style = typography().xxs.semiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(false),
                    onClick = {binder.player.seekTo(position - 5000)}
                )
            )

            // Outline (if applicable)
            BasicText(
                text = toDisplay,
                style = typography().xxs
                                    .semiBold
                                    .merge(
                                        TextStyle(
                                            drawStyle = Stroke(width = 1.0f, join = StrokeJoin.Round),
                                            color = outlineColor
                                        )
                                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer( Modifier.width( 5.dp ) )

        Icon(
            painter = painterResource( R.drawable.play_forward ),
            tint = colorPalette().favoritesIcon,
            contentDescription = "Forward 5 seconds",
            modifier = Modifier.size( DURATION_INDICATOR_HEIGHT.dp )
                               .pointerInput( position ) {
                                   detectTapGestures(
                                       onTap = {
                                           binder.player.seekTo( position + 5000 )
                                       },
                                       onDoubleTap = {
                                           binder.player.seekTo( position + 10_000 )
                                       },
                                       onLongPress = {
                                           binder.player.seekTo( position + 30_000 )
                                       }
                                   )
                               }
        )
    }
}