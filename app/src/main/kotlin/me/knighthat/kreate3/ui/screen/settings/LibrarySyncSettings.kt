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
import androidx.compose.ui.res.stringResource
import me.knighthat.kreate3.R
import me.knighthat.kreate3.preferences.Preference
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.provider.LibrarySection

/**
 * A switch for each [LibrarySection] of YouTube: the liked songs are synced, the others show.
 *
 * @param setting the setting of a section, like `youTubeSyncSetting`
 */
@Composable
internal fun LibrarySyncSettings(
    setting: (LibrarySection) -> Preference<Boolean>,
    onSyncChange: (LibrarySection, Boolean) -> Unit,
) = LibrarySyncSettings(
    syncs = LibrarySection.entries.associateWith {
        setting(it).collectAsStateWithLifecycle().value
    },
    onSyncChange = onSyncChange,
)

/** [LibrarySyncSettings] with whether each section [syncs]. */
@Composable
internal fun LibrarySyncSettings(
    syncs: Map<LibrarySection, Boolean>,
    onSyncChange: (LibrarySection, Boolean) -> Unit,
) = LibrarySection.entries.forEach { section ->
    val (title, summary) = when (section) {
        LibrarySection.SONGS ->
            stringResource(R.string.setting_title_sync_liked_songs) to stringResource(
                R.string.setting_description_sync_songs_youtube,
                stringResource(R.string.source_ytm)
            )

        LibrarySection.ALBUMS ->
            stringResource(R.string.setting_title_show_albums) to stringResource(
                R.string.setting_description_show_albums_youtube,
                stringResource(R.string.source_ytm)
            )

        LibrarySection.ARTISTS ->
            stringResource(R.string.setting_title_show_artists) to stringResource(
                R.string.setting_description_show_artists_youtube,
                stringResource(R.string.source_ytm)
            )

        LibrarySection.PLAYLISTS ->
            stringResource(R.string.setting_title_show_playlists) to stringResource(
                R.string.setting_description_show_playlists_youtube,
                stringResource(R.string.source_ytm)
            )
    }
    SwitchSetting(
        title = title,
        summary = summary,
        checked = syncs.getValue(section),
        onCheckedChange = { onSyncChange(section, it) },
    )
}
