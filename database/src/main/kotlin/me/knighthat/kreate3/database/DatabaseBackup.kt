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

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.database.repository.CleanupRepository
import me.knighthat.kreate3.database.repository.CleanupRepositoryImpl
import java.io.File

private const val TAG = "DatabaseBackup"

/**
 * Takes the database out for a backup, and puts one back.
 */
class DatabaseBackup internal constructor(
    private val context: Context,
    private val database: AppDatabase,
    // Tests open the copy with the framework's, the bundled one is built for devices only
    private val copyDriver: SQLiteDriver = BundledSQLiteDriver(),
) {

    /**
     * Writes a copy of the whole database to [target], replacing it. The copy is one file that
     * holds everything written so far, the database stays usable meanwhile.
     */
    suspend fun export(target: File) {
        target.delete()
        database.useWriterConnection { connection ->
            connection.usePrepared("VACUUM INTO ?") {
                it.bindText(1, target.absolutePath)
                it.step()
            }
        }
    }

    /**
     * Same as [export], then the copy is cleaned up like [CleanupRepository.cleanUp] and compacted,
     * for a smaller backup. Only the copy: the database in use keeps everything.
     *
     * @return the pictures of the playlists the clean up deleted, to leave them out too
     */
    suspend fun exportCleaned(target: File): List<Uri> = withContext(Dispatchers.IO) {
        export(target)
        // One file when it's done, with no journal beside it
        val copy = Room.databaseBuilder<AppDatabase>(context, target.path)
            .setDriver(copyDriver)
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        try {
            val result = CleanupRepositoryImpl(copy, copy.cleanupDao()).cleanUp()
            Logger.i(tag = TAG) { "Cleaned up the copy: $result" }
            result.playlists.mapNotNull { it.thumbnail }
        } finally {
            copy.close()
            File(target.path + "-journal").delete()
        }
    }

    companion object {

        /**
         * Schema version of the database this app opens. Older ones are migrated, newer ones
         * can't be opened.
         */
        const val VERSION = DATABASE_VERSION

        /**
         * Replaces the database with the file at [source], a copy made by [export].
         * Only call it before the database is opened in this process.
         */
        fun restore(context: Context, source: File) =
            restore(context.getDatabasePath(DATABASE_FILE_NAME), source)

        internal fun restore(target: File, source: File) {
            target.parentFile?.mkdirs()
            val temporary = File(target.parentFile, "${target.name}.restore")
            source.copyTo(temporary, overwrite = true)

            // A journal left by the old database would be applied to the new one
            listOf("-wal", "-shm", "-journal").forEach { File(target.path + it).delete() }
            check(temporary.renameTo(target)) { "Couldn't move the database in place" }
        }
    }
}
