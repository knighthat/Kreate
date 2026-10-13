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

import kotlinx.coroutines.flow.StateFlow

/**
 * A single secret of the active profile, like a password or an API key.
 *
 * Secrets never go in the profile's preferences file. They have a file of their own in the
 * profile's folder, so the preferences stay safe to back up, export or show. An empty value
 * means the secret is not set.
 *
 * Use the instances declared in [Secrets], they must not be created elsewhere.
 */
class Secret internal constructor(
    internal val name: String,
    private val store: SecretStore,
) {

    /**
     * Current value, emits every time it changes. Empty until the profile's file is read.
     */
    val state: StateFlow<String> get() = store.stateOf(name)

    /**
     * The value now, which is empty while the profile's file is still being read. Use [current]
     * when the real value is needed.
     */
    val value: String get() = state.value

    /**
     * The value once the active profile's file is read.
     */
    suspend fun current(): String = store.current(name)

    /**
     * Saves [value] for the active profile. The change shows in [state] once it is on disk.
     */
    fun update(value: String) = store.write(name, value)

    fun clear() = update("")
}
