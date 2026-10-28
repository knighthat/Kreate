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

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import java.io.File
import java.io.IOException

/**
 * Why a read or an edit of a DataStore failed.
 */
internal fun failureReasonOf(error: Exception): PreferenceFailure.Reason = when (error) {
    // A subclass of IOException, comes first
    is CorruptionException -> PreferenceFailure.Reason.DAMAGED
    is IOException -> PreferenceFailure.Reason.STORAGE
    else -> PreferenceFailure.Reason.UNKNOWN
}

/**
 * Creates a [DataStore] backed by the file at the path returned by [produceFile].
 *
 * Only one instance may be active per file, hold on to the returned store.
 * Cancelling [scope] shuts the store down.
 *
 * @param replaceDamaged what a damaged file is replaced with, called once when the file is read
 * at opening. The store reads its file only then, later changes are saved from what it holds.
 */
internal fun createPreferencesDataStore(
    scope: CoroutineScope,
    steps: List<SchemaStep>,
    replaceDamaged: () -> Preferences,
    produceFile: () -> File,
): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { replaceDamaged() },
        scope = scope,
        migrations = listOf(SchemaMigration(steps)),
        produceFile = produceFile,
    )
