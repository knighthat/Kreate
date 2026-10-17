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
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle

/**
 * How the player behaves and looks, and what it keeps of the songs played.
 */
@Composable
fun PlayerSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val historyPaused by Preferences.PLAYBACK_HISTORY_PAUSED.collectAsStateWithLifecycle()
    val historyMinSeconds by Preferences.PLAYBACK_HISTORY_MIN_SECONDS.collectAsStateWithLifecycle()
    val homeTapRadio by Preferences.HOME_SONGS_TAP_RADIO.collectAsStateWithLifecycle()
    val searchTapRadio by Preferences.SEARCH_SONGS_TAP_RADIO.collectAsStateWithLifecycle()
    val queuePersisted by Preferences.PERSISTENT_QUEUE_ENABLED.collectAsStateWithLifecycle()
    val queueSaveInterval by Preferences.PERSISTENT_QUEUE_SAVE_INTERVAL.collectAsStateWithLifecycle()

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
    }
}
