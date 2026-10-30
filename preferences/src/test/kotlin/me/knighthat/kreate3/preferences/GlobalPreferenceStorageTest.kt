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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The global file shared by [ProfileManager] and settings like [GlobalPreferences], on disk.
 */
class GlobalPreferenceStorageTest {

    private class NoProfiles : PreferenceStorage {
        override suspend fun read(profileId: String) = emptyMap<String, Any>()
        override suspend fun write(profileId: String, changes: Map<String, Any?>) = Unit
        override suspend fun delete(profileId: String) = Unit
    }

    private lateinit var root: File
    private lateinit var job: Job
    private lateinit var profiles: ProfileManager

    private fun eventually(check: () -> Boolean) = runBlocking {
        withTimeout(5_000) { while (!check()) delay(10) }
    }

    private fun open() {
        job = SupervisorJob()
        profiles = ProfileManager(CoroutineScope(job + Dispatchers.Default), root, NoProfiles())
        eventually { profiles.activeId.value != null }
    }

    /**
     * Like the next launch: the file opened again, from a copy because a DataStore keeps its file
     * for the rest of the process.
     */
    private fun reopen() = runBlocking {
        job.cancelAndJoin()
        moveTo(Files.createTempDirectory("global").toFile())
        open()
    }

    private fun moveTo(newRoot: File) {
        root.copyRecursively(newRoot)
        root.deleteRecursively()
        root = newRoot
    }

    private fun cacheSize() = Preference(
        "song_cache",
        1024L,
        codec<Long, Long>({ it }, { it }),
        profiles.global,
    )

    @Before
    fun setUp() {
        root = Files.createTempDirectory("global").toFile()
        open()
    }

    @After
    fun tearDown() {
        runBlocking { job.cancelAndJoin() }
        root.deleteRecursively()
    }

    @Test
    fun aSettingIsKeptAcrossLaunches() = runBlocking {
        cacheSize().update(2048L)
        assertTrue(profiles.global.flush())

        reopen()

        assertEquals(2048L, cacheSize().value)
    }

    @Test
    fun aSettingAndTheProfileListLiveSideBySide() = runBlocking {
        profiles.add("work")
        profiles.switchTo("work")
        cacheSize().update(2048L)
        assertTrue(profiles.global.flush())

        reopen()

        assertEquals(setOf(Profile.DEFAULT_ID, "work"), profiles.ids.first())
        assertEquals("work", profiles.activeId.value)
        assertEquals(2048L, cacheSize().value)
    }

    @Test
    fun changingProfilesKeepsTheSetting() = runBlocking {
        val size = cacheSize()
        size.update(2048L)
        profiles.global.flush()

        profiles.add("work")
        profiles.switchTo("work")
        profiles.delete("work")

        // Not a profile setting, switching doesn't reload it
        assertEquals(2048L, size.value)
        reopen()
        assertEquals(2048L, cacheSize().value)
        assertEquals(Profile.DEFAULT_ID, profiles.activeId.value)
    }

    @Test
    fun theDefaultLeavesTheFile() = runBlocking {
        val size = cacheSize()
        size.update(2048L)
        profiles.global.flush()
        size.reset()
        profiles.global.flush()

        reopen()

        assertEquals(1024L, cacheSize().value)
        val bytes = File(root, GlobalPreferenceStorage.FILE_NAME).readBytes().decodeToString()
        assertFalse("song_cache" in bytes)
    }

    @Test
    fun aSettingCreatedAfterLoadingHasTheStoredValue() = runBlocking {
        cacheSize().update(2048L)
        profiles.global.flush()

        reopen()
        profiles.global.awaitLoaded()
        // Like a lazy property of GlobalPreferences read long after startup
        delay(100)

        assertEquals(2048L, cacheSize().value)
    }

    @Test
    fun aDamagedFileLosesTheSettings() = runBlocking {
        cacheSize().update(2048L)
        profiles.global.flush()
        job.cancelAndJoin()
        moveTo(Files.createTempDirectory("global").toFile())
        File(root, GlobalPreferenceStorage.FILE_NAME).writeBytes(byteArrayOf(-1, -1, -1, -1))

        open()

        assertEquals(1024L, cacheSize().value)
        cacheSize().update(4096L)
        assertTrue(profiles.global.flush())
        reopen()
        assertEquals(4096L, cacheSize().value)
    }
}
