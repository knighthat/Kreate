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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * [PreferenceEngine] together with the [Preference]s it serves.
 */
class PreferenceEngineTest {

    private class MemoryStorage : PreferenceStorage {
        val profiles = ConcurrentHashMap<String, Map<String, Any>>()
        val writes: MutableList<Pair<String, Map<String, Any?>>> =
            Collections.synchronizedList(mutableListOf())
        var failReads = false

        override suspend fun read(profileId: String): Map<String, Any> {
            if (failReads) throw IllegalStateException("Unreadable")
            return profiles[profileId].orEmpty()
        }

        override suspend fun write(profileId: String, changes: Map<String, Any?>) {
            writes += profileId to changes
            val current = profiles[profileId].orEmpty().toMutableMap()
            changes.forEach { (name, value) ->
                if (value == null) current -= name else current[name] = value
            }
            profiles[profileId] = current
        }

        override suspend fun delete(profileId: String) {
            profiles -= profileId
        }
    }

    private lateinit var root: File
    private lateinit var scope: CoroutineScope
    private val storage = MemoryStorage()
    private lateinit var profiles: ProfileManager
    private lateinit var engine: PreferenceEngine

    private fun eventually(check: () -> Boolean) = runBlocking {
        withTimeout(5_000) { while (!check()) delay(10) }
    }

    private fun volume() = Preference("volume", 5, codec<Int, Int>({ it }, { it }), engine)

    private fun start() {
        profiles = ProfileManager(scope, root, storage)
        engine = PreferenceEngine(profiles, scope)
        runBlocking { withTimeout(5_000) { engine.awaitLoaded() } }
    }

    @Before
    fun setUp() {
        root = Files.createTempDirectory("engine").toFile()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @After
    fun tearDown() {
        scope.cancel()
        root.deleteRecursively()
    }

    @Test
    fun theValueOfTheActiveProfileIsLoaded() {
        storage.profiles[Profile.DEFAULT_ID] = mapOf("volume" to 7)
        start()

        assertEquals(7, volume().value)
    }

    @Test
    fun anUnreadableValueFallsBackToTheDefault() {
        storage.profiles[Profile.DEFAULT_ID] = mapOf("volume" to "loud")
        start()

        assertEquals(5, volume().value)
    }

    @Test
    fun anUnreadableFileGivesDefaults() {
        storage.failReads = true
        start()

        assertEquals(5, volume().value)
    }

    @Test
    fun aChangeShowsAtOnceAndIsSavedLater() {
        start()
        val volume = volume()

        volume.update(8)

        assertEquals(8, volume.value)
        eventually { storage.profiles[Profile.DEFAULT_ID] == mapOf("volume" to 8) }
    }

    @Test
    fun rapidChangesAreSavedOnce() {
        start()
        val volume = volume()

        volume.update(6)
        volume.update { it + 1 }
        volume.update(8)
        eventually { storage.writes.isNotEmpty() }
        Thread.sleep(400)

        assertEquals(listOf(Profile.DEFAULT_ID to mapOf("volume" to 8)), storage.writes.toList())
    }

    @Test
    fun flushingSavesWithoutWaiting() {
        start()
        val volume = volume()

        volume.update(9)
        runBlocking { engine.flush() }

        assertEquals(mapOf("volume" to 9), storage.profiles[Profile.DEFAULT_ID])
    }

    @Test
    fun theDefaultIsNotStored() {
        storage.profiles[Profile.DEFAULT_ID] = mapOf("volume" to 7)
        start()
        val volume = volume()

        volume.reset()
        runBlocking { engine.flush() }

        assertEquals(listOf(Profile.DEFAULT_ID to mapOf("volume" to null)), storage.writes.toList())
        assertEquals(emptyMap<String, Any>(), storage.profiles[Profile.DEFAULT_ID])
    }

    @Test
    fun settingTheSameValueSavesNothing() {
        start()
        val volume = volume()

        volume.update(5)
        volume.update { it }
        runBlocking { engine.flush() }

        assertTrue(storage.writes.isEmpty())
    }

    @Test
    fun aChangeMadeBeforeASwitchIsSavedToItsProfile() {
        storage.profiles["other"] = mapOf("volume" to 2)
        start()
        val volume = volume()

        volume.update(9)
        runBlocking {
            profiles.add("other")
            profiles.switchTo("other")
        }

        eventually { volume.value == 2 }
        assertEquals(mapOf("volume" to 9), storage.profiles[Profile.DEFAULT_ID])
        assertEquals(listOf(Profile.DEFAULT_ID), storage.writes.map { it.first })
    }

    @Test
    fun aProfileWithoutTheValueShowsTheDefault() {
        storage.profiles[Profile.DEFAULT_ID] = mapOf("volume" to 7)
        start()
        val volume = volume()

        runBlocking {
            profiles.add("other")
            profiles.switchTo("other")
        }

        eventually { volume.value == 5 }
    }
}
