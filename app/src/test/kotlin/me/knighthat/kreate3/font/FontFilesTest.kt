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

package me.knighthat.kreate3.font

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File

/** Fonts the user picked: telling a font from any other file, what is kept, and what a backup holds. */
class FontFilesTest {

    @get:Rule
    val folder = TemporaryFolder()

    /** A font file with the table directory of [tags] and nothing behind it. */
    private fun font(vararg tags: String, magic: Int = 0x00010000): File {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(magic)
            out.writeShort(tags.size)
            out.write(ByteArray(6))
            tags.forEach { tag ->
                out.write(tag.toByteArray(Charsets.ISO_8859_1))
                out.write(ByteArray(12))
            }
        }
        return folder.newFile().apply { writeBytes(bytes.toByteArray()) }
    }

    @Test
    fun aFontIsToldByItsHeader() {
        assertTrue(Sfnt.isFont(font("cmap", "glyf")))
        // OpenType with outlines of its own
        assertTrue(Sfnt.isFont(font("CFF ", magic = 0x4F54544F)))
        assertFalse(Sfnt.isFont(folder.newFile().apply { writeText("not a font at all") }))
        assertFalse(Sfnt.isFont(folder.newFile()))
        assertFalse(Sfnt.isFont(font(magic = 0x00010000)))
    }

    @Test
    fun aVariableFontHasAnFvarTable() {
        assertTrue(Sfnt.isVariable(font("cmap", "fvar", "glyf")))
        assertFalse(Sfnt.isVariable(font("cmap", "glyf")))
        assertFalse(Sfnt.isVariable(folder.newFile().apply { writeText("text") }))
    }

    @Test
    fun aFontIsStoredAsItsPreferenceNamesIt() {
        val font = SavedFont(1730990000000, "Roboto: Mono")

        assertEquals("1730990000000:Roboto: Mono", font.value)
        assertEquals(font, SavedFont.parse(font.value))
        assertNull(SavedFont.parse(""))
        assertNull(SavedFont.parse("Roboto"))
    }

    @Test
    fun onlyFontsOfAProfileAreInABackup() {
        assertTrue(FontFiles.isBackupPath("profiles/default/fonts/app-1730990000000.font"))
        assertTrue(FontFiles.isBackupPath("profiles/9f-2/fonts/lyrics-5.font"))
        assertFalse(FontFiles.isBackupPath("fonts/app-1.font"))
        assertFalse(FontFiles.isBackupPath("profiles/default/fonts/other-1.font"))
        assertFalse(FontFiles.isBackupPath("profiles/default/fonts/app-1.ttf"))
        assertFalse(FontFiles.isBackupPath("profiles/../fonts/app-1.font"))
    }
}
