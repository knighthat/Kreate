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

package me.knighthat.kreate3.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import me.knighthat.kreate3.database.model.LyricsSync
import me.knighthat.kreate3.lyrics.LyricsFinder
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.ui.screen.player.label
import me.knighthat.kreate3.ui.screen.player.lyricsSyncOf
import org.koin.compose.koinInject

/**
 * How the player behaves and looks, what it keeps of the songs played, and where its lyrics come
 * from.
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
    val lyricsSource by Preferences.LYRICS_SOURCE.collectAsStateWithLifecycle()
    val lyricsSync by Preferences.LYRICS_SYNC.collectAsStateWithLifecycle()
    val lyricsAlwaysLive by Preferences.LYRICS_ALWAYS_LIVE.collectAsStateWithLifecycle()

    SettingsPage("Player", onBack, modifier) {
        SettingsSection("Queue")
        SwitchSetting(
            title = "Radio from Songs",
            checked = homeTapRadio,
            onCheckedChange = { Preferences.HOME_SONGS_TAP_RADIO.update(it) },
            summary = if (homeTapRadio)
                "A song tapped in Songs plays with songs like it"
            else
                "A song tapped in Songs plays with the songs below it",
        )
        SwitchSetting(
            title = "Radio from search",
            checked = searchTapRadio,
            onCheckedChange = { Preferences.SEARCH_SONGS_TAP_RADIO.update(it) },
            summary = if (searchTapRadio)
                "A song tapped in search results plays with songs like it"
            else
                "A song tapped in search results plays with the results below it",
        )
        SwitchSetting(
            title = "Keep the queue",
            checked = queuePersisted,
            onCheckedChange = { Preferences.PERSISTENT_QUEUE_ENABLED.update(it) },
            summary = "Pick up where you left off after the app closes",
        )
        if (queuePersisted)
            NumberSetting(
                title = "Save interval",
                value = queueSaveInterval.toLong(),
                unit = "seconds",
                min = 1,
                max = 5 * 60,
                onSave = { Preferences.PERSISTENT_QUEUE_SAVE_INTERVAL.update(it.toInt()) },
                note = "How often the place in the song is saved while it plays",
            )

        SettingsSection("History")
        SwitchSetting(
            title = "Pause history",
            checked = historyPaused,
            onCheckedChange = { Preferences.PLAYBACK_HISTORY_PAUSED.update(it) },
            summary = "Songs played meanwhile aren't added to the history",
        )
        NumberSetting(
            title = "Minimum play time",
            value = historyMinSeconds.toLong(),
            unit = "seconds",
            min = 1,
            max = 10 * 60,
            onSave = { Preferences.PLAYBACK_HISTORY_MIN_SECONDS.update(it.toInt()) },
            note = "Songs played for less aren't added to the history. Pauses and loading don't count",
        )

        SettingsSection("Lyrics")
        ChoiceSetting(
            title = "Lyrics source",
            value = lyrics.source(lyricsSource) ?: lyrics.sources.first(),
            options = lyrics.sources,
            label = { it.name },
            onSelect = { Preferences.LYRICS_SOURCE.update(it.id) },
        )
        ChoiceSetting(
            title = "Preferred timing",
            value = lyricsSyncOf(lyricsSync),
            options = LyricsSync.entries.reversed(),
            label = { it.label },
            onSelect = { Preferences.LYRICS_SYNC.update(it.name) },
        )
        SwitchSetting(
            title = "Always fetch lyrics",
            checked = lyricsAlwaysLive,
            onCheckedChange = { Preferences.LYRICS_ALWAYS_LIVE.update(it) },
            summary = if (lyricsAlwaysLive)
                "Lyrics are fetched each time they show, and not saved"
            else
                "Saved lyrics timed as preferred show without fetching",
        )
    }
}
