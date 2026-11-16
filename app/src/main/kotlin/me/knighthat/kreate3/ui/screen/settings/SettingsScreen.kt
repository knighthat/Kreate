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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.knighthat.kreate3.R
import me.knighthat.kreate3.database.repository.UserRepository
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
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * The settings pages to pick from, and a row that resets the active profile's settings once
 * confirmed.
 *
 * @param onOpen called with the route of the page the user picked
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpen: (Route) -> Unit,
    modifier: Modifier = Modifier,
    profileManager: ProfileManager = koinInject(),
    userRepository: UserRepository = koinInject(),
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val activeId by profileManager.activeId.collectAsStateWithLifecycle()
    val defaultUser by remember(userRepository) { userRepository.find(Profile.DEFAULT_ID) }
        .collectAsStateWithLifecycle(null)

    // A backup holds every profile, so only the default one may save or replace them
    SettingsContent(
        onBack = onBack,
        onOpen = onOpen,
        backupAllowed = activeId == Profile.DEFAULT_ID,
        defaultProfileName = defaultUser?.name,
        server = openSubsonicName(),
        onReset = { viewModel.reset() },
        modifier = modifier,
    )
}

/**
 * @param backupAllowed whether the profile may open Backup; the row is grayed out otherwise,
 * naming [defaultProfileName] (`null` while it loads, the note stays hidden)
 * @param server what the OpenSubsonic server is called, see [openSubsonicName]
 */
@Composable
private fun SettingsContent(
    onBack: () -> Unit,
    onOpen: (Route) -> Unit,
    backupAllowed: Boolean,
    defaultProfileName: String?,
    server: String,
    onReset: () -> Unit,
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
        val backupDescription = stringResource(R.string.setting_description_backup)
        SettingsItem(
            title = stringResource(R.string.screen_title_backup),
            summary = if (backupAllowed || defaultProfileName == null) {
                backupDescription
            } else {
                val only = stringResource(
                    R.string.setting_description_backup_status_default_profile_only,
                    stringResource(R.string.quoted, defaultProfileName),
                )
                "$backupDescription\n$only"
            },
            onClick = if (backupAllowed) ({ onOpen(BackupSettings) }) else null,
            modifier = if (backupAllowed) Modifier else Modifier.alpha(DISABLED_ALPHA),
        )
        SettingsItem(
            title = stringResource(R.string.screen_title_about),
            summary = stringResource(R.string.setting_description_about),
            onClick = { onOpen(About) },
        )
        ConfirmSetting(
            title = stringResource(R.string.setting_title_reset_settings),
            summary = stringResource(R.string.setting_description_reset_settings),
            dialogTitle = stringResource(R.string.dialog_title_reset_settings),
            dialogBody = stringResource(R.string.dialog_body_reset_settings),
            action = stringResource(R.string.action_reset),
            onConfirm = onReset,
        )
    }
}

@ScreenPreviews
@Composable
private fun SettingsPreview() = PreviewTheme { SettingsSample(backupAllowed = true) }

/** Another profile than the default one: Backup is grayed out, naming the default profile. */
@ScreenPreviews
@Composable
private fun SettingsOtherProfilePreview() = PreviewTheme { SettingsSample(backupAllowed = false) }

@Composable
private fun SettingsSample(backupAllowed: Boolean) {
    SettingsContent(
        onBack = {},
        onOpen = {},
        backupAllowed = backupAllowed,
        defaultProfileName = "Default",
        server = "Navidrome",
        onReset = {},
    )
}
