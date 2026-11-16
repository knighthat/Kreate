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

package me.knighthat.kreate3.ui.screen.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import me.knighthat.kreate3.AppIcon
import me.knighthat.kreate3.AppLanguage
import me.knighthat.kreate3.R
import me.knighthat.kreate3.TranslationUpdates
import me.knighthat.kreate3.constant.ContrastLevel
import me.knighthat.kreate3.constant.ThemeMode
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.presence.DiscordCovers
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.labelRes
import me.knighthat.kreate3.ui.component.labelsOf
import me.knighthat.kreate3.ui.component.rememberOpenLink
import me.knighthat.kreate3.ui.screen.settings.ChoiceSetting
import me.knighthat.kreate3.ui.screen.settings.SettingsItem
import me.knighthat.kreate3.ui.screen.settings.SettingsPage
import me.knighthat.kreate3.ui.screen.settings.SettingsSection
import me.knighthat.kreate3.ui.screen.settings.SwitchSetting
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectFlowAsState

/** The Crowdin project, where the app is translated and new languages are asked for. */
private const val CROWDIN_PROJECT_URL = "https://translate.kreate.knighthat.me/project/kreate"

/**
 * What the user sees and touches: the theme, the language and the app icon.
 */
@Composable
fun UiSettingsScreen(
    onBack: () -> Unit,
    onOpenLanguages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val themeMode by Preferences.THEME_MODE.collectAsStateWithLifecycle()
    val contrastLevel by Preferences.CONTRAST_LEVEL.collectAsStateWithLifecycle()
    val iconOnly by Preferences.ICON_ONLY.collectAsStateWithLifecycle()
    val playlistInitial by Preferences.PLAYLIST_INITIAL.collectAsStateWithLifecycle()
    val binarySizes by Preferences.BINARY_SIZES.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var appIcon by remember { mutableStateOf(AppIcon.current(context)) }
    val language by Preferences.LANGUAGE.collectAsStateWithLifecycle()
    // Patreon's languages are known once its kept manifest was read
    val online by TranslationUpdates.languages.collectFlowAsState()
    val openLink = rememberOpenLink()

    UiSettingsContent(
        themeMode = themeMode,
        contrastLevel = contrastLevel,
        iconOnly = iconOnly,
        playlistInitial = playlistInitial,
        binarySizes = binarySizes,
        appIcon = appIcon,
        onAppIconChange = {
            it.apply(context)
            appIcon = it
        },
        language = remember(language, online) { AppLanguage.of(language) },
        onOpenLanguages = onOpenLanguages,
        onOpenCrowdin = { openLink(CROWDIN_PROJECT_URL) },
        fontSettings = { FontSettings() },
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
private fun UiSettingsContent(
    themeMode: ThemeMode,
    contrastLevel: ContrastLevel,
    iconOnly: Boolean,
    playlistInitial: Boolean,
    binarySizes: Boolean,
    appIcon: AppIcon,
    onAppIconChange: (AppIcon) -> Unit,
    language: Locale?,
    onOpenLanguages: () -> Unit,
    onOpenCrowdin: () -> Unit,
    fontSettings: @Composable () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(stringResource(R.string.screen_title_ui), onBack, modifier) {
        SettingsSection(stringResource(R.string.setting_title_theme))
        ChoiceSetting(
            title = stringResource(R.string.setting_title_theme),
            value = themeMode,
            options = ThemeMode.entries,
            label = labelsOf(ThemeMode.entries) { it.labelRes },
            onSelect = { Preferences.THEME_MODE.update(it) },
        )
        ChoiceSetting(
            title = stringResource(R.string.setting_title_contrast),
            value = contrastLevel,
            options = ContrastLevel.entries,
            label = labelsOf(ContrastLevel.entries) { it.labelRes },
            onSelect = { Preferences.CONTRAST_LEVEL.update(it) },
        )

        SettingsSection(stringResource(R.string.setting_title_language))
        // The list is a page of its own (LanguageSettingsScreen), "null" follows the device
        SettingsItem(
            title = stringResource(R.string.setting_title_language),
            summary = language?.let(AppLanguage::displayName)
                ?: stringResource(R.string.status_system),
            onClick = onOpenLanguages,
        )
        // Both lead to the Crowdin project, which lists every language. Translating needs a
        // language picked, "System" names none and English is the source
        if (language != null && language.language != "en")
            SettingsItem(
                title = stringResource(R.string.setting_title_translate_language),
                summary = stringResource(R.string.setting_description_translate_language),
                onClick = onOpenCrowdin,
            )
        SettingsItem(
            title = stringResource(R.string.setting_title_request_language),
            summary = stringResource(R.string.setting_description_request_language),
            onClick = onOpenCrowdin,
        )

        SettingsSection(stringResource(R.string.setting_header_layout))
        SwitchSetting(
            title = stringResource(R.string.setting_title_icon_only),
            checked = iconOnly,
            onCheckedChange = { Preferences.ICON_ONLY.update(it) },
            summary = stringResource(R.string.setting_description_icon_only),
        )
        SwitchSetting(
            title = stringResource(R.string.setting_title_playlist_initial),
            checked = playlistInitial,
            onCheckedChange = { Preferences.PLAYLIST_INITIAL.update(it) },
            summary = stringResource(R.string.setting_description_playlist_initial),
        )
        SwitchSetting(
            title = stringResource(R.string.setting_title_binary_sizes),
            checked = binarySizes,
            onCheckedChange = {
                Preferences.BINARY_SIZES.update(it)
                // Discord's cover sizes have their stops in these units
                DiscordCovers.snapSettings(it)
            },
            summary = binarySizesSummary(binarySizes),
        )

        fontSettings()

        SettingsSection(stringResource(R.string.setting_header_launcher))
        ChoiceSetting(
            title = stringResource(R.string.setting_title_app_icon),
            value = appIcon,
            options = AppIcon.entries,
            label = labelsOf(AppIcon.entries) { it.labelRes },
            onSelect = onAppIconChange,
        )
    }
}

@ScreenPreviews
@Composable
private fun UiSettingsPreview() = PreviewTheme {
    UiSettingsContent(
        themeMode = ThemeMode.THUMBNAIL,
        contrastLevel = ContrastLevel.NORMAL,
        iconOnly = false,
        playlistInitial = true,
        binarySizes = true,
        appIcon = AppIcon.BLACK,
        onAppIconChange = {},
        language = Locale.forLanguageTag("vi"),
        onOpenLanguages = {},
        onOpenCrowdin = {},
        fontSettings = {
            FontSettings(
                appName = "Roboto Mono",
                appCustom = true,
                lyricsName = "Roboto Mono",
                lyricsCustom = false,
                onPick = {},
                onReset = {},
            )
        },
        onBack = {},
    )
}

/** How [binary] sizes are counted: 1024 MiB as 1 GiB, or 1000 MB as 1 GB. */
@Composable
private fun binarySizesSummary(binary: Boolean): String {
    val locale = LocalResources.current.configuration.locales[0]
    val count = { n: Int -> "%d".format(locale, n) }
    return if (binary) {
        stringResource(
            R.string.setting_description_binary_sizes,
            stringResource(R.string.mib, count(1024)),
            stringResource(R.string.gib, count(1)),
        )
    } else {
        stringResource(
            R.string.setting_description_binary_sizes,
            stringResource(R.string.mb, count(1000)),
            stringResource(R.string.gb, count(1)),
        )
    }
}
