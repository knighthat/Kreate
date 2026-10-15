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

package me.knighthat.kreate3.backup

import me.knighthat.kreate3.database.DatabaseBackup
import me.knighthat.kreate3.preferences.PreferencesBackup
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class BackupTest {

    private val context = RuntimeEnvironment.getApplication()
    private val preferences = PreferencesBackup(context)
    private val target: File = Files.createTempDirectory("restore").toFile()
    private val pending = File(context.noBackupFilesDir, Backup.PENDING_DIRECTORY)
    private val rollback = File(context.cacheDir, Backup.ROLLBACK_FILE)

    private val manifest = """
        {"format":1,"appVersion":1,"database":${DatabaseBackup.VERSION},
        "globalPreferences":${PreferencesBackup.GLOBAL_VERSION},
        "profilePreferences":${PreferencesBackup.PROFILE_VERSION},"later":"ignored"}
    """.trimIndent()

    private val complete = mapOf(
        "manifest.json" to manifest,
        "kreate.db" to "SQLite format 3\u0000rest",
        "global.preferences_pb" to "global",
        "profiles/default/preferences.preferences_pb" to "default",
    )

    private fun zip(entries: Map<String, String>) =
        ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                entries.forEach { (path, text) ->
                    zip.putNextEntry(ZipEntry(path))
                    zip.write(text.toByteArray())
                    zip.closeEntry()
                }
            }
        }.toByteArray().inputStream()

    private fun unpack(entries: Map<String, String>) =
        Backup.unpack(zip(entries), target, preferences)

    private fun assertRefused(entries: Map<String, String>) {
        assertThrows(BackupException::class.java) { unpack(entries) }
    }

    /** A restore kept for the next start that fails, it has no database to move in place. */
    private fun keepBrokenRestore() {
        pending.mkdirs()
        File(pending, "global.preferences_pb").writeText("broken")
    }

    @After
    fun tearDown() {
        target.deleteRecursively()
        pending.deleteRecursively()
        rollback.delete()
    }

    @Test
    fun unpacksACompleteBackup() {
        unpack(complete)

        assertEquals(
            "default",
            File(target, "profiles/default/preferences.preferences_pb").readText()
        )
        assertTrue(File(target, "kreate.db").isFile)
    }

    @Test
    fun refusesPathsOutsideOfTheBackup() {
        val outside = File(target.parentFile, "outside.txt")
        assertRefused(complete + ("../outside.txt" to "x"))
        assertRefused(complete + ("profiles/default/secrets.preferences_pb" to "x"))
        assertFalse(outside.exists())
    }

    @Test
    fun refusesWhatIsNotABackup() {
        assertRefused(emptyMap())
        assertRefused(complete - "manifest.json")
        assertRefused(complete + ("manifest.json" to "not json"))
        assertRefused(complete - "kreate.db")
        assertRefused(complete + ("kreate.db" to "not a database"))
        assertRefused(complete - "global.preferences_pb")
        assertThrows(BackupException::class.java) {
            Backup.unpack("not a zip".byteInputStream(), target, preferences)
        }
    }

    @Test
    fun refusesBackupsOfNewerVersions() {
        assertRefused(
            complete + ("manifest.json" to manifest.replace(
                "\"format\":1",
                "\"format\":2"
            ))
        )
        assertRefused(
            complete + ("manifest.json" to manifest.replace(
                "\"database\":${DatabaseBackup.VERSION}",
                "\"database\":${DatabaseBackup.VERSION + 1}",
            ))
        )
    }

    @Test
    fun failedRestoreRollsBackOnTheNextStart() {
        keepBrokenRestore()
        rollback.outputStream().use { zip(complete).copyTo(it) }

        Backup.applyPendingRestore(context)
        assertFalse(rollback.exists())
        assertEquals("global", File(pending, "global.preferences_pb").readText())

        Backup.applyPendingRestore(context)
        assertFalse(pending.exists())
        assertEquals("global", File(context.filesDir, "global.preferences_pb").readText())
        assertEquals(
            "default",
            File(context.filesDir, "profiles/default/preferences.preferences_pb").readText(),
        )
        assertTrue(context.getDatabasePath("kreate.db").readText().startsWith("SQLite format 3"))
    }

    @Test
    fun failedRollBackIsNotTriedAgain() {
        keepBrokenRestore()

        Backup.applyPendingRestore(context)

        assertFalse(pending.exists())
    }

    @Test
    fun rollbackWithoutARestoreIsDeleted() {
        rollback.writeText("left by a stage that didn't finish")

        Backup.applyPendingRestore(context)

        assertFalse(rollback.exists())
        assertFalse(pending.exists())
    }
}
