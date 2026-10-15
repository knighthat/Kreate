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
import java.io.File

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

        return (profiles + File(rootDirectory, ProfileManager.GLOBAL_FILE_NAME))
            .filter(File::isFile)
            .associateBy { it.relativeTo(rootDirectory).invariantSeparatorsPath }
    }

    /**
     * Whether [path] is a path [files] can return.
     */
    fun isBackupPath(path: String): Boolean =
        path == ProfileManager.GLOBAL_FILE_NAME || PROFILE_PATH.matches(path)

    /**
     * Whether [source], laid out by the paths in the backup, has what [restore] needs.
     */
    fun isRestorable(source: File): Boolean =
        File(source, ProfileManager.GLOBAL_FILE_NAME).isFile

    /**
     * Replaces the preferences of every profile with the files in [source], laid out by their
     * paths in the backup. Only call it before any profile or preference is read in this process.
     */
    fun restore(source: File) {
        File(rootDirectory, Profile.DIRECTORY_NAME).deleteRecursively()
        File(rootDirectory, ProfileManager.GLOBAL_FILE_NAME).delete()

        source.walkTopDown()
            .filter(File::isFile)
            .forEach { file ->
                val path = file.relativeTo(source).invariantSeparatorsPath
                if (isBackupPath(path))
                    file.copyTo(File(rootDirectory, path), overwrite = true)
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
