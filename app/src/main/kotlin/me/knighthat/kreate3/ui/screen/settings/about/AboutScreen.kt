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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import me.knighthat.kreate3.R
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.rememberOpenLink
import me.knighthat.kreate3.ui.screen.settings.SettingsItem
import me.knighthat.kreate3.ui.screen.settings.SettingsPage

private const val SOURCE_CODE_URL = "https://github.com/knighthat/Kreate"
private const val ISSUES_URL = "$SOURCE_CODE_URL/issues"

/**
 * The app's version (a tap shows the notes of the flavor's last stable release, when it has
 * any) and license, links to its source code and issues, and the row opening the
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
    val resources = LocalResources.current
    val changelog by produceState<Changelog?>(null, resources) {
        value = Changelog.read(resources)
    }

    AboutContent(version, changelog, onBack, onOpenLicenses, modifier)
}

@Composable
private fun AboutContent(
    version: String,
    changelog: Changelog?,
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openLink = rememberOpenLink()
    var showChangelog by rememberSaveable { mutableStateOf(false) }

    SettingsPage(stringResource(R.string.screen_title_about), onBack, modifier) {
        SettingsItem(
            title = stringResource(R.string.setting_title_version),
            summary = version,
            onClick = changelog?.takeUnless { it.isEmpty }?.let { { showChangelog = true } },
        )
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
            title = stringResource(R.string.setting_title_open_source_licenses),
            summary = stringResource(R.string.setting_description_open_source_licenses),
            onClick = onOpenLicenses,
        )
    }

    if (showChangelog && changelog != null) {
        ChangelogSheet(changelog, onDismiss = { showChangelog = false })
    }
}


@ScreenPreviews
@Composable
private fun AboutPreview() = PreviewTheme {
    AboutContent(
        version = "3.1.0",
        changelog = Changelog(
            version = "3.1.0",
            adds = listOf("Song downloads with offline covers"),
            fixes = listOf("Crash when a paused song is disliked"),
        ),
        onBack = {},
        onOpenLicenses = {},
    )
}
