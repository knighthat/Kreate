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

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNamesThatAreNotFileSafe() {
        store.secret("../escape")
    }
}
