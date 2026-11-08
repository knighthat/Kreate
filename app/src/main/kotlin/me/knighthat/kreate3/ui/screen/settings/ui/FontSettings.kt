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

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.R
import me.knighthat.kreate3.font.FontFiles
import me.knighthat.kreate3.font.FontSlot
import me.knighthat.kreate3.notification.Notifier
import me.knighthat.kreate3.preferences.ProfileManager
import me.knighthat.kreate3.ui.navigation.FontPreview
import me.knighthat.kreate3.ui.navigation.rememberRouteResult
import me.knighthat.kreate3.ui.screen.settings.ButtonSetting
import me.knighthat.kreate3.ui.screen.settings.SettingsSection
import me.knighthat.kreate3.ui.theme.INTER_NAME
import me.knighthat.kreate3.ui.theme.preference
import me.knighthat.kreate3.ui.theme.rememberInstalledFont
import org.koin.compose.koinInject
import java.io.IOException

private const val TAG = "FontSettings"

/** What a font file's name ends in. */
private val EXTENSIONS = setOf("ttf", "otf")

/** What a picker is asked for: fonts, which many apps report as no type in particular. */
private val FONT_TYPES = arrayOf(
    "font/*",
    "application/x-font-ttf",
    "application/x-font-otf",
    "application/x-font-opentype",
    "application/vnd.ms-opentype",
    "application/octet-stream",
)

/**
 * The fonts of the app and of the lyrics: a tap on one picks a font file, which [FontPreviewScreen]
 * shows before Save keeps it for the profile. Reset, there once a font is kept, goes back to the default, Inter for the app and
 * the app's font for the lyrics.
 */
@Composable
fun FontSettings() {
    val files = koinInject<FontFiles>()
    val profiles = koinInject<ProfileManager>()
    val notifier = koinInject<Notifier>()
    // Keeping a font is over once the page is
    val global = koinInject<CoroutineScope>()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current

    val app = rememberInstalledFont(FontSlot.APP)
    val lyrics = rememberInstalledFont(FontSlot.LYRICS)

    val preview = rememberRouteResult<StagedFont, StagedFont>(::FontPreview) { staged ->
        global.launch {
            try {
                val profile = checkNotNull(profiles.activeId.first { it != null })
                val saved = withContext(Dispatchers.IO) {
                    files.keep(profile, staged.slot, staged.file, staged.name)
                }
                staged.slot.preference.update(saved.value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e, TAG) { "Couldn't keep the ${staged.slot} font" }
                notifier.post(
                    resources.getString(
                        R.string.error_save_font,
                        resources.getString(R.string.quoted, staged.name),
                    )
                )
            }
        }
    }

    // The slot being picked for, kept over the picker's page
    var picking by rememberSaveable { mutableStateOf<FontSlot?>(null) }
    val pick = rememberLauncherForActivityResult(OpenDocument()) { uri ->
        val slot = picking
        picking = null
        if (uri == null || slot == null) return@rememberLauncherForActivityResult

        scope.launch {
            val file = withContext(Dispatchers.IO) { context.displayName(uri) }.orEmpty()
            val extension = file.substringAfterLast('.', "").lowercase()
            if (extension !in EXTENSIONS) {
                notifier.post(resources.getString(R.string.error_font_wrong_type))
                return@launch
            }

            try {
                val staged = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: throw IOException("Can't read the picked font")
                    files.stage(input)
                }
                preview.launch(StagedFont(slot, staged, file.substringBeforeLast('.')))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e, TAG) { "Couldn't read the picked font" }
                notifier.post(
                    resources.getString(
                        R.string.error_read_font,
                        resources.getString(R.string.quoted, file),
                    )
                )
            }
        }
    }

    val appName = app?.font?.name ?: INTER_NAME
    FontSettings(
        appName = appName,
        appCustom = app != null,
        // Lyrics follow the app's font until they have one of their own
        lyricsName = lyrics?.font?.name ?: appName,
        lyricsCustom = lyrics != null,
        onPick = {
            picking = it
            pick.launch(FONT_TYPES)
        },
        onReset = { slot ->
            global.launch {
                try {
                    val profile = checkNotNull(profiles.activeId.first { it != null })
                    slot.preference.update("")
                    withContext(Dispatchers.IO) { files.forget(profile, slot) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.w(e, TAG) { "Couldn't delete the $slot font" }
                }
            }
        },
    )
}

/**
 * [FontSettings] with the values given.
 *
 * @param appCustom whether the app's font is one the user picked, which Reset can take away and
 * which shows the button
 */
@Composable
fun FontSettings(
    appName: String,
    appCustom: Boolean,
    lyricsName: String,
    lyricsCustom: Boolean,
    onPick: (FontSlot) -> Unit,
    onReset: (FontSlot) -> Unit,
) {
    SettingsSection(stringResource(R.string.setting_header_font))
    ButtonSetting(
        title = stringResource(R.string.setting_title_app_font),
        summary = stringResource(R.string.setting_description_font, appName),
        onClick = { onPick(FontSlot.APP) },
        action = stringResource(R.string.action_reset),
        onAction = { onReset(FontSlot.APP) },
        showAction = appCustom,
    )
    ButtonSetting(
        title = stringResource(R.string.setting_title_lyrics_font),
        summary = stringResource(R.string.setting_description_font, lyricsName),
        onClick = { onPick(FontSlot.LYRICS) },
        action = stringResource(R.string.action_reset),
        onAction = { onReset(FontSlot.LYRICS) },
        showAction = lyricsCustom,
    )
}

/** The name of the file at [uri], `null` when its provider doesn't tell. */
private fun Context.displayName(uri: Uri): String? =
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null }
