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

package me.knighthat.kreate.database

import androidx.paging.PagingData
import androidx.paging.testing.asSnapshot
import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import me.knighthat.kreate.database.entity.AlbumEntity
import me.knighthat.kreate.database.entity.ArtistEntity
import me.knighthat.kreate.database.entity.SongArtistMapEntity
import me.knighthat.kreate.database.entity.SongEntity
import me.knighthat.kreate.database.entity.UserEntity
import me.knighthat.kreate.database.model.Origin
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** Every row of a paged list. */
internal suspend fun <T : Any> Flow<PagingData<T>>.asList(): List<T> = asSnapshot()

internal class TestClock(var time: Instant = Instant.fromEpochMilliseconds(1_000)) : Clock {

    override fun now(): Instant = time

    fun advance(by: Duration = 1.seconds) {
        time += by
    }
}

internal class TestUser(id: String? = null) : ActiveUser {

    override val id = MutableStateFlow(id)
}

/**
 * Opens an empty database in memory for every test. The app opens it with the bundled driver,
 * whose native library is built for devices only, so the framework driver stands in for it.
 */
@RunWith(RobolectricTestRunner::class)
internal abstract class DatabaseTest {

    protected val clock = TestClock()
    protected val activeUser = TestUser(USER)

    protected lateinit var database: AppDatabase

    @Before
    fun openDatabase() {
        database = Room.inMemoryDatabaseBuilder<AppDatabase>(RuntimeEnvironment.getApplication())
            .setDriver(AndroidSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }

    @After
    fun closeDatabase() = database.close()

    protected suspend fun addUser(id: String = USER, name: String = id) =
        database.userDao().upsert(UserEntity(id, name, createdAt = EPOCH, updatedAt = EPOCH))

    protected suspend fun addArtist(id: String, origin: Origin = Origin.LOCAL, name: String? = id) =
        database.artistDao()
            .upsert(ArtistEntity(id, origin, name, createdAt = EPOCH, updatedAt = EPOCH))

    protected suspend fun addAlbum(
        id: String,
        origin: Origin = Origin.LOCAL,
        name: String? = id,
        year: Int? = null,
    ) = database.albumDao().upsert(
        AlbumEntity(id, origin, name, year = year, createdAt = EPOCH, updatedAt = EPOCH)
    )

    protected suspend fun addSong(
        id: String,
        origin: Origin = Origin.LOCAL,
        name: String? = id,
        albumId: String? = null,
        albumPosition: Int? = null,
        unavailableSince: Instant? = null,
        artists: List<String> = emptyList(),
    ) {
        database.songDao().upsert(
            SongEntity(
                id = id,
                origin = origin,
                name = name,
                albumId = albumId,
                albumPosition = albumPosition,
                unavailableSince = unavailableSince,
                createdAt = EPOCH,
                updatedAt = EPOCH,
            )
        )
        artists.forEach { database.songDao().link(SongArtistMapEntity(id, it)) }
    }

    companion object {

        const val USER = "me"

        val EPOCH: Instant = Instant.fromEpochMilliseconds(0)

        /** Stored in a reaction column for a dislike. */
        val DISLIKED: Instant = Instant.fromEpochMilliseconds(-1)
    }
}
