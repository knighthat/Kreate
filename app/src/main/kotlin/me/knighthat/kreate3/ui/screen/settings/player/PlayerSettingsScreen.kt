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

package me.knighthat.kreate3.ui.screen.settings.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.knighthat.kreate3.R
import me.knighthat.kreate3.constant.AutoTapAction
import me.knighthat.kreate3.constant.PlaybackFailureAction
import me.knighthat.kreate3.constant.SleepTimerStep
import me.knighthat.kreate3.lyrics.LyricsFinder
import me.knighthat.kreate3.model.LyricsProvider
import me.knighthat.kreate3.model.LyricsSync
import me.knighthat.kreate3.playback.PlaybackController
import me.knighthat.kreate3.playback.PlaybackLifecycle
import me.knighthat.kreate3.playback.PlayerBuild
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.RestartPlayerButton
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.labelRes
import me.knighthat.kreate3.ui.component.labelsOf
import me.knighthat.kreate3.ui.component.sourceRes
import me.knighthat.kreate3.ui.screen.player.LYRICS_TEXT_SIZES
import me.knighthat.kreate3.ui.screen.player.labelRes
import me.knighthat.kreate3.ui.screen.settings.ChoiceSetting
import me.knighthat.kreate3.ui.screen.settings.NumberSetting
import me.knighthat.kreate3.ui.screen.settings.SettingsPage
import me.knighthat.kreate3.ui.screen.settings.SettingsSection
import me.knighthat.kreate3.ui.screen.settings.SliderSetting
import me.knighthat.kreate3.ui.screen.settings.SwitchSetting
import org.koin.compose.koinInject

/**
 * How the player behaves and looks, what it keeps of the songs played, where its lyrics come
 * from, and what tapping an item in Android Auto does.
 */
