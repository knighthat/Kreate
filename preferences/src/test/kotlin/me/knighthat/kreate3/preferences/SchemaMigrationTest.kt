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

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchemaMigrationTest {

    private val version = intPreferencesKey(SCHEMA_VERSION_NAME)
    private val name = stringPreferencesKey("name")
    private val ran = mutableListOf<Int>()

    /** Three steps, each records itself and appends its number to `name`. */
    private val migration = SchemaMigration(
        List(3) { step ->
            SchemaStep {
                ran += step
                it[name] = it[name].orEmpty() + step
            }
        }
    )

    @Test
    fun aNewFileIsStampedWithoutRunningSteps() = runBlocking {
        assertTrue(migration.shouldMigrate(emptyPreferences()))

        val migrated = migration.migrate(emptyPreferences())

        assertEquals(3, migrated[version])
        assertTrue(ran.isEmpty())
        assertNull(migrated[name])
    }

    @Test
    fun aFileWithoutVersionRunsEveryStepInOrder() = runBlocking {
        val migrated = migration.migrate(preferencesOf(name to ">"))

        assertEquals(listOf(0, 1, 2), ran)
        assertEquals(">012", migrated[name])
        assertEquals(3, migrated[version])
    }

    @Test
    fun onlyTheMissingStepsRun() = runBlocking {
        val old = preferencesOf(version to 1, name to ">")
        assertTrue(migration.shouldMigrate(old))

        val migrated = migration.migrate(old)

        assertEquals(listOf(1, 2), ran)
        assertEquals(">12", migrated[name])
    }

    @Test
    fun anUpToDateFileIsLeftAlone() = runBlocking {
        assertFalse(migration.shouldMigrate(preferencesOf(version to 3)))
    }

    @Test
    fun aFileFromANewerAppIsLeftAlone() = runBlocking {
        assertFalse(migration.shouldMigrate(preferencesOf(version to 7, name to "future")))
    }

    @Test
    fun withoutStepsAFileIsOnlyStamped() = runBlocking {
        val migrated = SchemaMigration(emptyList()).migrate(preferencesOf(name to "kept"))

        assertEquals(0, migrated[version])
        assertEquals("kept", migrated[name])
    }
}
