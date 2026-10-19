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

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Key that stores the schema version of a file, reserved for the storage layer.
 */
internal const val SCHEMA_VERSION_NAME = "schema_version"

private val SCHEMA_VERSION = intPreferencesKey(SCHEMA_VERSION_NAME)

/**
 * One change to the stored format. Works on raw entries so it keeps working
 * after the definitions of the preferences change.
 */
internal fun interface SchemaStep {

    fun migrate(preferences: MutablePreferences)
}

/**
 * Steps applied to profile files. Step `n` upgrades version `n` to `n + 1`,
 * so the latest version is the number of steps. Never reorder or remove a step.
 */
internal val PROFILE_STEPS: List<SchemaStep> = listOf(
    // 0 → 1: the OpenSubsonic sync switches but the playlists' became a SyncMode. Off is kept, on
    // takes the default
    SchemaStep { preferences ->
        listOf(
            "open_subsonic_sync_songs",
            "open_subsonic_sync_albums",
            "open_subsonic_sync_artists",
        ).forEach { name ->
            val key = booleanPreferencesKey(name)
            when (preferences.asMap()[key]) {
                false -> preferences[stringPreferencesKey(name)] = "OFF"
                true -> preferences.remove(key)
            }
        }
    },
)

/**
 * Same as [PROFILE_STEPS] but for the secrets file of a profile.
 */
internal val SECRET_STEPS: List<SchemaStep> = emptyList()

/**
 * Same as [PROFILE_STEPS] but for the global file.
 */
internal val GLOBAL_STEPS: List<SchemaStep> = emptyList()

/**
 * Brings a file up to the latest version, runs once when the file is first opened.
 *
 * - A new (empty) file is stamped with the latest version.
 * - A file without a version is treated as version 0.
 * - A file newer than the app (after a downgrade) is left untouched.
 */
internal class SchemaMigration(private val steps: List<SchemaStep>) : DataMigration<Preferences> {

    private val latest = steps.size

    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        val stored = currentData[SCHEMA_VERSION]
        return stored == null || stored < latest
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val preferences = currentData.toMutablePreferences()
        val stored = preferences[SCHEMA_VERSION] ?: if (preferences.asMap().isEmpty()) latest else 0

        for (version in stored until latest)
            steps[version].migrate(preferences)
        preferences[SCHEMA_VERSION] = latest

        return preferences.toPreferences()
    }

    override suspend fun cleanUp() = Unit
}
