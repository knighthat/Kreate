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
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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

/**
 * Entries of the file by name, without its schema version.
 */
internal fun Preferences.entries(): Map<String, Any> =
    asMap()
        .mapKeys { it.key.name }
        .filterKeys { it != SCHEMA_VERSION_NAME }

/**
 * Applies [changes] to the file, an entry with a `null` value is removed. Values are limited to
 * the types of [PreferenceStorage].
 */
internal fun MutablePreferences.applyChanges(changes: Map<String, Any?>) =
    changes.forEach { (name, value) ->
        if (value == null) remove(name) else put(name, value)
    }

private fun MutablePreferences.remove(name: String) {
    asMap().keys.firstOrNull { it.name == name }?.let(::remove)
}

@Suppress("UNCHECKED_CAST")
private fun MutablePreferences.put(name: String, value: Any) {
    require(name != SCHEMA_VERSION_NAME) { "\"$name\" is reserved" }

    // Remove first, in case the entry was stored with a different type
    remove(name)
    when (value) {
        is String -> this[stringPreferencesKey(name)] = value
        is Int -> this[intPreferencesKey(name)] = value
        is Long -> this[longPreferencesKey(name)] = value
        is Float -> this[floatPreferencesKey(name)] = value
        is Double -> this[doublePreferencesKey(name)] = value
        is Boolean -> this[booleanPreferencesKey(name)] = value
        is Set<*> -> this[stringSetPreferencesKey(name)] = value as Set<String>
        else -> throw IllegalArgumentException("Unsupported type ${value::class} for \"$name\"")
    }
}
