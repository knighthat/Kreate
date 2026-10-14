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

package me.knighthat.kreate3.database

import kotlinx.coroutines.flow.StateFlow

/**
 * Tells the repositories whose data to read and write.
 *
 * The app provides it to Koin, usually backed by the active profile.
 */
interface ActiveUser {

    /**
     * Id of the active user, `null` while it is not known yet.
     */
    val id: StateFlow<String?>
}
