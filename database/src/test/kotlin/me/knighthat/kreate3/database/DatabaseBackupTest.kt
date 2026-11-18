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

package me.knighthat.kreate3.database

import android.net.Uri
import androidx.room.Room
import androidx.room.useReaderConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.knighthat.kreate3.database.entity.UserPlaylistEntity
import me.knighthat.kreate3.model.Origin
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.nio.file.Files
import kotlin.time.Instant

internal class DatabaseBackupTest : DatabaseTest() {

    private val directory: File = Files.createTempDirectory("backup").toFile()

    private val backup by lazy {
        DatabaseBackup(RuntimeEnvironment.getApplication(), database, AndroidSQLiteDriver())
    }

    private suspend fun playlists(of: AppDatabase): Long =
        of.useReaderConnection { connection ->
            connection.usePrepared("SELECT COUNT(*) FROM user_playlist") {
                it.step()
                it.getLong(0)
            }
        }

    @After
    fun deleteDirectory() {
        directory.deleteRecursively()
    }

    @Test
    fun restoredCopyHoldsWhatWasWritten() = runBlocking<Unit> {
        addUser(name = "Kept")
        val copy = File(directory, "copy.db")
        backup.export(copy)

        val target = File(directory, "kreate.db")
        File(target.path + "-wal").writeText("stale")
        DatabaseBackup.restore(target, copy)
        assertFalse(File(target.path + "-wal").exists())

        val restored =
            Room.databaseBuilder<AppDatabase>(RuntimeEnvironment.getApplication(), target.path)
                .setDriver(AndroidSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        try {
            assertEquals("Kept", restored.userDao().get(USER)?.name)
        } finally {
            restored.close()
        }
    }

    @Test
    fun exportReplacesAnOldFile() = runBlocking<Unit> {
        val copy = File(directory, "copy.db").apply { writeText("old") }
        backup.export(copy)

        assertEquals("SQLite format 3", copy.readBytes().copyOf(15).decodeToString())
    }

    @Test
    fun cleanedCopyLeavesTheDatabaseInUseAlone() = runBlocking<Unit> {
        addUser()
        val picture = Uri.parse("file:///covers/playlist-1.jpg")
        database.userPlaylistDao().upsert(
            UserPlaylistEntity(
                userId = USER,
                origin = Origin.LOCAL,
                name = "empty",
                thumbnail = picture,
                createdAt = Instant.fromEpochMilliseconds(0),
                updatedAt = Instant.fromEpochMilliseconds(0),
            )
        )
        val copy = File(directory, "copy.db")

        assertEquals(listOf(picture), backup.exportCleaned(copy))
        assertFalse(File(copy.path + "-journal").exists())

        val cleaned =
            Room.databaseBuilder<AppDatabase>(RuntimeEnvironment.getApplication(), copy.path)
                .setDriver(AndroidSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        try {
            assertEquals(0L, playlists(cleaned))
        } finally {
            cleaned.close()
        }
        assertEquals(1L, playlists(database))
    }
}
