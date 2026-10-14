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

package me.knighthat.kreate.database.repository

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import me.knighthat.kreate.database.DatabaseTest
import me.knighthat.kreate.database.entity.SearchHistoryEntity
import me.knighthat.kreate.database.entity.UserPlaylistEntity
import me.knighthat.kreate.database.entity.UserSongEntity
import me.knighthat.kreate.database.model.Origin
import me.knighthat.kreate.database.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * Users, and what goes with them: their library and the rest of their data.
 */
internal class UserRepositoryTest : DatabaseTest() {

    private lateinit var users: UserRepositoryImpl
    private lateinit var queue: QueueRepositoryImpl

    @Before
    fun setUp() {
        users = UserRepositoryImpl(database.userDao(), clock)
        queue = QueueRepositoryImpl(database.persistentQueueDao(), activeUser)
    }

    @Test
    fun savingAgainKeepsWhenTheUserWasMade() = runBlocking {
        val created = clock.now()
        users.save(User(USER, "Me"))
        clock.advance()

        users.save(User(USER, "Still me"))

        val row = database.userDao().get(USER)!!
        assertEquals("Still me", row.name)
        assertEquals(created, row.createdAt)
        assertEquals(clock.now(), row.updatedAt)
    }

    @Test
    fun usersAreListedByName() = runBlocking {
        users.save(User("2", "Zoe"))
        users.save(User("1", "Adam"))

        assertEquals(listOf("Adam", "Zoe"), users.all().first().map { it.name })
    }

    @Test
    fun aProfileNeedsAName() {
        runBlocking { users.save(User(USER, "Me")) }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { users.rename(USER, "  ") }
        }
        assertEquals("Me", runBlocking { users.find(USER).first() }?.name)
    }

    @Test
    fun renamingIsAChangeButBeingActiveIsNot() = runBlocking {
        val created = clock.now()
        users.save(User(USER, "Me"))
        clock.advance()

        users.markActive(USER)
        users.markSeen(USER)
        val afterActivity = database.userDao().get(USER)!!
        assertEquals(clock.now(), afterActivity.activeSince)
        assertEquals(clock.now(), afterActivity.lastSeen)
        assertEquals(created, afterActivity.updatedAt)

        clock.advance()
        users.rename(USER, "New")
        assertEquals(clock.now(), database.userDao().get(USER)?.updatedAt)
    }

    @Test
    fun deletingAUserDeletesTheirData() = runBlocking {
        users.save(User(USER, "Me"))
        addSong("s1")
        database.userSongDao()
            .upsert(UserSongEntity(USER, "s1", createdAt = EPOCH, updatedAt = EPOCH))
        val playlist = database.userPlaylistDao().upsert(
            UserPlaylistEntity(
                userId = USER,
                origin = Origin.LOCAL,
                name = "Mix",
                createdAt = EPOCH,
                updatedAt = EPOCH
            )
        )
        database.searchHistoryDao().upsert(SearchHistoryEntity("query", USER, EPOCH))
        queue.replace(listOf("s1"))

        users.delete(USER)

        assertNull(database.userSongDao().get(USER, "s1"))
        assertNull(database.userPlaylistDao().get(playlist))
        assertEquals(
            emptyList<Any>(),
            database.searchHistoryDao().startingWith(USER, "%", 10).first()
        )
        assertEquals(emptyList<Any>(), database.persistentQueueDao().queue(USER).first())
        // The catalog is shared, it stays
        assertNotNull(database.songDao().get("s1"))
    }

    @Test
    fun theQueueKeepsTheFirstPlaceOfEachSong() = runBlocking {
        addUser()
        listOf("a", "b", "c").forEach { addSong(it) }

        queue.replace(listOf("b", "a", "b", "c"))

        assertEquals(
            listOf("b" to 0, "a" to 1, "c" to 2),
            queue.queue().first().map { it.songId to it.position }
        )
    }

    @Test
    fun replacingTheQueueLeavesOtherUsersAlone() = runBlocking {
        addUser()
        addUser("other")
        listOf("a", "b").forEach { addSong(it) }
        queue.replace(listOf("a"))
        activeUser.id.value = "other"
        queue.replace(listOf("b"))

        queue.clear()

        activeUser.id.value = USER
        assertEquals(listOf("a"), queue.queue().first().map { it.songId })
    }
}
