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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SecretStoreTest {

    private class NoStorage : PreferenceStorage {
        override suspend fun read(profileId: String) = emptyMap<String, Any>()
        override suspend fun write(profileId: String, changes: Map<String, Any?>) = Unit
        override suspend fun delete(profileId: String) = Unit
    }

    /** Preferences files in memory, by profile. */
    private class MemoryStorage(val files: MutableMap<String, MutableMap<String, Any>>) :
        PreferenceStorage {
        override suspend fun read(profileId: String): Map<String, Any> =
            files[profileId].orEmpty().toMap()

        override suspend fun write(profileId: String, changes: Map<String, Any?>) {
            val file = files.getOrPut(profileId) { mutableMapOf() }
            changes.forEach { (name, value) ->
                if (value == null) file.remove(name) else file[name] = value
            }
        }

        override suspend fun delete(profileId: String) {
            files.remove(profileId)
        }
    }

    private lateinit var root: File
    private lateinit var scope: CoroutineScope
    private lateinit var storeScope: CoroutineScope
    private lateinit var profiles: ProfileManager
    private lateinit var store: SecretStore

    private fun secretsFile(profileId: String) =
        File(Profile.directory(root, profileId), "secrets.preferences_pb")

    private fun eventually(check: () -> Boolean) = runBlocking {
        withTimeout(5_000) { while (!check()) delay(10) }
    }

    /** Starts the store over on the same files, like a new launch of the app. */
    private fun restart() {
        if (::storeScope.isInitialized) storeScope.cancel()
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        store = SecretStore(storeScope, profiles, root)
    }

    @Before
    fun setUp() {
        root = Files.createTempDirectory("secrets").toFile()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        profiles = ProfileManager(scope, root, NoStorage())
        eventually { profiles.activeId.value == Profile.DEFAULT_ID }
        restart()
    }

    @After
    fun tearDown() {
        storeScope.cancel()
        scope.cancel()
        root.deleteRecursively()
    }

    @Test
    fun keepsEverySecretInOneFileOfItsOwn() {
        val password = store.secret("password")
        val key = store.secret("api_key")

        password.update("hunter2")
        key.update("abc")

        eventually { password.value == "hunter2" && key.value == "abc" }
        assertTrue(secretsFile(Profile.DEFAULT_ID).isFile)
        assertFalse(
            File(
                Profile.directory(root, Profile.DEFAULT_ID),
                "preferences.preferences_pb"
            ).exists()
        )
    }

    @Test
    fun readsWhatWasSavedBefore() {
        store.secret("password").update("saved")
        eventually { secretsFile(Profile.DEFAULT_ID).isFile }
        restart()

        val password = store.secret("password")
        password.state

        eventually { password.value == "saved" }
    }

    @Test
    fun currentWaitsForTheFileToBeRead() {
        store.secret("password").update("saved")
        eventually { secretsFile(Profile.DEFAULT_ID).isFile }
        restart()

        // Read before anything loaded it, the value right away is still empty
        assertEquals("saved", runBlocking { store.secret("password").current() })
    }

    @Test
    fun emptyValueRemovesTheSecret() {
        val password = store.secret("password")
        password.update("x")
        eventually { password.value == "x" }

        password.clear()
        eventually { password.value.isEmpty() }
        restart()

        val reloaded = store.secret("password")
        reloaded.state
        Thread.sleep(300)
        assertEquals("", reloaded.value)
    }

    @Test
    fun followsTheActiveProfile() {
        val password = store.secret("password")
        password.update("default")
        eventually { password.value == "default" }

        val other = runBlocking { profiles.add("other") }
        runBlocking { profiles.switchTo(other) }
        eventually { profiles.activeId.value == other }
        eventually { password.value.isEmpty() }

        password.update("other")
        eventually { password.value == "other" }

        runBlocking { profiles.switchTo(Profile.DEFAULT_ID) }
        eventually { password.value == "default" }
        assertTrue(secretsFile(other).isFile)
    }

    @Test
    fun deletingAProfileDeletesItsSecrets() {
        val password = store.secret("password")
        val other = runBlocking { profiles.add("other") }
        runBlocking { profiles.switchTo(other) }
        eventually { profiles.activeId.value == other }
        password.update("other")
        eventually { secretsFile(other).isFile }

        runBlocking { profiles.delete(other) }

        eventually { !Profile.directory(root, other).exists() }
    }

    @Test
    fun deletesSecretsOfProfilesDeletedWhileNotRunning() {
        // Stopped first, or it may clean the folder up before the file is in it
        storeScope.cancel()
        val orphan = Profile.directory(root, "gone").apply { mkdirs() }
        File(orphan, "secrets.preferences_pb").writeText("")
        restart()

        eventually { !orphan.exists() }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNamesThatAreNotFileSafe() {
        store.secret("../escape")
    }

    @Test
    fun secretsThatWerePreferencesAreMovedOutOfThePreferencesFile() {
        val storage = MemoryStorage(
            mutableMapOf(
                Profile.DEFAULT_ID to mutableMapOf(
                    "open_subsonic_username" to "me",
                    "youtube_page_id" to "page",
                    "theme_mode" to "DARK",
                )
            )
        )
        // A folder of its own, two stores can't share the files of setUp's
        val movedRoot = Files.createTempDirectory("moved").toFile()
        val movedScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val movedProfiles = ProfileManager(movedScope, movedRoot, storage)
        eventually { movedProfiles.activeId.value == Profile.DEFAULT_ID }
        val store = SecretStore(movedScope, movedProfiles, movedRoot)

        // Waits for the move, which comes before every read
        assertEquals("me", runBlocking { store.secret("open_subsonic_username").current() })
        assertEquals("page", runBlocking { store.secret("youtube_page_id").current() })
        // Other preferences stay where they are
        assertEquals(mapOf("theme_mode" to "DARK"), storage.files[Profile.DEFAULT_ID])
        assertTrue(
            File(
                Profile.directory(movedRoot, Profile.DEFAULT_ID),
                "secrets.preferences_pb"
            ).isFile
        )

        movedScope.cancel()
        movedRoot.deleteRecursively()
    }
}
