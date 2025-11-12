package app.kreate.android.themed.rimusic.screen.player.timeline

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.util.readableText
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.ColorPaletteMode
import it.fast4x.rimusic.enums.PauseBetweenSongs
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.styling.favoritesIcon
import it.fast4x.rimusic.utils.DURATION_INDICATOR_HEIGHT
import it.fast4x.rimusic.utils.positionAndDurationState
import it.fast4x.rimusic.utils.rememberPreference
import it.fast4x.rimusic.utils.semiBold
import kotlinx.coroutines.delay
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Adjust timeline based on provided values.
 *
 * @param operation of [Long] to get desired position (either [Long.plus] or [Long.minus])
 * @param valueSelector takes a value between position and [comparedValue] to ensure position isn't outside of allowed range
 * @param comparedValue either end of position, this value sets the limit for calculated position
 */
@UnstableApi
@Composable
private fun RowScope.SkipTimeButton(
    player: Player,
    position: Long,
    operation: Long.(Long) -> Long,
    valueSelector: (Long, Long) -> Long,
    comparedValue: Long,
    contentDescription: String,
    onClickLabel: String,
    onLongClickLabel: String,
    modifier: Modifier = Modifier,
    tapAdjustment: Long = 5_000L,
    doubleTapAdjustment: Long = 10_000L,
    longTapAdjustment: Long = 30_000L
) {
    if( !Preferences.PLAYER_SHOW_SEEK_BUTTONS.value ) return

    fun seekTo( adjustment: Long ) {
        val adjustedPosition = position.operation( adjustment )
        val newPosition = valueSelector( adjustedPosition, comparedValue )
        player.seekTo( newPosition )
    }

    Icon(
        painter = painterResource( R.drawable.play_forward ),
        tint = colorPalette().favoritesIcon,
        contentDescription = contentDescription,
        modifier = modifier.size( DURATION_INDICATOR_HEIGHT.dp )
                           .align( Alignment.CenterVertically )
                           .combinedClickable(
                               interactionSource = remember { MutableInteractionSource() },
                               indication = null,
                               role = Role.Button,
                               onClickLabel = onClickLabel,
                               onClick = { seekTo(tapAdjustment) },
                               onDoubleClick = { seekTo(doubleTapAdjustment) },
                               onLongClickLabel = onLongClickLabel,
                               onLongClick = { seekTo(longTapAdjustment) }
                           )
    )
}

@Composable
private fun outlineColorState(): State<Color> {
    val colorPaletteMode by Preferences.THEME_MODE
    val textOutline by Preferences.TEXT_OUTLINE
    val isDarkTheme = isSystemInDarkTheme()

    return remember {
        derivedStateOf {
            if ( colorPaletteMode == ColorPaletteMode.Light || (colorPaletteMode == ColorPaletteMode.System && !isDarkTheme) )
                Color.White.copy( 0.5f )
            else if( !textOutline )
                Color.Transparent
            else
                Color.Black
        }
    }
}

@Composable
private fun OutlinedText( text: String, outlineColor: Color ) {
    // Main text
    BasicText(
        text = text,
        style = typography().xxs.semiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )

    // Outline
    BasicText(
        text = text,
        style = typography().xxs
                            .semiBold
                            .merge(
                                TextStyle(
                                    drawStyle = Stroke(width = 1.0f, join = StrokeJoin.Round),
                                    color = outlineColor
                                )
                            ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@UnstableApi
@Composable
fun DurationIndicator(
    player: Player,
    scrubbingPosition: Long?,
    position: Long,
    duration: Long
) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding( horizontal = 10.dp )
                           .fillMaxWidth()
    ) {
        SkipTimeButton(
            player, position, Long::minus, ::maxOf, 0, "Rewind", "Rewind 5 seconds", "Rewinds 30 seconds", Modifier.rotate( 180f )
        )

        Spacer( Modifier.width( 5.dp ) )

        /**
         * Current implement of [rememberPreference] creates new [MutableState]
         * each time the function is called. To prevent creation of multiple instances,
         * this variable is placed in parent class and passed to each [OutlinedText].
         *
         * When it's updated, all [OutlinedText] are updated as well.
         */
        val outlineColor by outlineColorState()

        // Scrubbing position
        Box(
            modifier = Modifier.weight( 1f )
                               .height( DURATION_INDICATOR_HEIGHT.dp ),
            contentAlignment = Alignment.CenterStart
        ) {
            val toDisplay by remember( position ) {
                derivedStateOf {
                    (scrubbingPosition ?: position).toDuration(DurationUnit.MILLISECONDS)
                                                   .readableText()
                }
            }
            OutlinedText( toDisplay, outlineColor )
        }

        // Remaining duration
        val showRemainingSongTime by Preferences.PLAYER_SHOW_SONGS_REMAINING_TIME
        if( showRemainingSongTime ) {
            Box(
                modifier = Modifier.weight( 1f )
                                   .height( DURATION_INDICATOR_HEIGHT.dp ),
                contentAlignment = Alignment.Center
            ) {
                val positionAndDuration by player.positionAndDurationState()
                val timeRemaining by remember {
                    derivedStateOf {
                        (positionAndDuration.second - positionAndDuration.first).coerceAtLeast( 0 )
                    }
                }
                var isPaused by remember { mutableStateOf(false) }

                val pauseBetweenSongs by Preferences.PAUSE_BETWEEN_SONGS
                if(pauseBetweenSongs != PauseBetweenSongs.`0`)
                    LaunchedEffect(timeRemaining) {
                        if(timeRemaining < 500) {
                            isPaused = true
                            player.pause()
                            delay(pauseBetweenSongs.asMillis)
                            player.play()
                            isPaused = false
                        }
                    }

                if(isPaused) return@Box

                val toDisplay by remember {
                    derivedStateOf {
                        timeRemaining.toDuration(DurationUnit.MILLISECONDS).readableText()
                    }
                }
                OutlinedText( toDisplay, outlineColor )
            }
        }

        // Song's duration
        Box(
            modifier = Modifier.weight( 1f )
                               .height( DURATION_INDICATOR_HEIGHT.dp ),
            contentAlignment = Alignment.CenterEnd
        ) {
            val toDisplay = remember( duration ) {
                if( duration <= 0 ) "--:--" else duration.toDuration( DurationUnit.MILLISECONDS ).readableText()
            }
            OutlinedText( toDisplay, outlineColor )
        }

        Spacer( Modifier.width( 5.dp ) )

        SkipTimeButton(
            player, position, Long::plus, ::minOf, duration, "Forward", "Forward 5 seconds", "Forward 30 seconds"
        )
    }
}