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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.knighthat.kreate3.R
import me.knighthat.kreate3.preferences.Profile
import me.knighthat.kreate3.preferences.ProfileManager
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.openSubsonicName
import me.knighthat.kreate3.ui.navigation.About
import me.knighthat.kreate3.ui.navigation.BackupSettings
import me.knighthat.kreate3.ui.navigation.DataSettings
import me.knighthat.kreate3.ui.navigation.GeneralSettings
import me.knighthat.kreate3.ui.navigation.NetworkSettings
import me.knighthat.kreate3.ui.navigation.PlayerSettings
import me.knighthat.kreate3.ui.navigation.Route
import me.knighthat.kreate3.ui.navigation.UiSettings
import org.koin.compose.koinInject

/**
 * The settings pages to pick from.
 *
 * @param onOpen called with the route of the page the user picked
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpen: (Route) -> Unit,
    modifier: Modifier = Modifier,
    profileManager: ProfileManager = koinInject(),
) {
    val activeId by profileManager.activeId.collectAsStateWithLifecycle()

    // A backup holds every profile, so only the default one may save or replace them
    SettingsContent(
        onBack = onBack,
        onOpen = onOpen,
        showBackup = activeId == Profile.DEFAULT_ID,
        server = openSubsonicName(),
        modifier = modifier,
    )
}

/** @param server what the OpenSubsonic server is called, see [openSubsonicName] */
@Composable
private fun SettingsContent(
    onBack: () -> Unit,
    onOpen: (Route) -> Unit,
    showBackup: Boolean,
    server: String,
    modifier: Modifier = Modifier,
) {
    SettingsPage(stringResource(R.string.screen_title_settings), onBack, modifier) {
        SettingsItem(
            title = stringResource(R.string.screen_title_general),
            summary = stringResource(R.string.setting_description_general),
            onClick = { onOpen(GeneralSettings) },
        )
        SettingsItem(
            title = stringResource(R.string.screen_title_data),
            summary = stringResource(R.string.setting_description_data),
            onClick = { onOpen(DataSettings) },
        )
        SettingsItem(
            title = stringResource(R.string.screen_title_player),
            summary = stringResource(R.string.setting_description_player),
            onClick = { onOpen(PlayerSettings) },
        )
        SettingsItem(
            title = stringResource(R.string.screen_title_ui),
            summary = stringResource(R.string.setting_description_ui),
            onClick = { onOpen(UiSettings) },
        )
        SettingsItem(
            title = stringResource(R.string.screen_title_network),
            summary = stringResource(
                R.string.setting_description_network,
                server,
                stringResource(R.string.source_ytm)
            ),
            onClick = { onOpen(NetworkSettings) },
        )
        if (showBackup)
            SettingsItem(
                title = stringResource(R.string.screen_title_backup),
                summary = stringResource(R.string.setting_description_backup),
                onClick = { onOpen(BackupSettings) },
            )
        SettingsItem(
            title = stringResource(R.string.screen_title_about),
            summary = stringResource(R.string.setting_description_about),
            onClick = { onOpen(About) },
        )
    }
}

@ScreenPreviews
@Composable
private fun SettingsPreview() = PreviewTheme {
    SettingsContent(onBack = {}, onOpen = {}, showBackup = true, server = "Navidrome")
}
