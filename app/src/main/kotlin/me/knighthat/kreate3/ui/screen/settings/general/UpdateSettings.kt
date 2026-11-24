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

package me.knighthat.kreate3.ui.screen.settings.general

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import me.knighthat.kreate3.BuildConfig
import me.knighthat.kreate3.R
import me.knighthat.kreate3.constant.AutoUpdateMode
import me.knighthat.kreate3.preferences.GlobalPreferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.ui.component.labelRes
import me.knighthat.kreate3.ui.component.labelsOf
import me.knighthat.kreate3.ui.screen.settings.ChoiceSetting
import me.knighthat.kreate3.ui.screen.settings.DISABLED_ALPHA
import me.knighthat.kreate3.ui.screen.settings.SettingsItem
import me.knighthat.kreate3.ui.screen.settings.SettingsSection
import me.knighthat.kreate3.ui.screen.settings.SettingsSummary
import me.knighthat.kreate3.update.AppUpdater
import me.knighthat.kreate3.update.UpdateState
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectFlow

/**
 * The Updates section of the General settings, in a build that updates itself ([updater]); the
 * others show it with [UpdatesNotAvailable].
 * Picking Automatic while the app may not install apps opens the system's page for it first, and
 * the mode changes only once that is allowed. The mode is a [GlobalPreferences], only the default
 * profile changes it; every profile may check.
 *
 * @param appWide whether the profile may change the mode, shown read-only otherwise
 * @param defaultProfileName named in the note of what only the default profile changes, `null`
 * while it loads and the note stays hidden
 */
@Composable
fun UpdateSettings(updater: AppUpdater, appWide: Boolean, defaultProfileName: String?) {
    val mode by GlobalPreferences.AUTO_UPDATE_MODE.collectAsStateWithLifecycle()
    val state by updater.state.collectFlow()

    // Back from the system's page: Automatic only if the app may install apps now
    val askPermission = rememberLauncherForActivityResult(StartActivityForResult()) {
        if (updater.canInstall) GlobalPreferences.AUTO_UPDATE_MODE.update(AutoUpdateMode.AUTOMATIC)
    }

    UpdateSettings(
        mode = mode,
        checking = (state as? UpdateState.Checking)?.manual == true,
        appWide = appWide,
        defaultProfileName = defaultProfileName,
        onSelect = { selected ->
            val permission =
                if (selected == AutoUpdateMode.AUTOMATIC && !updater.canInstall)
                    updater.installPermissionIntent()
                else
                    null
            if (permission != null)
                askPermission.launch(permission)
            else
                GlobalPreferences.AUTO_UPDATE_MODE.update(selected)
        },
        onCheck = updater::checkNow,
    )
}

/**
 * The Updates section in a build that doesn't update itself: both rows dimmed, saying it isn't
 * available in this build, the mode shown as Off (what `AutoUpdateOff` keeps it at).
 */
@Composable
fun UpdatesNotAvailable() {
    val unavailable = stringResource(
        R.string.status_not_available_in,
        BuildConfig.FLAVOR_platform.uppercase()
    )

    SettingsSection(stringResource(R.string.setting_header_updates))
    ChoiceSetting(
        title = stringResource(R.string.setting_title_auto_update_mode),
        value = AutoUpdateMode.OFF,
        options = AutoUpdateMode.entries,
        label = labelsOf(AutoUpdateMode.entries) { it.labelRes },
        summary = unavailable,
        onSelect = {},
        enabled = false,
    )
    SettingsItem(
        title = stringResource(R.string.setting_title_check_for_update),
        modifier = Modifier.alpha(DISABLED_ALPHA),
        summary = unavailable,
    )
}

/**
 * What [UpdateSettings] shows, without the updater: the mode and Check for update, which says
 * Checking… and doesn't take taps while [checking].
 */
@Composable
internal fun UpdateSettings(
    mode: AutoUpdateMode,
    checking: Boolean,
    appWide: Boolean,
    defaultProfileName: String?,
    onSelect: (AutoUpdateMode) -> Unit,
    onCheck: () -> Unit,
) {
    SettingsSection(stringResource(R.string.setting_header_updates))
    if (!appWide && defaultProfileName != null)
        SettingsSummary(
            stringResource(
                R.string.setting_description_default_profile_only,
                stringResource(R.string.quoted, defaultProfileName)
            )
        )
    ChoiceSetting(
        title = stringResource(R.string.setting_title_auto_update_mode),
        value = mode,
        options = AutoUpdateMode.entries,
        label = labelsOf(AutoUpdateMode.entries) { it.labelRes },
        summary = if (mode == AutoUpdateMode.OFF) {
            stringResource(mode.descriptionRes, stringResource(R.string.app_name))
        } else {
            stringResource(mode.descriptionRes)
        },
        onSelect = onSelect,
        enabled = appWide,
    )
    SettingsItem(
        title = stringResource(R.string.setting_title_check_for_update),
        summary = if (checking) {
            stringResource(R.string.setting_description_check_for_update_status_checking)
        } else {
            // Without the flavor after +, it names the build and not the version
            stringResource(
                R.string.setting_description_check_for_update,
                BuildConfig.VERSION_NAME.substringBefore('+')
            )
        },
        onClick = if (checking) null else onCheck,
    )
}

/** What the mode does, the dialog only names it. */
private val AutoUpdateMode.descriptionRes: Int
    @StringRes get() = when (this) {
        AutoUpdateMode.OFF -> R.string.setting_description_auto_update_mode_off
        AutoUpdateMode.AUTOMATIC -> R.string.setting_description_auto_update_mode_automatic
        AutoUpdateMode.ASK -> R.string.setting_description_auto_update_mode_ask
    }
