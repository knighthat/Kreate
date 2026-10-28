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

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DataStorePreferenceStorageTest {

    private val root = Files.createTempDirectory("storage").toFile()
    private val storage = DataStorePreferenceStorage(root)

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun everySupportedTypeIsReadBack() = runBlocking {
        val values = mapOf(
            "string" to "text",
            "int" to 1,
            "long" to 2L,
            "float" to 3f,
            "double" to 4.0,
            "boolean" to true,
            "set" to setOf("a", "b"),
        )

        storage.write("p", values)

        assertEquals(values, storage.read("p"))
    }

    @Test
    fun aNullValueRemovesTheEntry() = runBlocking {
        storage.write("p", mapOf("a" to 1, "b" to 2))

        storage.write("p", mapOf("a" to null))

        assertEquals(mapOf("b" to 2), storage.read("p"))
    }

    @Test
    fun aValueOfAnotherTypeReplacesTheOldOne() = runBlocking {
        storage.write("p", mapOf("a" to 1))

        storage.write("p", mapOf("a" to "one"))

        assertEquals(mapOf("a" to "one"), storage.read("p"))
    }

    @Test
    fun theSchemaVersionIsHiddenAndReserved() = runBlocking {
        storage.write("p", mapOf("a" to 1))

        assertFalse(SCHEMA_VERSION_NAME in storage.read("p"))
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { storage.write("p", mapOf(SCHEMA_VERSION_NAME to 9)) }
        }
        Unit
    }

    @Test
    fun anUnsupportedTypeIsRefused() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { storage.write("p", mapOf("a" to listOf(1))) }
        }
    }

    @Test
    fun profilesHaveTheirOwnValues() = runBlocking {
        storage.write("one", mapOf("a" to 1))
        storage.write("two", mapOf("a" to 2))

        assertEquals(mapOf("a" to 1), storage.read("one"))
        assertEquals(mapOf("a" to 2), storage.read("two"))
    }

    @Test
    fun deletingAProfileRemovesItsFiles() = runBlocking {
        storage.write("p", mapOf("a" to 1))
        val directory = Profile.directory(root, "p")
        assertTrue(directory.isDirectory)

        storage.delete("p")

        assertFalse(directory.exists())
        // The store is opened anew, empty
        assertEquals(emptyMap<String, Any>(), storage.read("p"))
    }

    @Test
    fun aDamagedFileIsReplacedWithAnEmptyOne() = runBlocking {
        var damaged = false
        val storage = DataStorePreferenceStorage(root) { damaged = true }
        File(Profile.directory(root, "p"), DataStorePreferenceStorage.PROFILE_FILE_NAME).apply {
            parentFile!!.mkdirs()
            // Not a protobuf message
            writeBytes(byteArrayOf(-1, -1, -1, -1))
        }

        assertEquals(emptyMap<String, Any>(), storage.read("p"))
        assertTrue(damaged)
        storage.write("p", mapOf("a" to 1))
        assertEquals(mapOf("a" to 1), storage.read("p"))
    }
}
