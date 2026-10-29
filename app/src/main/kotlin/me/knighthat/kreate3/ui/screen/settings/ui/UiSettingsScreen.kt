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
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import me.knighthat.kreate3.AppIcon
import me.knighthat.kreate3.AppLanguage
import me.knighthat.kreate3.R
import me.knighthat.kreate3.constant.ContrastLevel
import me.knighthat.kreate3.constant.ThemeMode
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.component.labelRes
import me.knighthat.kreate3.ui.component.labelsOf
import me.knighthat.kreate3.ui.screen.settings.ChoiceSetting
import me.knighthat.kreate3.ui.screen.settings.SettingsPage
import me.knighthat.kreate3.ui.screen.settings.SettingsSection
import me.knighthat.kreate3.ui.screen.settings.SwitchSetting
import org.koin.compose.koinInject
import java.util.Locale

/**
 * What the user sees and touches: the theme, the language and the app icon.
 */
@Composable
fun UiSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val themeMode by Preferences.THEME_MODE.collectAsStateWithLifecycle()
    val contrastLevel by Preferences.CONTRAST_LEVEL.collectAsStateWithLifecycle()
    val iconOnly by Preferences.ICON_ONLY.collectAsStateWithLifecycle()
    val playlistInitial by Preferences.PLAYLIST_INITIAL.collectAsStateWithLifecycle()
    val binarySizes by Preferences.BINARY_SIZES.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var appIcon by remember { mutableStateOf(AppIcon.current(context)) }
    val language by Preferences.LANGUAGE.collectAsStateWithLifecycle()
    val scope = koinInject<CoroutineScope>()

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
        language = AppLanguage.of(language),
        languages = listOf(null) + AppLanguage.available,
        // The global scope, a restart for it outlives this screen
        onLanguageChange = { AppLanguage.select(context, scope, it) },
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
    languages: List<Locale?>,
    onLanguageChange: (Locale?) -> Unit,
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
        // Each language in its own words, "null" follows the device
        val system = stringResource(R.string.setting_description_language_status_system)
        ChoiceSetting(
            title = stringResource(R.string.setting_title_language),
            value = language,
            options = languages,
            label = { it?.let(AppLanguage::displayName) ?: system },
            onSelect = onLanguageChange,
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
            onCheckedChange = { Preferences.BINARY_SIZES.update(it) },
            summary = stringResource(R.string.setting_description_binary_sizes),
        )

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
        language = null,
        languages = listOf(null, Locale.ENGLISH, Locale.forLanguageTag("vi")),
        onLanguageChange = {},
        onBack = {},
    )
}
