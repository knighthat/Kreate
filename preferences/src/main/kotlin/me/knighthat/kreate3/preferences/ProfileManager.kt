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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.io.File
import kotlin.uuid.Uuid

/**
 * Keeps track of which profiles exist and which one is currently in use. It only knows ids,
 * the names and everything else about a profile belong to the user in the database.
 *
 * The list is kept in the global file with [GlobalPreferences], as two settings of [global];
 * this class is how the rest of the app reads and changes them. Layout under the app's files
 * directory:
 * - `global.preferences_pb`: profile ids, the active profile id and [GlobalPreferences]
 * - `profiles/<id>/`: files of one profile, owned by [PreferenceStorage]
 *
 * Secrets are not here, [SecretStore] keeps them under the no-backup folder.
 *
 * Create a single instance per process, [scope] is used to share [activeId].
 */
class ProfileManager internal constructor(
    scope: CoroutineScope,
    internal val storage: PreferenceStorage,
    /** Serves the global file, [GlobalPreferences] shares it */
    internal val global: PreferenceEngine,
    /** What every store of this module failed at, see [Preferences.failures] */
    internal val reports: FailureReports,
) {

    constructor(scope: CoroutineScope, context: Context) :
            this(scope, context.filesDir, FailureReports())

    internal constructor(
        scope: CoroutineScope,
        rootDirectory: File,
        storage: PreferenceStorage,
        reports: FailureReports = FailureReports(),
    ) : this(scope, storage, GlobalPreferenceStorage.engine(scope, rootDirectory, reports), reports)

    private constructor(scope: CoroutineScope, rootDirectory: File, reports: FailureReports) :
            this(scope, rootDirectory, profileStorage(rootDirectory, reports), reports)

    // Without the default profile, which is always there
    private val storedIds = Preference(
        PROFILE_IDS,
        emptySet(),
        codec<Set<String>, Set<*>>({ it }, { it.filterIsInstance<String>().toSet() }),
        global,
    )
    private val storedActiveId =
        Preference(ACTIVE_PROFILE_ID, "", codec<String, String>({ it }, { it }), global)

    /**
     * Ids of all known profiles, the default one included. Emits once the global file is loaded.
     */
    val ids: Flow<Set<String>> = flow {
        global.awaitLoaded()
        emitAll(storedIds.state.map { it + Profile.DEFAULT_ID })
    }

    /**
     * Id of the profile in use, `null` until the global file is loaded.
     * Falls back to the default profile when the stored id is missing or no longer exists,
     * or the global file can't be read.
     */
    val activeId: StateFlow<String?> = flow {
        global.awaitLoaded()
        emitAll(
            combine(ids, storedActiveId.state) { ids, active ->
                active.takeIf { it in ids } ?: Profile.DEFAULT_ID
            }
        )
    }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * Registers a profile. It does not become active. Adding a known id changes nothing.
     *
     * @return the id of the profile, `null` when it couldn't be saved
     */
    suspend fun add(id: String = Uuid.random().toString()): String? {
        global.awaitLoaded()
        if (id != Profile.DEFAULT_ID) storedIds.update { it + id }
        return id.takeIf { global.flush() }
    }

    /**
     * Makes the profile with [profileId] active. Unknown ids are ignored.
     *
     * The profile is in use at once, and goes back when the change couldn't be saved.
     *
     * @return `false` when the change couldn't be saved
     */
    suspend fun switchTo(profileId: String): Boolean {
        global.awaitLoaded()
        if (profileId != Profile.DEFAULT_ID && profileId !in storedIds.value) return true

        storedActiveId.update(profileId)
        return global.flush()
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

        global.awaitLoaded()
        if (profileId !in storedIds.value) return false

        storedIds.update { it - profileId }
        storedActiveId.update { if (it == profileId) "" else it }
        if (!global.flush()) return false

        storage.delete(profileId)
        return true
    }

    internal companion object {

        /** Names in the global file, [GlobalPreferences] can't use them */
        const val PROFILE_IDS = "profile_ids"
        const val ACTIVE_PROFILE_ID = "active_profile_id"

        /** The profiles' files, damage is reported as [PreferenceFailure.Store.SETTINGS] */
        fun profileStorage(rootDirectory: File, reports: FailureReports) =
            DataStorePreferenceStorage(rootDirectory) {
                reports.report(
                    PreferenceFailure.Store.SETTINGS,
                    saving = false,
                    PreferenceFailure.Reason.DAMAGED
                )
            }
    }
}