@Composable
fun PlayerSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    lyrics: LyricsFinder = koinInject(),
    playback: PlaybackLifecycle = koinInject(),
    controller: PlaybackController = koinInject(),
) {
    val restartNeeded by playback.restartNeeded.collectAsStateWithLifecycle(false)
    val sleepTimer by controller.sleepTimer.collectAsStateWithLifecycle()
    val historyPaused by Preferences.PLAYBACK_HISTORY_PAUSED.collectAsStateWithLifecycle()
    val historyMinSeconds by Preferences.PLAYBACK_HISTORY_MIN_SECONDS.collectAsStateWithLifecycle()
    val homeTapRadio by Preferences.HOME_SONGS_TAP_RADIO.collectAsStateWithLifecycle()
    val searchTapRadio by Preferences.SEARCH_SONGS_TAP_RADIO.collectAsStateWithLifecycle()
    val queuePersisted by Preferences.PERSISTENT_QUEUE_ENABLED.collectAsStateWithLifecycle()
    val queueSaveInterval by Preferences.PERSISTENT_QUEUE_SAVE_INTERVAL.collectAsStateWithLifecycle()
    val preloadSeconds by Preferences.PLAYBACK_PRELOAD_SECONDS.collectAsStateWithLifecycle()
    val failureAction by Preferences.PLAYBACK_FAILURE_ACTION.collectAsStateWithLifecycle()
    val stallSeconds by Preferences.PLAYBACK_STALL_SECONDS.collectAsStateWithLifecycle()
    val loudnessNormalization by Preferences.LOUDNESS_NORMALIZATION.collectAsStateWithLifecycle()
    val loudnessTarget by Preferences.LOUDNESS_TARGET_LUFS.collectAsStateWithLifecycle()
    val bufferAhead by Preferences.PLAYBACK_BUFFER_AHEAD_SECONDS.collectAsStateWithLifecycle()
    val bufferToStart by Preferences.PLAYBACK_BUFFER_TO_START_SECONDS.collectAsStateWithLifecycle()
    val keepPlayed by Preferences.PLAYBACK_KEEP_PLAYED.collectAsStateWithLifecycle()
    val lyricsSource by Preferences.LYRICS_SOURCE.collectAsStateWithLifecycle()
    val lyricsSync by Preferences.LYRICS_SYNC.collectAsStateWithLifecycle()
    val lyricsAlwaysLive by Preferences.LYRICS_ALWAYS_LIVE.collectAsStateWithLifecycle()
    val lyricsTextSize by Preferences.LYRICS_TEXT_SIZE.collectAsStateWithLifecycle()
    val sleepTimerRemove by Preferences.SLEEP_TIMER_REMOVE_STEP.collectAsStateWithLifecycle()
    val sleepTimerAdd by Preferences.SLEEP_TIMER_ADD_STEP.collectAsStateWithLifecycle()
    val autoAlbumTap by Preferences.AUTO_ALBUM_TAP.collectAsStateWithLifecycle()
    val autoArtistTap by Preferences.AUTO_ARTIST_TAP.collectAsStateWithLifecycle()
    val autoPlaylistTap by Preferences.AUTO_PLAYLIST_TAP.collectAsStateWithLifecycle()

    PlayerSettingsContent(
        historyPaused = historyPaused,
        historyMinSeconds = historyMinSeconds,
        homeTapRadio = homeTapRadio,
        searchTapRadio = searchTapRadio,
        queuePersisted = queuePersisted,
        queueSaveInterval = queueSaveInterval,
        preloadSeconds = preloadSeconds,
        failureAction = failureAction,
        stallSeconds = stallSeconds,
        loudnessNormalization = loudnessNormalization,
        loudnessTarget = loudnessTarget,
        bufferAhead = bufferAhead,
        bufferToStart = bufferToStart,
        keepPlayed = keepPlayed,
        lyricsSource = (lyrics.source(lyricsSource) ?: lyrics.sources.first()).provider,
        lyricsSources = lyrics.sources.associate { it.provider to it.name },
        lyricsSync = lyricsSync,
        lyricsAlwaysLive = lyricsAlwaysLive,
        lyricsTextSize = lyricsTextSize,
        sleepTimerRemove = sleepTimerRemove,
        sleepTimerAdd = sleepTimerAdd,
        autoAlbumTap = autoAlbumTap,
        autoArtistTap = autoArtistTap,
        autoPlaylistTap = autoPlaylistTap,
        restartNeeded = restartNeeded,
        sleepTimerOn = sleepTimer != null,
        onRestartPlayer = { playback.restartPlayer() },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * @param lyricsSource the provider used, one of [lyricsSources]
 * @param lyricsSources the name of each provider there is a source for, in the order they show
 * @param restartNeeded whether a setting changed applies only to a new player, see [PlayerBuild]
 * @param sleepTimerOn whether the sleep timer is on, a restart stops it
 */
@Composable
private fun PlayerSettingsContent(
    historyPaused: Boolean,
    historyMinSeconds: Int,
    homeTapRadio: Boolean,
    searchTapRadio: Boolean,
    queuePersisted: Boolean,
    queueSaveInterval: Int,
    preloadSeconds: Int,
    failureAction: PlaybackFailureAction,
    stallSeconds: Int,
    loudnessNormalization: Boolean,
    loudnessTarget: Int,
    bufferAhead: Int,
    bufferToStart: Int,
    keepPlayed: Boolean,
    lyricsSource: LyricsProvider,
    lyricsSources: Map<LyricsProvider, String>,
    lyricsSync: LyricsSync,
    lyricsAlwaysLive: Boolean,
    lyricsTextSize: Int,
    sleepTimerRemove: SleepTimerStep,
    sleepTimerAdd: SleepTimerStep,
    autoAlbumTap: AutoTapAction,
    autoArtistTap: AutoTapAction,
    autoPlaylistTap: AutoTapAction,
    restartNeeded: Boolean,
    sleepTimerOn: Boolean,
    onRestartPlayer: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmRestart by remember { mutableStateOf(false) }
    if (confirmRestart)
        RestartPlayerDialog(
            sleepTimerOn = sleepTimerOn,
            onRestart = {
                confirmRestart = false
                onRestartPlayer()
            },
            onDismiss = { confirmRestart = false },
        )

    SettingsPage(
        title = stringResource(R.string.screen_title_player),
        onBack = onBack,
        modifier = modifier,
        actions = { RestartPlayerButton({ confirmRestart = true }, enabled = restartNeeded) },
    ) {
        SettingsSection(stringResource(R.string.setting_header_queue))
        SwitchSetting(
            title = stringResource(R.string.setting_title_radio_from_songs),
            checked = homeTapRadio,
            onCheckedChange = { Preferences.HOME_SONGS_TAP_RADIO.update(it) },
            summary = if (homeTapRadio)
                stringResource(R.string.setting_description_radio_from_songs_on)
            else
                stringResource(R.string.setting_description_radio_from_songs_off),
        )
        SwitchSetting(
            title = stringResource(R.string.setting_title_radio_from_search),
            checked = searchTapRadio,
            onCheckedChange = { Preferences.SEARCH_SONGS_TAP_RADIO.update(it) },
            summary = if (searchTapRadio)
                stringResource(R.string.setting_description_radio_from_search_on)
            else
                stringResource(R.string.setting_description_radio_from_search_off),
        )
        SwitchSetting(
            title = stringResource(R.string.setting_title_keep_the_queue),
            checked = queuePersisted,
            onCheckedChange = { Preferences.PERSISTENT_QUEUE_ENABLED.update(it) },
            summary = stringResource(R.string.setting_description_keep_the_queue),
        )
        if (queuePersisted)
            NumberSetting(
                title = stringResource(R.string.setting_title_save_interval),
                value = queueSaveInterval.toLong(),
                min = 1,
                max = 5 * 60,
                onSave = { Preferences.PERSISTENT_QUEUE_SAVE_INTERVAL.update(it.toInt()) },
                note = {
                    stringResource(
                        R.string.setting_description_save_interval,
                        pluralStringResource(R.plurals.second, it.toInt(), it)
                    )
                },
            )
        val off = stringResource(R.string.status_off)
        val preloadLabels = PRELOAD_SECONDS.associateWith {
            if (it == 0) off else pluralStringResource(R.plurals.second, it, it)
        }
        SliderSetting(
            title = stringResource(R.string.setting_title_load_next_song_ahead),
            value = preloadSeconds,
            options = PRELOAD_SECONDS,
            label = { preloadLabels.getValue(it) },
            onSelect = { Preferences.PLAYBACK_PRELOAD_SECONDS.update(it) },
        )
        ChoiceSetting(
            title = stringResource(R.string.setting_title_when_a_song_fails),
            value = failureAction,
            options = PlaybackFailureAction.entries,
            label = labelsOf(PlaybackFailureAction.entries) { it.labelRes },
            onSelect = { Preferences.PLAYBACK_FAILURE_ACTION.update(it) },
        )
        val stallLabels = STALL_SECONDS.associateWith {
            if (it == 0) off else pluralStringResource(R.plurals.second, it, it)
        }
        SliderSetting(
            title = stringResource(R.string.setting_title_give_up_loading_after),
            value = stallSeconds,
            options = STALL_SECONDS,
            label = { stallLabels.getValue(it) },
            onSelect = { Preferences.PLAYBACK_STALL_SECONDS.update(it) },
        )

        SettingsSection(stringResource(R.string.setting_header_sound))
        SwitchSetting(
            title = stringResource(R.string.setting_title_even_out_loudness),
            checked = loudnessNormalization,
            onCheckedChange = { Preferences.LOUDNESS_NORMALIZATION.update(it) },
            summary = stringResource(R.string.setting_description_even_out_loudness),
        )
        if (loudnessNormalization) {
            val lufsLabels = TARGET_LUFS.associateWith { stringResource(R.string.lufs, it) }
            SliderSetting(
                title = stringResource(R.string.setting_title_target_loudness),
                value = loudnessTarget,
                options = TARGET_LUFS,
                label = { lufsLabels.getValue(it) },
                onSelect = { Preferences.LOUDNESS_TARGET_LUFS.update(it) },
            )
        }

        SettingsSection(stringResource(R.string.setting_header_buffering))
        val unlimited = stringResource(R.string.status_unlimited)
        val bufferLabels = BUFFER_AHEAD_SECONDS.associateWith {
            if (it == 0) unlimited else pluralStringResource(R.plurals.second, it, it)
        }
        SliderSetting(
            title = stringResource(R.string.setting_title_load_ahead),
            value = bufferAhead,
            options = BUFFER_AHEAD_SECONDS,
            label = { bufferLabels.getValue(it) },
            onSelect = { Preferences.PLAYBACK_BUFFER_AHEAD_SECONDS.update(it) },
        )
        SliderSetting(
            title = stringResource(R.string.setting_title_load_before_playing),
            value = bufferToStart,
            options = BUFFER_SECONDS,
            label = { bufferLabels.getValue(it) },
            onSelect = { Preferences.PLAYBACK_BUFFER_TO_START_SECONDS.update(it) },
        )
        SwitchSetting(
            title = stringResource(R.string.setting_title_keep_what_was_played),
            checked = keepPlayed,
            onCheckedChange = { Preferences.PLAYBACK_KEEP_PLAYED.update(it) },
            summary = stringResource(R.string.setting_description_keep_what_was_played),
        )

        SettingsSection(stringResource(R.string.setting_header_sleep_timer))
        val sleepTimerLabels = SleepTimerStep.entries.associateWith { step ->
            step.minutes?.let { pluralStringResource(R.plurals.minute, it, it) }
                ?: stringResource(R.string.sleep_timer_to_end_of_song)
        }
        ChoiceSetting(
            title = stringResource(R.string.setting_title_remove_button),
            value = sleepTimerRemove,
            options = SleepTimerStep.entries,
            label = { sleepTimerLabels.getValue(it) },
            onSelect = { Preferences.SLEEP_TIMER_REMOVE_STEP.update(it) },
        )
        ChoiceSetting(
            title = stringResource(R.string.setting_title_add_button),
            value = sleepTimerAdd,
            options = SleepTimerStep.entries,
            label = { sleepTimerLabels.getValue(it) },
            onSelect = { Preferences.SLEEP_TIMER_ADD_STEP.update(it) },
        )

        SettingsSection(stringResource(R.string.setting_header_history))
        SwitchSetting(
            title = stringResource(R.string.setting_title_pause_history),
            checked = historyPaused,
            onCheckedChange = { Preferences.PLAYBACK_HISTORY_PAUSED.update(it) },
            summary = stringResource(R.string.setting_description_pause_history),
        )
        NumberSetting(
            title = stringResource(R.string.setting_title_minimum_play_time),
            value = historyMinSeconds.toLong(),
            min = 1,
            max = 10 * 60,
            onSave = { Preferences.PLAYBACK_HISTORY_MIN_SECONDS.update(it.toInt()) },
            note = {
                stringResource(
                    R.string.setting_description_minimum_play_time,
                    pluralStringResource(R.plurals.second, it.toInt(), it)
                )
            },
        )

        SettingsSection(stringResource(R.string.setting_header_lyrics))
        ChoiceSetting(
            title = stringResource(R.string.setting_title_lyrics_source),
            value = lyricsSource,
            options = lyricsSources.keys.toList(),
            label = { lyricsSources.getValue(it) },
            onSelect = { Preferences.LYRICS_SOURCE.update(it) },
        )
        val syncLabels = LyricsSync.entries.associateWith { stringResource(it.labelRes) }
        ChoiceSetting(
            title = stringResource(R.string.setting_title_preferred_timing),
            value = lyricsSync,
            options = LyricsSync.entries.reversed(),
            label = { syncLabels.getValue(it) },
            onSelect = { Preferences.LYRICS_SYNC.update(it) },
        )
        SwitchSetting(
            title = stringResource(R.string.setting_title_always_fetch_lyrics),
            checked = lyricsAlwaysLive,
            onCheckedChange = { Preferences.LYRICS_ALWAYS_LIVE.update(it) },
            summary = if (lyricsAlwaysLive)
                stringResource(R.string.setting_description_always_fetch_lyrics_on)
            else
                stringResource(R.string.setting_description_always_fetch_lyrics_off),
        )
        val textSizes = LYRICS_TEXT_SIZES.toList()
        val percents = textSizes.associateWith { stringResource(R.string.percent, it) }
        SliderSetting(
            title = stringResource(R.string.setting_title_text_size),
            value = lyricsTextSize,
            options = textSizes,
            label = { percents.getValue(it) },
            onSelect = { Preferences.LYRICS_TEXT_SIZE.update(it) },
        )

        SettingsSection(stringResource(R.string.setting_header_android_auto))
        val autoTapLabels = labelsOf(AutoTapAction.entries) { it.labelRes }
        ChoiceSetting(
            title = stringResource(R.string.setting_title_tapping_an_album),
            value = autoAlbumTap,
            options = AutoTapAction.entries,
            label = autoTapLabels,
            onSelect = { Preferences.AUTO_ALBUM_TAP.update(it) },
        )
        ChoiceSetting(
            title = stringResource(R.string.setting_title_tapping_an_artist),
            value = autoArtistTap,
            options = AutoTapAction.entries,
            label = autoTapLabels,
            onSelect = { Preferences.AUTO_ARTIST_TAP.update(it) },
        )
        ChoiceSetting(
            title = stringResource(R.string.setting_title_tapping_a_playlist),
            value = autoPlaylistTap,
            options = AutoTapAction.entries,
            label = autoTapLabels,
            onSelect = { Preferences.AUTO_PLAYLIST_TAP.update(it) },
        )
    }
}

/**
 * Asks whether to restart the player, with a warning while the sleep timer is on.
 */
@Composable
private fun RestartPlayerDialog(
    sleepTimerOn: Boolean,
    onRestart: () -> Unit,
    onDismiss: () -> Unit
) =
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_restart_the_player)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.dialog_body_restart_the_player_1))
                if (sleepTimerOn)
                    Text(
                        text = stringResource(R.string.dialog_body_restart_the_player_2),
                        color = MaterialTheme.colorScheme.error,
                    )
            }
        },
        confirmButton = { TextButton(onClick = onRestart) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )

