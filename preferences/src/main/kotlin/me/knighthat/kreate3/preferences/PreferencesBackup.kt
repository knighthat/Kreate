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
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.constant.OpenSubsonicAuth
import java.io.File

/**
 * The OpenSubsonic server a backup keeps for the default profile.
 *
 * @param url the address, empty when the backup has none
 * @param auth the way to sign in, `null` when the backup left the default
 */
class StagedOpenSubsonic(val url: String, val auth: OpenSubsonicAuth?)

/**
 * Takes the preferences of every profile out for a backup, and puts them back. Secrets are left
 * out, they never leave the device.
 *
 * Paths in a backup are the paths under the app's files folder: `global.preferences_pb` and
 * `profiles/<id>/preferences.preferences_pb`.
 */
class PreferencesBackup internal constructor(private val rootDirectory: File) {

    constructor(context: Context) : this(context.filesDir)

    /**
     * Every file to back up, by its path in the backup. The files are replaced whole on every
     * write, so a copy is never half written.
     */
    fun files(): Map<String, File> {
        val profiles = File(rootDirectory, Profile.DIRECTORY_NAME)
            .listFiles()
            .orEmpty()
            .map { File(it, DataStorePreferenceStorage.PROFILE_FILE_NAME) }

        return (profiles + File(rootDirectory, GlobalPreferenceStorage.FILE_NAME))
            .filter(File::isFile)
            .associateBy { it.relativeTo(rootDirectory).invariantSeparatorsPath }
    }

    /**
     * Whether [path] is a path [files] can return.
     */
    fun isBackupPath(path: String): Boolean =
        path == GlobalPreferenceStorage.FILE_NAME || PROFILE_PATH.matches(path)

    /**
     * Whether [source], laid out by the paths in the backup, has what [restore] needs.
     */
    fun isRestorable(source: File): Boolean =
        File(source, GlobalPreferenceStorage.FILE_NAME).isFile

    /**
     * Replaces the preferences of every profile with the files in [source], laid out by their
     * paths in the backup. Only call it before any profile or preference is read in this process.
     */
    fun restore(source: File) {
        File(rootDirectory, Profile.DIRECTORY_NAME).deleteRecursively()
        File(rootDirectory, GlobalPreferenceStorage.FILE_NAME).delete()

        source.walkTopDown()
            .filter(File::isFile)
            .forEach { file ->
                val path = file.relativeTo(source).invariantSeparatorsPath
                if (isBackupPath(path))
                    file.copyTo(File(rootDirectory, path), overwrite = true)
            }
    }

    /**
     * What the default profile of a backup laid out in [source], by its paths, keeps of the
     * OpenSubsonic server, read from a copy so the backup stays as it is. Nothing is opened for
     * the app: use it while the backup waits for the next start.
     *
     * @return `null` when the backup has no file for the default profile
     */
    suspend fun stagedOpenSubsonic(source: File): StagedOpenSubsonic? =
        withContext(Dispatchers.IO) {
            val staged = File(
                Profile.directory(source, Profile.DEFAULT_ID),
                DataStorePreferenceStorage.PROFILE_FILE_NAME
            )
            if (!staged.isFile) return@withContext null

            val copy = File.createTempFile("staged", ".preferences_pb")
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            try {
                staged.copyTo(copy, overwrite = true)
                val values = createPreferencesDataStore(
                    scope = scope,
                    steps = PROFILE_STEPS,
                    name = "staged preferences",
                    replaceDamaged = { emptyPreferences() },
                ) { copy }.data.first().entries()

                StagedOpenSubsonic(
                    url = values[Preferences.OPEN_SUBSONIC_URL_NAME] as? String ?: "",
                    auth = OpenSubsonicAuth.entries.firstOrNull {
                        it.name == values[Preferences.OPEN_SUBSONIC_AUTH_NAME]
                    },
                )
            } finally {
                scope.cancel()
                // The store leaves files beside its own
                copy.parentFile?.listFiles { file -> file.name.startsWith(copy.name) }
                    ?.forEach(File::delete)
            }
        }

    /**
     * Writes a global file into [destination], laid out by the paths in a backup, that lists
     * [profileIds] and nothing else, for a restore of data that has no preferences of its own.
     * Restoring it leaves every profile with default settings. The default profile needs no entry.
     */
    suspend fun writeProfileList(destination: File, profileIds: Collection<String>) {
        destination.mkdirs()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            createPreferencesDataStore(
                scope = scope,
                steps = GLOBAL_STEPS,
                name = "global preferences",
                replaceDamaged = { emptyPreferences() },
            ) { File(destination, GlobalPreferenceStorage.FILE_NAME) }.edit {
                it[stringSetPreferencesKey(ProfileManager.PROFILE_IDS)] =
                    profileIds.toSet() - Profile.DEFAULT_ID
            }
        } finally {
            scope.cancel()
        }
    }

    companion object {

        /** Schema version of the global file this app writes, newer ones are left untouched. */
        val GLOBAL_VERSION: Int = GLOBAL_STEPS.size

        /** Schema version of the profile files this app writes, newer ones are left untouched. */
        val PROFILE_VERSION: Int = PROFILE_STEPS.size

        private val PROFILE_PATH = Regex(
            "${Profile.DIRECTORY_NAME}/[A-Za-z0-9_-]+/${Regex.escape(DataStorePreferenceStorage.PROFILE_FILE_NAME)}"
        )
    }
}
