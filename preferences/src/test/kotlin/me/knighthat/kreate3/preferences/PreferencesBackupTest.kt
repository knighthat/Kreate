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

package me.knighthat.kreate3.preferences

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PreferencesBackupTest {

    private val root: File = Files.createTempDirectory("preferences").toFile()
    private val backup = PreferencesBackup(root)

    private fun write(path: String, text: String) =
        File(root, path).apply { parentFile?.mkdirs() }.writeText(text)

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun backsUpPreferencesButNotSecrets() {
        write("global.preferences_pb", "global")
        write("profiles/default/preferences.preferences_pb", "default")
        write("profiles/default/secrets.preferences_pb", "secret")
        write("setup_completed", "")

        assertEquals(
            setOf("global.preferences_pb", "profiles/default/preferences.preferences_pb"),
            backup.files().keys,
        )
    }

    @Test
    fun acceptsOnlyPreferencePaths() {
        assertTrue(backup.isBackupPath("global.preferences_pb"))
        assertTrue(backup.isBackupPath("profiles/0f1e-ab_C/preferences.preferences_pb"))
        assertFalse(backup.isBackupPath("profiles/default/secrets.preferences_pb"))
        assertFalse(backup.isBackupPath("profiles/../preferences.preferences_pb"))
        assertFalse(backup.isBackupPath("profiles/a/b/preferences.preferences_pb"))
        assertFalse(backup.isBackupPath("/global.preferences_pb"))
    }

    @Test
    fun restoreReplacesEveryProfile() {
        write("global.preferences_pb", "old")
        write("profiles/gone/preferences.preferences_pb", "old")
        val source = Files.createTempDirectory("source").toFile()
        File(source, "global.preferences_pb").writeText("new")
        File(source, "profiles/default").mkdirs()
        File(source, "profiles/default/preferences.preferences_pb").writeText("new")
        File(source, "kreate.db").writeText("not a preference")

        assertTrue(backup.isRestorable(source))
        backup.restore(source)
        source.deleteRecursively()

        assertEquals(
            setOf("global.preferences_pb", "profiles/default/preferences.preferences_pb"),
            backup.files().keys,
        )
        assertEquals("new", File(root, "global.preferences_pb").readText())
        assertFalse(File(root, "kreate.db").exists())
    }
}
