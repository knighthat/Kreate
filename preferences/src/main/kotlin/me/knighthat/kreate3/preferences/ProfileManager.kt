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

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import kotlin.uuid.Uuid

/**
 * Keeps track of which profiles exist and which one is currently in use. It only knows ids,
 * the names and everything else about a profile belong to the user in the database.
 *
 * Layout under the app's files directory:
 * - `global.preferences_pb`: profile ids and the active profile id
 * - `profiles/<id>/`: files of one profile, owned by [PreferenceStorage]
 *
 * Create a single instance per process, [scope] is used to share [activeId].
 */
class ProfileManager internal constructor(
    scope: CoroutineScope,
    rootDirectory: File,
    internal val storage: PreferenceStorage,
) {

    constructor(scope: CoroutineScope, context: Context) : this(scope, context.filesDir)

    private constructor(scope: CoroutineScope, rootDirectory: File) :
            this(scope, rootDirectory, DataStorePreferenceStorage(rootDirectory))

    private val global: DataStore<Preferences> = createPreferencesDataStore(
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        steps = GLOBAL_STEPS,
    ) { File(rootDirectory, GLOBAL_FILE_NAME) }

    /**
     * Ids of all known profiles.
     */
    val ids: Flow<Set<String>> = global.data.map { it[PROFILE_IDS].orEmpty() }

    /**
     * Id of the profile in use, `null` until the global store is loaded.
     * Falls back to the default profile when the stored id is missing or no longer exists.
     */
    val activeId: StateFlow<String?> = global.data
        .map { preferences ->
            val ids = preferences[PROFILE_IDS].orEmpty()
            preferences[ACTIVE_PROFILE_ID]?.takeIf { it in ids }
                ?: Profile.DEFAULT_ID.takeIf { it in ids }
                ?: ids.minOrNull()
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            global.edit {
                if (Profile.DEFAULT_ID !in it[PROFILE_IDS].orEmpty())
                    it[PROFILE_IDS] = it[PROFILE_IDS].orEmpty() + Profile.DEFAULT_ID
            }
        }
    }

    /**
     * Registers a profile. It does not become active. Adding a known id changes nothing.
     *
     * @return the id of the profile
     */
    suspend fun add(id: String = Uuid.random().toString()): String {
        global.edit { it[PROFILE_IDS] = it[PROFILE_IDS].orEmpty() + id }
        return id
    }

    /**
     * Makes the profile with [profileId] active. Unknown ids are ignored.
     */
    suspend fun switchTo(profileId: String) {
        global.edit {
            if (profileId in it[PROFILE_IDS].orEmpty())
                it[ACTIVE_PROFILE_ID] = profileId
        }
    }

    /**
     * Removes the profile and its files.
     *
     * @throws IllegalArgumentException when [profileId] is the default profile
     * @return `true` when the profile was deleted, `false` if it doesn't exist
     */
    suspend fun delete(profileId: String): Boolean {
        require(profileId != Profile.DEFAULT_ID) { "The default profile cannot be deleted" }

        var deleted = false
        global.edit {
            val ids = it[PROFILE_IDS].orEmpty()
            if (profileId !in ids) return@edit

            it[PROFILE_IDS] = ids - profileId
            if (it[ACTIVE_PROFILE_ID] == profileId)
                it.remove(ACTIVE_PROFILE_ID)

            deleted = true
        }
        if (!deleted) return false

        storage.delete(profileId)
        return true
    }

    private companion object {

        const val GLOBAL_FILE_NAME = "global.preferences_pb"

        val PROFILE_IDS = stringSetPreferencesKey("profile_ids")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
    }
}
