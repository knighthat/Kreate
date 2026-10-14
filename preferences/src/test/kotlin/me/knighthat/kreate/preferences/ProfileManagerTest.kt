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

package me.knighthat.kreate.preferences

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ProfileManagerTest {

    private class DeleteRecorder : PreferenceStorage {
        val deleted = mutableListOf<String>()
        override suspend fun read(profileId: String) = emptyMap<String, Any>()
        override suspend fun write(profileId: String, changes: Map<String, Any?>) = Unit
        override suspend fun delete(profileId: String) {
            deleted += profileId
        }
    }

    private lateinit var root: File
    private lateinit var scope: CoroutineScope
    private val storage = DeleteRecorder()
    private lateinit var profiles: ProfileManager

    private fun eventually(check: () -> Boolean) = runBlocking {
        withTimeout(5_000) { while (!check()) delay(10) }
    }

    @Before
    fun setUp() {
        root = Files.createTempDirectory("profiles").toFile()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        profiles = ProfileManager(scope, root, storage)
        eventually { profiles.activeId.value == Profile.DEFAULT_ID }
    }

    @After
    fun tearDown() {
        scope.cancel()
        root.deleteRecursively()
    }

    @Test
    fun theDefaultProfileAlwaysExists() = runBlocking {
        assertEquals(setOf(Profile.DEFAULT_ID), profiles.ids.first())
    }

    @Test
    fun anAddedProfileIsNotActive() = runBlocking {
        val id = profiles.add()

        assertEquals(setOf(Profile.DEFAULT_ID, id), profiles.ids.first())
        assertEquals(Profile.DEFAULT_ID, profiles.activeId.value)
    }

    @Test
    fun addingAKnownIdChangesNothing() = runBlocking {
        profiles.add("other")
        profiles.add("other")

        assertEquals(setOf(Profile.DEFAULT_ID, "other"), profiles.ids.first())
    }

    @Test
    fun switchingToAnUnknownProfileIsIgnored() = runBlocking {
        profiles.add("other")
        profiles.switchTo("other")
        eventually { profiles.activeId.value == "other" }

        profiles.switchTo("ghost")
        delay(100)

        assertEquals("other", profiles.activeId.value)
    }

    @Test
    fun theDefaultProfileCannotBeDeleted() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { profiles.delete(Profile.DEFAULT_ID) }
        }
    }

    @Test
    fun deletingTheActiveProfileFallsBackToTheDefault() = runBlocking {
        profiles.add("other")
        profiles.switchTo("other")
        eventually { profiles.activeId.value == "other" }

        assertTrue(profiles.delete("other"))

        eventually { profiles.activeId.value == Profile.DEFAULT_ID }
        assertEquals(setOf(Profile.DEFAULT_ID), profiles.ids.first())
        assertEquals(listOf("other"), storage.deleted)
    }

    @Test
    fun deletingAnUnknownProfileDoesNothing() = runBlocking {
        assertFalse(profiles.delete("ghost"))
        assertTrue(storage.deleted.isEmpty())
    }
}
