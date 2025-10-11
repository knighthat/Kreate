package it.fast4x.rimusic.utils

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import app.kreate.android.Preferences
import it.fast4x.rimusic.enums.PlayerBackgroundColors
import it.fast4x.rimusic.enums.PlayerControlsType
import it.fast4x.rimusic.service.modern.PlayerServiceModern
import it.fast4x.rimusic.ui.components.themed.PlaybackParamsDialog
import it.fast4x.rimusic.ui.screens.player.components.controls.ControlsEssential
import it.fast4x.rimusic.ui.screens.player.components.controls.ControlsModern
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun GetControls(
    binder: PlayerServiceModern.Binder,
    position: Long,
    shouldBePlaying: Boolean,
    likedAt: Long?,
    mediaId: String,
    onBlurScaleChange: (Float) -> Unit
) {
    val playerControlsType by Preferences.PLAYER_CONTROLS_TYPE
    val playerPlayButtonType by Preferences.PLAYER_PLAY_BUTTON_TYPE
    val playerBackgroundColors by Preferences.PLAYER_BACKGROUND

    val isGradientBackgroundEnabled = playerBackgroundColors == PlayerBackgroundColors.ThemeColorGradient ||
            playerBackgroundColors == PlayerBackgroundColors.CoverColorGradient

    var playbackSpeed by Preferences.AUDIO_SPEED_VALUE
    var playbackDuration by Preferences.AUDIO_MEDLEY_DURATION

    var showSpeedPlayerDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSpeedPlayerDialog) {
        PlaybackParamsDialog(
            onDismiss = { showSpeedPlayerDialog = false },
            speedValue = { playbackSpeed = it },
            pitchValue = {},
            durationValue = {
                playbackDuration = it
            },
            scaleValue = onBlurScaleChange
        )
    }


        MedleyMode(
            binder = binder,
            seconds = if (playbackDuration < 1f) 0 else playbackDuration.roundToInt()
        )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth()
    ) {

        if (playerControlsType == PlayerControlsType.Essential)
            ControlsEssential(
                binder = binder,
                position = position,
                playbackSpeed = playbackSpeed,
                shouldBePlaying = shouldBePlaying,
                likedAt = likedAt,
                mediaId = mediaId,
                playerPlayButtonType = playerPlayButtonType,
                isGradientBackgroundEnabled = isGradientBackgroundEnabled,
                onShowSpeedPlayerDialog = { showSpeedPlayerDialog = true }
            )

        if (playerControlsType == PlayerControlsType.Modern)
            ControlsModern(
                binder = binder,
                playbackSpeed = playbackSpeed,
                shouldBePlaying = shouldBePlaying,
                playerPlayButtonType = playerPlayButtonType,
                onShowSpeedPlayerDialog = { showSpeedPlayerDialog = true }
            )
    }
}