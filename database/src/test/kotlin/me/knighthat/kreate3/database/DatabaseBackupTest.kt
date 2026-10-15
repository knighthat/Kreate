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

import androidx.room.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.nio.file.Files

internal class DatabaseBackupTest : DatabaseTest() {

    private val directory: File = Files.createTempDirectory("backup").toFile()

    @After
    fun deleteDirectory() {
        directory.deleteRecursively()
    }

    @Test
    fun restoredCopyHoldsWhatWasWritten() = runBlocking<Unit> {
        addUser(name = "Kept")
        val copy = File(directory, "copy.db")
        DatabaseBackup(database).export(copy)

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
        DatabaseBackup(database).export(copy)

        assertEquals("SQLite format 3", copy.readBytes().copyOf(15).decodeToString())
    }
}
