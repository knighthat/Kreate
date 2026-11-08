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

package me.knighthat.kreate3.ui.theme

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.knighthat.kreate3.font.FontFiles
import me.knighthat.kreate3.font.FontSlot
import me.knighthat.kreate3.font.SavedFont
import me.knighthat.kreate3.font.Sfnt
import me.knighthat.kreate3.preferences.Preference
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.ProfileManager
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import java.io.File

/** The setting of [FontSlot], it stores a [SavedFont]. */
val FontSlot.preference: Preference<String>
    get() = when (this) {
        FontSlot.APP -> Preferences.APP_FONT
        FontSlot.LYRICS -> Preferences.LYRICS_FONT
    }

/** A font the active profile picked, and the file it is kept in. */
data class InstalledFont(val font: SavedFont, val file: File)

/**
 * The font the active profile picked for [slot], `null` while it has none or its file is gone, like
 * after a backup from before it was restored.
 */
@Composable
fun rememberInstalledFont(slot: FontSlot): InstalledFont? {
    val files = koinInject<FontFiles>()
    val profile by koinInject<ProfileManager>().activeId.collectAsStateWithLifecycle()
    val value by slot.preference.collectAsStateWithLifecycle()

    return remember(profile, value) {
        val font = SavedFont.parse(value)
        val id = profile
        if (font == null || id == null)
            null
        else
            InstalledFont(font, files.file(id, slot, font)).takeIf { it.file.isFile }
    }
}

/**
 * The font in [file], with all its weights when it is a variable font; a static one has one, a
 * bold or italic text is drawn from it by the system. Reads the first bytes of the file.
 */
@OptIn(ExperimentalTextApi::class)
fun fontFamilyOf(file: File): FontFamily =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Sfnt.isVariable(file)) {
        FontFamily(
            weights.map { weight ->
                Font(
                    file = file,
                    weight = weight,
                    style = FontStyle.Normal,
                    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
                )
            }
        )
    } else {
        FontFamily(Font(file))
    }

/**
 * The font lyrics are drawn in, `null` follows the theme's. Set by `KreateApp` from
 * [Preferences.LYRICS_FONT].
 */
val LocalLyricsFont = staticCompositionLocalOf<FontFamily?> { null }

/** This style in the lyrics' font, see [LocalLyricsFont]. */
@Composable
fun TextStyle.forLyrics(): TextStyle =
    LocalLyricsFont.current?.let { copy(fontFamily = it) } ?: this
