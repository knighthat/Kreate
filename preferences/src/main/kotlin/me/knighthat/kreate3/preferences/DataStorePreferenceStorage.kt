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
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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
 *
 * @param onDamaged called when a damaged profile file was replaced with an empty one
 */
internal class DataStorePreferenceStorage(
    private val rootDirectory: File,
    private val onDamaged: () -> Unit = {},
) : PreferenceStorage {

    private class OpenStore(val scope: CoroutineScope, val dataStore: DataStore<Preferences>)

    private val mutex = Mutex()
    private val openStores = mutableMapOf<String, OpenStore>()

    override suspend fun read(profileId: String): Map<String, Any> =
        storeOf(profileId).data.first().entries()

    override suspend fun write(profileId: String, changes: Map<String, Any?>) {
        storeOf(profileId).edit { it.applyChanges(changes) }
    }

    override suspend fun delete(profileId: String) = mutex.withLock {
        openStores.remove(profileId)?.scope?.cancel()
        profileDirectory(profileId).deleteRecursively()
        Unit
    }

    override fun reasonOf(error: Exception): PreferenceFailure.Reason = failureReasonOf(error)

    private suspend fun storeOf(profileId: String): DataStore<Preferences> = mutex.withLock {
        openStores.getOrPut(profileId) {
            // Separate scope per store, so it can be cancelled alone when the profile is deleted
            val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val dataStore = createPreferencesDataStore(
                storeScope,
                PROFILE_STEPS,
                replaceDamaged = {
                    onDamaged()
                    emptyPreferences()
                },
            ) { File(profileDirectory(profileId), PROFILE_FILE_NAME) }
            OpenStore(storeScope, dataStore)
        }.dataStore
    }

    private fun profileDirectory(profileId: String): File =
        Profile.directory(rootDirectory, profileId)

    internal companion object {

        const val PROFILE_FILE_NAME = "preferences.preferences_pb"
    }
}
