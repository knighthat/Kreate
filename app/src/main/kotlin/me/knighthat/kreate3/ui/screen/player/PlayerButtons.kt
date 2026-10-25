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

package me.knighthat.kreate3.ui.screen.player

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import kotlinx.coroutines.delay
import me.knighthat.kreate3.R
import me.knighthat.kreate3.playback.PlaybackController
import me.knighthat.kreate3.playback.SleepTimer
import me.knighthat.kreate3.playback.SleepTimerAction
import me.knighthat.kreate3.playback.buttonLabel
import me.knighthat.kreate3.playback.changedBy
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.ui.component.LocalNotifier
import me.knighthat.kreate3.ui.component.formatDuration
import me.knighthat.kreate3.ui.screen.detail.rememberSongShare
import kotlin.time.Duration.Companion.minutes

/** Minutes the sleep timer can be set to. */
private val SLEEP_TIMER_MINUTES = listOf(5, 10, 15, 30, 45, 60)

/**
 * The buttons of [NowPlayingPage]: the equalizer, the sleep timer, in the primary color while it
 * runs, and sharing the song.
 */
@Composable
internal fun PlayerButtons(
    item: MediaItem,
    controller: PlaybackController,
    modifier: Modifier = Modifier,
) = Row(
    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier,
) {
    EqualizerButton(controller)
    SleepTimerButton(controller)
    SongShareButton(item)
}

/**
 * Opens the equalizer of the device on the player's sound, the system's or an app's. Not every
 * device has one, the user is told then.
 */
@Composable
private fun EqualizerButton(controller: PlaybackController) {
    val context = LocalContext.current
    val notifier = LocalNotifier.current
    // Started for a result, the equalizer learns which app opened it
    val equalizer =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}

    ModeButton(
        icon = R.drawable.equalizer,
        description = stringResource(R.string.semantic_equalizer),
        isOn = false,
        onClick = {
            val audioSession = controller.audioSessionId()
            if (audioSession == null) {
                notifier.post("The player isn't ready yet")
                return@ModeButton
            }
            val panel = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSession)
                .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            try {
                equalizer.launch(panel)
            } catch (_: ActivityNotFoundException) {
                notifier.post("No equalizer on this device")
            }
        },
    )
}

/**
 * Sets when playback pauses by itself: after some minutes or at the end of the song. While a
 * timer runs, the menu says what is left and has the buttons of its notification: take time off,
 * put it on hold or let it go on, and add time. Each change shows the notification again.
 */
@Composable
private fun SleepTimerButton(controller: PlaybackController) = Box {
    val timer by controller.sleepTimer.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }

    ModeButton(
        icon = R.drawable.bedtime,
        description = stringResource(R.string.semantic_sleep_timer),
        isOn = timer != null,
        onClick = { open = true },
    )
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        timer?.let { running ->
            SleepTimerControls(running, controller)
            HorizontalDivider()
        }
        SleepTimerItem("Off", isChecked = timer == null) {
            open = false
            controller.changeSleepTimer(SleepTimerAction.STOP)
        }
        SLEEP_TIMER_MINUTES.forEach { minutes ->
            // A running timer counts down, no row stays its match
            SleepTimerItem(if (minutes == 60) "1 hour" else "$minutes minutes", isChecked = false) {
                open = false
                controller.startSleepTimer(minutes.minutes)
            }
        }
        SleepTimerItem("End of song", isChecked = timer is SleepTimer.EndOfSong) {
            open = false
            controller.startSleepTimer(null)
        }
    }
}

/**
 * What is left of [timer], counted down, and the buttons to change it, like its notification's.
 * The remove and add buttons go by the steps of the settings, each is off while it can't change
 * the timer.
 */
@Composable
private fun SleepTimerControls(timer: SleepTimer, controller: PlaybackController) {
    val removeStep by Preferences.SLEEP_TIMER_REMOVE_STEP.collectAsStateWithLifecycle()
    val addStep by Preferences.SLEEP_TIMER_ADD_STEP.collectAsStateWithLifecycle()
    var remaining by remember(timer) { mutableStateOf(timer.remaining) }
    if (timer is SleepTimer.Running)
        LaunchedEffect(timer) {
            while (timer.remaining > 0) {
                // On the next second of what is left, the count goes down in step
                delay(timer.remaining % 1_000 + 1)
                remaining = timer.remaining
            }
        }

    Text(
        text = when {
            remaining == null && timer.isPaused -> "On hold, until the song ends"
            remaining == null -> "Pauses when the song ends"
            timer.isPaused -> "On hold, ${formatDuration(remaining)} left"
            else -> "Pauses in ${formatDuration(remaining)}"
        },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
    ) {
        // Asked again each second with the count, removing can't take all that is left
        TextButton(
            onClick = { controller.changeSleepTimer(SleepTimerAction.REMOVE) },
            enabled = timer.changedBy(removeStep, add = false) != null,
        ) {
            Text(removeStep.buttonLabel(add = false))
        }
        IconButton(onClick = { controller.changeSleepTimer(SleepTimerAction.TOGGLE_PAUSE) }) {
            Icon(
                painter = painterResource(
                    if (timer.isPaused) R.drawable.play_arrow else R.drawable.pause
                ),
                contentDescription = if (timer.isPaused) stringResource(R.string.semantic_resume_the_sleep_timer)
                else stringResource(R.string.semantic_pause_the_sleep_timer),
            )
        }
        TextButton(
            onClick = { controller.changeSleepTimer(SleepTimerAction.ADD) },
            enabled = timer.changedBy(addStep, add = true) != null,
        ) {
            Text(addStep.buttonLabel(add = true))
        }
    }
}

/** A row of the sleep timer's menu, the check before its label. */
@Composable
private fun SleepTimerItem(label: String, isChecked: Boolean, onClick: () -> Unit) =
    DropdownMenuItem(
        text = { Text(label) },
        // Room for the check on every row, the labels line up
        leadingIcon = {
            if (isChecked)
                Icon(
                    painterResource(R.drawable.check),
                    contentDescription = stringResource(R.string.semantic_set)
                )
            else
                Spacer(Modifier.size(24.dp))
        },
        onClick = onClick,
    )

/** Shares the song, see [rememberSongShare]. */
@Composable
private fun SongShareButton(item: MediaItem) {
    val share = rememberSongShare(item)

    ModeButton(
        icon = R.drawable.share,
        description = stringResource(R.string.semantic_share),
        isOn = false,
        enabled = share.enabled,
        onClick = { share.share() },
    )
}
