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

import java.io.File

/**
 * Constants of the profiles. A profile is only an id here, its name and everything else
 * about the user live in the database.
 */
object Profile {

    /**
     * Id of the built-in profile, it always exists and cannot be deleted.
     */
    const val DEFAULT_ID = "default"

    /**
     * Folder under a root that holds one folder per profile.
     */
    internal const val DIRECTORY_NAME = "profiles"

    /**
     * Folder of the profile's files under [rootDirectory]. Deleting a profile deletes it.
     */
    internal fun directory(rootDirectory: File, profileId: String): File =
        File(File(rootDirectory, DIRECTORY_NAME), profileId)
}
