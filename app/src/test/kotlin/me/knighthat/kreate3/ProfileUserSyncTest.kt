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

package me.knighthat.kreate3

import android.content.ContextWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.knighthat.kreate3.database.model.User
import me.knighthat.kreate3.database.repository.UserRepository
import me.knighthat.kreate3.preferences.Profile
import me.knighthat.kreate3.preferences.ProfileManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.nio.file.Files
import java.util.Collections
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class ProfileUserSyncTest {

    private class Users : UserRepository {
        val users = MutableStateFlow(mapOf<String, User>())
        val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override fun all() = users.map { it.values.toList() }
        override fun find(id: String) = users.map { it[id] }
        override suspend fun save(user: User) {
            calls += "save ${user.id} ${user.name}"
            users.update { it + (user.id to user) }
        }

        override suspend fun rename(id: String, name: String) = Unit
        override suspend fun markActive(id: String) {
            calls += "active $id"
            users.update { it + (id to it.getValue(id).copy(activeSince = ACTIVE)) }
        }

        override suspend fun markSeen(id: String) {
            calls += "seen $id"
        }

        override suspend fun delete(id: String) {
            calls += "delete $id"
            users.update { it - id }
        }
    }

    private val root: File = Files.createTempDirectory("profiles").toFile()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val users = Users()
    private lateinit var profiles: ProfileManager
    private lateinit var sync: ProfileUserSync

    private fun eventually(check: () -> Boolean) = runBlocking {
        withTimeout(5_000) { while (!check()) delay(10) }
    }

    private fun start() {
        // Every test has files of its own, the profiles' stores can't be closed between tests
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getFilesDir() = root
        }
        profiles = ProfileManager(scope, context)
        sync = ProfileUserSync(profiles, users, scope)
        eventually { sync.id.value == Profile.DEFAULT_ID }
    }

    @After
    fun tearDown() {
        scope.cancel()
        root.deleteRecursively()
    }

    @Test
    fun theDefaultProfileGetsAUserOnItsFirstStart() {
        start()

        eventually { "active ${Profile.DEFAULT_ID}" in users.calls }
        assertEquals("Default", users.users.value[Profile.DEFAULT_ID]?.name)
    }

    @Test
    fun aStartIsNotANewActivityPeriod() {
        users.users.value = mapOf(
            Profile.DEFAULT_ID to User(
                Profile.DEFAULT_ID,
                "Me",
                activeSince = Instant.fromEpochMilliseconds(5)
            )
        )

        start()
        Thread.sleep(200)

        assertEquals(emptyList<String>(), users.calls.toList())
        assertEquals("Me", users.users.value[Profile.DEFAULT_ID]?.name)
    }

    @Test
    fun switchingEndsOneProfileAndStartsTheOther() {
        start()
        val other = runBlocking { sync.createProfile("Work")!! }
        users.calls.clear()

        runBlocking { profiles.switchTo(other) }

        eventually { sync.id.value == other }
        eventually { users.calls.size == 2 }
        assertEquals(listOf("seen ${Profile.DEFAULT_ID}", "active $other"), users.calls.toList())
    }

    @Test
    fun aCreatedProfileHasItsNameAndIsNotActive() = runBlocking {
        start()

        val id = sync.createProfile("Work")!!

        assertEquals("Work", users.users.value[id]?.name)
        assertTrue(id in profiles.ids.first())
        delay(200)
        assertEquals(Profile.DEFAULT_ID, sync.id.value)
        assertNull(users.users.value[id]?.activeSince)
    }

    @Test
    fun aProfileWithoutUserGetsOne() = runBlocking {
        start()

        profiles.add("found")

        eventually { users.users.value["found"] != null }
        assertEquals("Profile", users.users.value["found"]?.name)
    }

    @Test
    fun deletingAProfileDeletesItsUser() = runBlocking {
        start()
        val id = sync.createProfile("Work")!!

        assertTrue(sync.deleteProfile(id))

        assertNull(users.users.value[id])
        assertFalse(id in profiles.ids.first())
    }

    @Test
    fun deletingAnUnknownProfileKeepsEveryUser() = runBlocking {
        start()

        assertFalse(sync.deleteProfile("ghost"))

        assertFalse(users.calls.any { it.startsWith("delete") })
    }

    private companion object {
        val ACTIVE: Instant = Instant.fromEpochMilliseconds(1)
    }
}
