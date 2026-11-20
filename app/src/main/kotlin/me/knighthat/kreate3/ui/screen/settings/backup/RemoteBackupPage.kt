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

package me.knighthat.kreate3.ui.screen.settings.backup

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.StateFlow

/**
 * The Remote tab of the Backup page, which only the patreon build has: its `platformModule`
 * declares one with Koin, the others none, and the page then has no tabs.
 *
 * @property active whether cloud backup is on (signed in, the tier has it, the switch on); the tab
 *   is grayed out and can't be opened while it isn't
 * @property content the tab, kept clear of the bars by its padding
 */
class RemoteBackupPage(
    val active: StateFlow<Boolean>,
    val content: @Composable (contentPadding: PaddingValues) -> Unit,
)
