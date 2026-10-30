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
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import me.knighthat.kreate3.preferences.PreferenceFailure.Store.GLOBAL
import java.io.File

/**
 * Keeps `<root>/global.preferences_pb`: [GlobalPreferences] and the profile list of
 * [ProfileManager], one file for every profile. The profile ids it is given are ignored.
 *
 * A damaged file is rebuilt from the profiles' folders, which profile was active and the
 * [GlobalPreferences] are lost.
 *
 * This class is the only owner of the file, it must never be opened elsewhere. Create a single
 * instance per process.
 */
internal class GlobalPreferenceStorage(
    rootDirectory: File,
    reports: FailureReports,
) : PreferenceStorage {

    private val dataStore: DataStore<Preferences> = createPreferencesDataStore(
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        steps = GLOBAL_STEPS,
        replaceDamaged = {
            reports.report(GLOBAL, saving = false, PreferenceFailure.Reason.DAMAGED)
            val ids = File(rootDirectory, Profile.DIRECTORY_NAME)
                .listFiles()
                ?.filter { it.isDirectory }
                ?.map { it.name }
                .orEmpty()
            preferencesOf(stringSetPreferencesKey(ProfileManager.PROFILE_IDS) to ids.toSet())
        },
    ) { File(rootDirectory, FILE_NAME) }

    override suspend fun read(profileId: String): Map<String, Any> =
        dataStore.data.first().entries()

    override suspend fun write(profileId: String, changes: Map<String, Any?>) {
        dataStore.edit { it.applyChanges(changes) }
    }

    override suspend fun delete(profileId: String) =
        throw UnsupportedOperationException("Global settings belong to no profile")

    override fun reasonOf(error: Exception): PreferenceFailure.Reason = failureReasonOf(error)

    internal companion object {

        const val FILE_NAME = "global.preferences_pb"

        /** What [PreferenceEngine] is given as the profile, ignored here */
        const val ID = "global"

        /** Serves the global file, for [ProfileManager] and [GlobalPreferences] to share */
        fun engine(
            scope: CoroutineScope,
            rootDirectory: File,
            reports: FailureReports,
        ) = PreferenceEngine(
            storage = GlobalPreferenceStorage(rootDirectory, reports),
            profileIds = flowOf(GlobalPreferenceStorage.ID),
            reports = reports,
            store = PreferenceFailure.Store.GLOBAL,
            scope = scope,
        )
    }
}
