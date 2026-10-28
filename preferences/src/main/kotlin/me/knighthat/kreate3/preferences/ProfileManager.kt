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
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.knighthat.kreate3.preferences.PreferenceFailure.Store.PROFILES
import me.knighthat.kreate3.preferences.PreferenceFailure.Store.SETTINGS
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
 * Secrets are not here, [SecretStore] keeps them under the no-backup folder.
 *
 * Create a single instance per process, [scope] is used to share [activeId].
 */
class ProfileManager internal constructor(
    scope: CoroutineScope,
    rootDirectory: File,
    internal val storage: PreferenceStorage,
    /** What every store of this module failed at, see [Preferences.failures] */
    internal val reports: FailureReports = FailureReports(),
) {

    constructor(scope: CoroutineScope, context: Context) :
            this(scope, context.filesDir, FailureReports())

    private constructor(scope: CoroutineScope, rootDirectory: File, reports: FailureReports) :
            this(
                scope,
                rootDirectory,
                DataStorePreferenceStorage(rootDirectory) {
                    reports.report(SETTINGS, saving = false, PreferenceFailure.Reason.DAMAGED)
                },
                reports,
            )

    private val global: DataStore<Preferences> = createPreferencesDataStore(
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        steps = GLOBAL_STEPS,
        replaceDamaged = {
            reports.report(PROFILES, saving = false, PreferenceFailure.Reason.DAMAGED)
            // Rebuilt from the profiles' folders, which one was active is lost
            val ids = File(rootDirectory, Profile.DIRECTORY_NAME)
                .listFiles()
                ?.filter { it.isDirectory }
                ?.map { it.name }
                .orEmpty()
            preferencesOf(PROFILE_IDS to ids.toSet() + Profile.DEFAULT_ID)
        },
    ) { File(rootDirectory, GLOBAL_FILE_NAME) }

    /** The global file, empty when it can't be read */
    private val stored: SharedFlow<Preferences> = global.data
        .catch { e ->
            if (e !is Exception || e is CancellationException) throw e
            reports.report(PROFILES, saving = false, failureReasonOf(e))
            emit(emptyPreferences())
        }
        .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    /**
     * Ids of all known profiles.
     */
    val ids: Flow<Set<String>> = stored.map { it[PROFILE_IDS].orEmpty() }

    /**
     * Id of the profile in use, `null` until the global store is loaded.
     * Falls back to the default profile when the stored id is missing or no longer exists,
     * or the global file can't be read.
     */
    val activeId: StateFlow<String?> = stored
        .map { preferences ->
            val ids = preferences[PROFILE_IDS].orEmpty()
            preferences[ACTIVE_PROFILE_ID]?.takeIf { it in ids }
                ?: Profile.DEFAULT_ID.takeIf { it in ids }
                ?: ids.minOrNull()
                ?: Profile.DEFAULT_ID
        }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            // Not reported, it isn't the user's change; done again at the next start
            retrying {
                global.edit {
                    if (Profile.DEFAULT_ID !in it[PROFILE_IDS].orEmpty())
                        it[PROFILE_IDS] = it[PROFILE_IDS].orEmpty() + Profile.DEFAULT_ID
                }
            }
        }
    }

    /**
     * Registers a profile. It does not become active. Adding a known id changes nothing.
     *
     * @return the id of the profile, `null` when it couldn't be saved
     */
    suspend fun add(id: String = Uuid.random().toString()): String? =
        id.takeIf { edit { it[PROFILE_IDS] = it[PROFILE_IDS].orEmpty() + id } }

    /**
     * Makes the profile with [profileId] active. Unknown ids are ignored.
     *
     * @return `false` when the change couldn't be saved, the active profile stays
     */
    suspend fun switchTo(profileId: String): Boolean =
        edit {
            if (profileId in it[PROFILE_IDS].orEmpty())
                it[ACTIVE_PROFILE_ID] = profileId
        }

    /**
     * Removes the profile and its files.
     *
     * @throws IllegalArgumentException when [profileId] is the default profile
     * @return `true` when the profile was deleted, `false` if it doesn't exist or the change
     * couldn't be saved
     */
    suspend fun delete(profileId: String): Boolean {
        require(profileId != Profile.DEFAULT_ID) { "The default profile cannot be deleted" }

        var deleted = false
        val saved = edit {
            val ids = it[PROFILE_IDS].orEmpty()
            if (profileId !in ids) return@edit

            it[PROFILE_IDS] = ids - profileId
            if (it[ACTIVE_PROFILE_ID] == profileId)
                it.remove(ACTIVE_PROFILE_ID)

            deleted = true
        }
        if (!saved || !deleted) return false

        storage.delete(profileId)
        return true
    }

    /**
     * Applies [transform] to the global file, see [retrying].
     *
     * @return `false` when it couldn't be saved, which is reported
     */
    private suspend fun edit(transform: suspend (MutablePreferences) -> Unit): Boolean {
        val error = retrying { global.edit(transform) } ?: return true
        reports.report(PROFILES, saving = true, failureReasonOf(error))
        return false
    }

    internal companion object {

        const val GLOBAL_FILE_NAME = "global.preferences_pb"

        val PROFILE_IDS = stringSetPreferencesKey("profile_ids")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
    }
}
