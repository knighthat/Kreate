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

package me.knighthat.kreate3.ui.screen.settings.about

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import me.knighthat.kreate3.R
import me.knighthat.kreate3.ui.component.LocalNotifier
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.screen.settings.SettingsItem
import me.knighthat.kreate3.ui.screen.settings.SettingsPage

private const val SOURCE_CODE_URL = "https://github.com/knighthat/Kreate"
private const val ISSUES_URL = "$SOURCE_CODE_URL/issues"

/**
 * The app's version and license, links to its source code and issues, and the row opening the
 * libraries it is built with.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val version = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }

    AboutContent(version, onBack, onOpenLicenses, modifier)
}

@Composable
private fun AboutContent(
    version: String,
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openLink = rememberOpenLink()

    SettingsPage(stringResource(R.string.screen_title_about), onBack, modifier) {
        SettingsItem(title = stringResource(R.string.setting_title_version), summary = version)
        SettingsItem(
            title = stringResource(R.string.setting_title_license),
            summary = stringResource(R.string.setting_description_license),
        )
        SettingsItem(
            title = stringResource(R.string.setting_title_source_code),
            summary = stringResource(R.string.setting_description_source_code),
            onClick = { openLink(SOURCE_CODE_URL) },
        )
        SettingsItem(
            title = stringResource(R.string.setting_title_report_issue),
            summary = stringResource(R.string.setting_description_report_issue),
            onClick = { openLink(ISSUES_URL) },
        )
        SettingsItem(
            title = stringResource(R.string.screen_title_open_source_licenses),
            summary = stringResource(R.string.setting_description_open_source_licenses),
            onClick = onOpenLicenses,
            // The row opens a page of its own
            trailing = {
                Icon(
                    painterResource(R.drawable.chevron_right),
                    contentDescription = null
                )
            },
        )
    }
}

/**
 * Opens a link in another app, or says no app can when none is installed (an emulator may have
 * no browser).
 */
@Composable
internal fun rememberOpenLink(): (String) -> Unit {
    val uriHandler = LocalUriHandler.current
    val notifier = LocalNotifier.current
    val noBrowser = stringResource(R.string.error_no_browser)
    return remember(uriHandler, notifier, noBrowser) {
        { uri ->
            try {
                uriHandler.openUri(uri)
            } catch (_: IllegalArgumentException) {
                // How the handler says no app can open it
                notifier.post(noBrowser)
            }
        }
    }
}

@ScreenPreviews
@Composable
private fun AboutPreview() = PreviewTheme {
    AboutContent(version = "3.0.0", onBack = {}, onOpenLicenses = {})
}
