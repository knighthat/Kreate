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

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import me.knighthat.kreate3.AppLanguage
import me.knighthat.kreate3.BuildConfig
import me.knighthat.kreate3.R
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.screen.settings.RadioSetting
import me.knighthat.kreate3.ui.screen.settings.SettingsPage
import org.koin.compose.koinInject
import java.util.Locale

/**
 * The app's language, opened from [UiSettingsScreen]: "System" and the languages built into the
 * APK (the merged `values-*` folders), each in its own words. Builds without
 * [BuildConfig.PREBUILT_LANGUAGES] (patreon) list none.
 */
@Composable
fun LanguageSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val language by Preferences.LANGUAGE.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = koinInject<CoroutineScope>()

    LanguageSettingsContent(
        language = AppLanguage.of(language),
        languages = if (BuildConfig.PREBUILT_LANGUAGES) listOf(null) + AppLanguage.available else emptyList(),
        // The global scope, a restart for it outlives this screen
        onSelect = { AppLanguage.select(context, scope, it) },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * @param languages what can be picked, "null" follows the device; empty shows that there is none
 */
@Composable
private fun LanguageSettingsContent(
    language: Locale?,
    languages: List<Locale?>,
    onSelect: (Locale?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) = SettingsPage(stringResource(R.string.setting_title_language), onBack, modifier) {
    if (languages.isEmpty()) {
        Text(
            text = "No translation available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    } else {
        val system = stringResource(R.string.status_system)
        RadioSetting(
            value = language,
            options = languages,
            label = { it?.let(AppLanguage::displayName) ?: system },
            onSelect = onSelect,
        )
    }
}

@ScreenPreviews
@Composable
private fun LanguageSettingsPreview() = PreviewTheme {
    LanguageSettingsContent(
        language = Locale.forLanguageTag("vi"),
        languages = listOf(null, Locale.ENGLISH, Locale.forLanguageTag("vi")),
        onSelect = {},
        onBack = {},
    )
}
