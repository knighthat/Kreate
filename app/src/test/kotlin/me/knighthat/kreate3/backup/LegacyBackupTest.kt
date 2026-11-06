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

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Reading the archive. Building the database from it runs the bundled SQLite driver, which has
 * no host library: that part is tested in `:database` and stays untested on a device.
 */
@RunWith(RobolectricTestRunner::class)
class LegacyBackupTest {

    private val target: File = Files.createTempDirectory("legacy").toFile()

    private val database = "SQLite format 3\u0000rest"

    private fun manifest(
        source: String = "kreate-legacy",
        format: Int = 1,
        profiles: String = """
            [{"name":"default","database":"profiles/0/data.db","default":true,"schema":36},
             {"name":"Work","database":"profiles/1/data.db"}]
        """.trimIndent(),
    ) =
        """{"format":$format,"source":"$source","appVersion":"later","later":1,"profiles":$profiles}"""

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

    /** Unpacks into an empty folder, as the import does. */
    private fun unpack(entries: Map<String, String>): LegacyManifest {
        target.deleteRecursively()
        target.mkdirs()
        return LegacyBackup.unpack(zip(entries), target)
    }

    private val complete = mapOf(
        "manifest.json" to manifest(),
        "profiles/0/data.db" to database,
        "profiles/1/data.db" to database,
    )

    @After
    fun tearDown() {
        target.deleteRecursively()
    }

    @Test
    fun readsTheProfiles() {
        val read = unpack(complete)

        assertEquals(listOf("default", "Work"), read.profiles.map { it.name })
        assertEquals(listOf(true, false), read.profiles.map { it.default })
        assertEquals(database, File(target, "profiles/1/data.db").readText())
    }

    @Test
    fun skipsWhatItDoesNotKnow() {
        unpack(complete + ("../escape.db" to database) + ("extra/notes.txt" to "x"))

        assertFalse(File(target, "extra/notes.txt").exists())
        assertFalse(File(target.parentFile, "escape.db").exists())
    }

    @Test
    fun refusesWhatIsNotAnArchiveOfTheOldApp() {
        listOf(
            complete - "manifest.json",
            complete + ("manifest.json" to "not json"),
            // A backup of this app
            complete + ("manifest.json" to manifest(source = "kreate")),
        ).forEach {
            assertThrows(BackupException::class.java) { unpack(it) }
        }
    }

    @Test
    fun refusesWhatItCanNotRead() {
        listOf(
            complete + ("manifest.json" to manifest(format = 2)),
            complete - "profiles/1/data.db",
            complete + ("profiles/1/data.db" to "not a database"),
            complete + ("manifest.json" to manifest(profiles = "[]")),
            // Two default profiles
            complete + ("manifest.json" to manifest(
                profiles = """[{"name":"a","database":"profiles/0/data.db","default":true},
                    {"name":"b","database":"profiles/1/data.db","default":true}]"""
            )),
            // The same database twice, a path outside of the layout
            complete + ("manifest.json" to manifest(
                profiles = """[{"name":"a","database":"profiles/0/data.db"},
                    {"name":"b","database":"profiles/0/data.db"}]"""
            )),
            complete + ("manifest.json" to manifest(
                profiles = """[{"name":"a","database":"../profiles/0/data.db"}]"""
            )),
        ).forEach {
            assertThrows(BackupException::class.java) { unpack(it) }
        }
    }

    @Test
    fun tellsTheArchiveFromABackupOfThisApp() {
        assertEquals(true, LegacyBackup.isLegacy(zip(complete)))
        // The manifest is not the first entry
        assertEquals(
            true,
            LegacyBackup.isLegacy(zip(mapOf("profiles/0/data.db" to database) + complete))
        )
        assertEquals(
            false,
            LegacyBackup.isLegacy(
                zip(
                    mapOf(
                        "manifest.json" to """{"format":1,"appVersion":1,"database":3}""",
                        "kreate.db" to database
                    )
                )
            ),
        )
        assertEquals(false, LegacyBackup.isLegacy(zip(mapOf("kreate.db" to database))))
        assertEquals(false, LegacyBackup.isLegacy("not a zip".byteInputStream()))
    }
}
