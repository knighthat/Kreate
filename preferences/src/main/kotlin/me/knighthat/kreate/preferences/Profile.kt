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

/**
 * A user of the app. Every profile owns a separate preferences file.
 */
data class Profile(val id: String, val name: String) {

    /**
     * The built-in profile, it always exists and cannot be deleted.
     */
    val isDefault: Boolean get() = id == DEFAULT_ID

    companion object {

        const val DEFAULT_ID = "default"
    }
}