@ScreenPreviews
@Composable
private fun PlayerSettingsPreview() = PreviewTheme {
    PlayerSettingsContent(
        historyPaused = false,
        historyMinSeconds = 1,
        homeTapRadio = false,
        searchTapRadio = false,
        queuePersisted = true,
        queueSaveInterval = 10,
        preloadSeconds = 5,
        failureAction = PlaybackFailureAction.SKIP,
        stallSeconds = 15,
        loudnessNormalization = true,
        loudnessTarget = -14,
        bufferAhead = 50,
        bufferToStart = 1,
        keepPlayed = false,
        lyricsSource = LyricsProvider.LRCLIB,
        lyricsSources = LyricsProvider.entries.associateWith { stringResource(it.sourceRes) },
        lyricsSync = LyricsSync.WORD,
        lyricsAlwaysLive = false,
        lyricsTextSize = 100,
        sleepTimerRemove = SleepTimerStep.FIVE_MINUTES,
        sleepTimerAdd = SleepTimerStep.FIVE_MINUTES,
        autoAlbumTap = AutoTapAction.OPEN,
        autoArtistTap = AutoTapAction.OPEN,
        autoPlaylistTap = AutoTapAction.OPEN,
        restartNeeded = true,
        sleepTimerOn = false,
        onRestartPlayer = {},
        onBack = {},
    )
}

/** Seconds of the next song that can be loaded ahead, `0` for none. */
private val PRELOAD_SECONDS = listOf(0, 5, 10, 15, 30, 60)

/** Seconds a song may load while it should play before it fails, `0` to wait for the network. */
private val STALL_SECONDS = listOf(0, 5, 10, 15, 30, 60)

/** Seconds that can be loaded before a song plays. */
private val BUFFER_SECONDS = listOf(1, 2, 3, 5, 10, 15, 20, 30, 40, 50, 60)

/** Seconds that can be loaded ahead, `0` (last) for the whole song. */
private val BUFFER_AHEAD_SECONDS = BUFFER_SECONDS + 0

/** Loudness songs can be evened out to, in LUFS, quietest first. */
private val TARGET_LUFS = (-18..-10).toList()
