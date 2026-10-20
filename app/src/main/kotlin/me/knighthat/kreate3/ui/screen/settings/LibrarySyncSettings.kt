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
import me.knighthat.kreate3.preferences.Preference
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.provider.LibrarySection

/**
 * A switch for each [LibrarySection] of a source: the liked songs are synced, the others show.
 *
 * @param setting the setting of a section, like `youTubeSyncSetting`
 * @param where what the user's library is called on the source, like "on YouTube"
 */
@Composable
internal fun LibrarySyncSettings(
    setting: (LibrarySection) -> Preference<Boolean>,
    where: String,
    onSyncChange: (LibrarySection, Boolean) -> Unit,
) = LibrarySection.entries.forEach { section ->
    val syncs by setting(section).collectAsStateWithLifecycle()
    val (title, summary) = when (section) {
        LibrarySection.SONGS -> "Sync liked songs" to "The songs you liked $where are liked here too"
        LibrarySection.ALBUMS -> "Show albums" to "The albums you saved $where show in your library"
        LibrarySection.ARTISTS -> "Show artists" to "The artists you follow $where show in your library"
        LibrarySection.PLAYLISTS -> "Show playlists" to "Your playlists $where show in your library"
    }
    SwitchSetting(
        title = title,
        summary = summary,
        checked = syncs,
        onCheckedChange = { onSyncChange(section, it) },
    )
}
