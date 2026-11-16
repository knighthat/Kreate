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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import me.knighthat.kreate3.AppLanguage
import me.knighthat.kreate3.R
import me.knighthat.kreate3.TranslationUpdates
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.ui.component.PreviewTheme
import me.knighthat.kreate3.ui.component.ScreenPreviews
import me.knighthat.kreate3.ui.screen.settings.RadioSetting
import me.knighthat.kreate3.ui.screen.settings.SettingsPage
import org.koin.compose.koinInject
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectFlowAsState

/**
 * The app's language, opened from [UiSettingsScreen]: "System" and [AppLanguage.available], each
 * in its own words. Foss and github have the languages built into the APK; patreon has those of
 * Crowdin's manifest, a new one fetched on opening once the kept one is 48 h old (see
 * [TranslationUpdates]), and downloads a language's texts once it is picked.
 */
@Composable
fun LanguageSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val language by Preferences.LANGUAGE.collectAsStateWithLifecycle()
    // Read so the list follows a new manifest
    val online by TranslationUpdates.languages.collectFlowAsState()
    val context = LocalContext.current
    val scope = koinInject<CoroutineScope>()
    var refreshing by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        TranslationUpdates.refresh()
        refreshing = false
    }

    LanguageSettingsContent(
        language = remember(language, online) { AppLanguage.of(language) },
        languages = remember(online) { AppLanguage.available },
        refreshing = refreshing,
        // The global scope, a restart for it outlives this screen
        onSelect = { AppLanguage.select(context, scope, it) },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * @param languages what can be picked besides "System" (`null`, the device's)
 * @param refreshing whether the languages may still change, a spinner shows while English is the
 * only one
 */
@Composable
private fun LanguageSettingsContent(
    language: Locale?,
    languages: List<Locale>,
    refreshing: Boolean,
    onSelect: (Locale?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) = SettingsPage(stringResource(R.string.setting_title_language), onBack, modifier) {
    if (languages.size > 1) {
        val system = stringResource(R.string.status_system)
        RadioSetting(
            value = language,
            options = listOf(null) + languages,
            label = { it?.let(AppLanguage::displayName) ?: system },
            onSelect = onSelect,
        )
    } else if (refreshing) {
        Box(Modifier
            .fillMaxWidth()
            .padding(16.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        Text(
            text = "No translation available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@ScreenPreviews
@Composable
private fun LanguageSettingsPreview() = PreviewTheme {
    LanguageSettingsContent(
        language = Locale.forLanguageTag("vi"),
        languages = listOf(Locale.ENGLISH, Locale.forLanguageTag("vi")),
        refreshing = false,
        onSelect = {},
        onBack = {},
    )
}
