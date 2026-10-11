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

package me.knighthat.kreate.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
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
 * Keeps track of the profiles and which one is currently in use.
 *
 * Layout under the app's files directory:
 * - `global.preferences_pb`: profile list and the active profile id
 * - `profiles/<id>/`: files of one profile, owned by [PreferenceStorage]
 *
 * Create a single instance per process, [scope] is used to share [activeProfile].
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
     * All known profiles, sorted by name.
     */
    val profiles: Flow<List<Profile>> = global.data.map { it.profiles() }

    /**
     * The profile in use, `null` until the global store is loaded.
     * Falls back to the first profile when the stored id is missing or no longer exists.
     */
    val activeProfile: StateFlow<Profile?> = global.data
        .map { preferences ->
            val profiles = preferences.profiles()
            profiles.firstOrNull { it.id == preferences[ACTIVE_PROFILE_ID] }
                ?: profiles.firstOrNull()
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            global.edit {
                if (Profile.DEFAULT_ID !in it[PROFILE_IDS].orEmpty())
                    it.addProfile(DEFAULT_PROFILE_NAME, Profile.DEFAULT_ID)
            }
        }
    }

    /**
     * Adds a new profile. It does not become active.
     */
    suspend fun create(name: String): Profile {
        lateinit var profile: Profile
        global.edit { profile = it.addProfile(name) }
        return profile
    }

    /**
     * Changes the name of the profile with [profileId]. Unknown ids are ignored.
     *
     * @throws IllegalArgumentException for a blank [name]
     */
    suspend fun rename(profileId: String, name: String) {
        require(name.isNotBlank()) { "A profile needs a name" }

        global.edit {
            if (profileId in it[PROFILE_IDS].orEmpty())
                it[profileNameKey(profileId)] = name
        }
    }

    /**
     * Makes the profile with [profileId] active. Unknown ids are ignored.
     */
    suspend fun switchTo(profileId: String) {
        global.edit {
            if (it.profiles().any { profile -> profile.id == profileId })
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
            it.remove(profileNameKey(profileId))
            if (it[ACTIVE_PROFILE_ID] == profileId)
                it.remove(ACTIVE_PROFILE_ID)

            deleted = true
        }
        if (!deleted) return false

        storage.delete(profileId)
        return true
    }

    private fun Preferences.profiles(): List<Profile> =
        this[PROFILE_IDS].orEmpty()
            .map { Profile(it, this[profileNameKey(it)].orEmpty()) }
            .sortedWith(compareBy(Profile::name, Profile::id))

    private fun MutablePreferences.addProfile(
        name: String,
        id: String = Uuid.random().toString()
    ): Profile {
        val profile = Profile(id, name)

        this[PROFILE_IDS] = this[PROFILE_IDS].orEmpty() + profile.id
        this[profileNameKey(profile.id)] = name

        return profile
    }

    private companion object {

        const val GLOBAL_FILE_NAME = "global.preferences_pb"
        const val DEFAULT_PROFILE_NAME = "Default"

        val PROFILE_IDS = stringSetPreferencesKey("profile_ids")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")

        fun profileNameKey(profileId: String) = stringPreferencesKey("profile_name.$profileId")
    }
}
