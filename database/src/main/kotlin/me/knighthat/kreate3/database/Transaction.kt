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

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/**
 * Runs [block] in a single write transaction, all or nothing. The database is opened with a
 * SQLite driver, there is no `SupportSQLiteOpenHelper` behind it, so `withTransaction` can't be used.
 *
 * DAO calls made inside [block] join the transaction.
 */
internal suspend fun <R> AppDatabase.inTransaction(block: suspend () -> R): R =
    useWriterConnection { connection -> connection.immediateTransaction { block() } }
