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

import androidx.annotation.PluralsRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import me.knighthat.kreate3.R
import me.knighthat.kreate3.constant.SyncMode
import me.knighthat.kreate3.preferences.Preference
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.provider.LibrarySection

/** "Sync songs" and the like, [plural] naming what is synced. */
@Composable
internal fun syncTitle(@PluralsRes plural: Int): String =
    stringResource(R.string.setting_title_sync, pluralStringResource(plural, 2))

/** What [mode] does to the albums or artists [plural] names. */
@Composable
internal fun syncModeDescription(mode: SyncMode, @PluralsRes plural: Int): String =
    stringResource(
        when (mode) {
            SyncMode.OFF -> R.string.setting_description_sync_status_off
            SyncMode.LIKED -> R.string.setting_description_sync_status_liked
            SyncMode.ALL -> R.string.setting_description_sync_status_all
        },
        pluralStringResource(plural, 2),
    )

/** Liked songs on [source] are synced while [on]. */
@Composable
internal fun syncSongsDescription(on: Boolean, source: String): String = stringResource(
    if (on) R.string.setting_description_sync_liked_songs_on
    else R.string.setting_description_sync_liked_songs_off,
    source,
)

/** The playlists on [source] are synced while [on]. */
@Composable
internal fun syncPlaylistsDescription(on: Boolean, source: String): String = stringResource(
    if (on) R.string.setting_description_sync_playlists_on
    else R.string.setting_description_sync_playlists_off,
    source,
)

/**
 * A switch for each [LibrarySection] of YouTube: the liked songs, saved albums, followed artists
 * and playlists.
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
    val source = stringResource(R.string.source_ytm)
    val checked = syncs.getValue(section)
    val mode = if (checked) SyncMode.LIKED else SyncMode.OFF
    val (title, summary) = when (section) {
        LibrarySection.SONGS ->
            syncTitle(R.plurals.unit_song) to syncSongsDescription(checked, source)

        LibrarySection.ALBUMS ->
            syncTitle(R.plurals.unit_album) to syncModeDescription(mode, R.plurals.unit_album)

        LibrarySection.ARTISTS ->
            syncTitle(R.plurals.unit_artist) to syncModeDescription(mode, R.plurals.unit_artist)

        LibrarySection.PLAYLISTS ->
            syncTitle(R.plurals.unit_playlist) to syncPlaylistsDescription(checked, source)
    }
    SwitchSetting(
        title = title,
        summary = summary,
        checked = checked,
        onCheckedChange = { onSyncChange(section, it) },
    )
}
