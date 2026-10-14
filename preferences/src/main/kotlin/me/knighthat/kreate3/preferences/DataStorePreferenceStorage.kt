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

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Keeps one [DataStore] per profile, files are `<root>/profiles/<id>/preferences.preferences_pb`.
 *
 * This class is the only owner of the profile files, a file must never be opened elsewhere.
 */
internal class DataStorePreferenceStorage(private val rootDirectory: File) : PreferenceStorage {

    private class OpenStore(val scope: CoroutineScope, val dataStore: DataStore<Preferences>)

    private val mutex = Mutex()
    private val openStores = mutableMapOf<String, OpenStore>()

    override suspend fun read(profileId: String): Map<String, Any> =
        storeOf(profileId).data.first()
            .asMap()
            .mapKeys { it.key.name }
            .filterKeys { it != SCHEMA_VERSION_NAME }

    override suspend fun write(profileId: String, changes: Map<String, Any?>) {
        storeOf(profileId).edit { preferences ->
            changes.forEach { (name, value) ->
                if (value == null)
                    preferences.remove(name)
                else
                    preferences.put(name, value)
            }
        }
    }

    override suspend fun delete(profileId: String) = mutex.withLock {
        openStores.remove(profileId)?.scope?.cancel()
        profileDirectory(profileId).deleteRecursively()
        Unit
    }

    private suspend fun storeOf(profileId: String): DataStore<Preferences> = mutex.withLock {
        openStores.getOrPut(profileId) {
            // Separate scope per store, so it can be cancelled alone when the profile is deleted
            val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val dataStore = createPreferencesDataStore(storeScope, PROFILE_STEPS) {
                File(profileDirectory(profileId), PROFILE_FILE_NAME)
            }
            OpenStore(storeScope, dataStore)
        }.dataStore
    }

    private fun profileDirectory(profileId: String): File =
        Profile.directory(rootDirectory, profileId)

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

    private companion object {

        const val PROFILE_FILE_NAME = "preferences.preferences_pb"
    }
}
