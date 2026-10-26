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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import me.knighthat.kreate3.R
import me.knighthat.kreate3.constant.AutoTapAction
import me.knighthat.kreate3.constant.SleepTimerStep
import me.knighthat.kreate3.lyrics.LyricsFinder
import me.knighthat.kreate3.model.LyricsSync
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.ui.component.labelRes
import me.knighthat.kreate3.ui.component.labelsOf
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
) {
    val historyPaused by Preferences.PLAYBACK_HISTORY_PAUSED.collectAsStateWithLifecycle()
    val historyMinSeconds by Preferences.PLAYBACK_HISTORY_MIN_SECONDS.collectAsStateWithLifecycle()
    val homeTapRadio by Preferences.HOME_SONGS_TAP_RADIO.collectAsStateWithLifecycle()
    val searchTapRadio by Preferences.SEARCH_SONGS_TAP_RADIO.collectAsStateWithLifecycle()
    val queuePersisted by Preferences.PERSISTENT_QUEUE_ENABLED.collectAsStateWithLifecycle()
    val queueSaveInterval by Preferences.PERSISTENT_QUEUE_SAVE_INTERVAL.collectAsStateWithLifecycle()
    val preloadSeconds by Preferences.PLAYBACK_PRELOAD_SECONDS.collectAsStateWithLifecycle()
    val lyricsSource by Preferences.LYRICS_SOURCE.collectAsStateWithLifecycle()
    val lyricsSync by Preferences.LYRICS_SYNC.collectAsStateWithLifecycle()
    val lyricsAlwaysLive by Preferences.LYRICS_ALWAYS_LIVE.collectAsStateWithLifecycle()
    val lyricsTextSize by Preferences.LYRICS_TEXT_SIZE.collectAsStateWithLifecycle()
    val sleepTimerRemove by Preferences.SLEEP_TIMER_REMOVE_STEP.collectAsStateWithLifecycle()
    val sleepTimerAdd by Preferences.SLEEP_TIMER_ADD_STEP.collectAsStateWithLifecycle()
    val autoAlbumTap by Preferences.AUTO_ALBUM_TAP.collectAsStateWithLifecycle()
    val autoArtistTap by Preferences.AUTO_ARTIST_TAP.collectAsStateWithLifecycle()
    val autoPlaylistTap by Preferences.AUTO_PLAYLIST_TAP.collectAsStateWithLifecycle()

    SettingsPage(stringResource(R.string.screen_title_player), onBack, modifier) {
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
                unit = pluralStringResource(R.plurals.unit_second, queueSaveInterval),
                min = 1,
                max = 5 * 60,
                onSave = { Preferences.PERSISTENT_QUEUE_SAVE_INTERVAL.update(it.toInt()) },
                note = stringResource(R.string.setting_description_save_interval),
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
            unit = pluralStringResource(R.plurals.unit_second, historyMinSeconds),
            min = 1,
            max = 10 * 60,
            onSave = { Preferences.PLAYBACK_HISTORY_MIN_SECONDS.update(it.toInt()) },
            note = stringResource(R.string.setting_description_minimum_play_time),
        )

        SettingsSection(stringResource(R.string.setting_header_lyrics))
        ChoiceSetting(
            title = stringResource(R.string.setting_title_lyrics_source),
            value = lyrics.source(lyricsSource) ?: lyrics.sources.first(),
            options = lyrics.sources,
            label = { it.name },
            onSelect = { Preferences.LYRICS_SOURCE.update(it.provider) },
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

/** Seconds of the next song that can be loaded ahead, `0` for none. */
private val PRELOAD_SECONDS = listOf(0, 5, 10, 15, 30, 60)
