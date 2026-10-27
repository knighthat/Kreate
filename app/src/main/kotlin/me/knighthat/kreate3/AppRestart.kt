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

package me.knighthat.kreate3

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import me.knighthat.kreate3.AppRestart.needed
import me.knighthat.kreate3.AppRestart.readOnce
import me.knighthat.kreate3.preferences.Preference

/**
 * Settings read once by what the app makes once, like the HTTP client, the song caches and the
 * image loader: a change to one applies after the app restarts. Reading them with [readOnce]
 * lets [needed] tell.
 */
object AppRestart {

    // The value each setting was read with, the first read is the one in use
    private val read = MutableStateFlow<Map<Preference<*>, Any?>>(emptyMap())

    /**
     * Whether a setting read with [readOnce] is set to something else now, a restart applies it.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val needed: Flow<Boolean> = read
        .flatMapLatest { used ->
            if (used.isEmpty()) flowOf(false)
            else combine(used.map { (preference, value) -> preference.state.map { it != value } }) {
                it.any { changed -> changed }
            }
        }
        .distinctUntilChanged()

    /** The value of [preference] now, kept as the one in use until the app restarts. */
    fun <T> readOnce(preference: Preference<T>): T {
        val value = preference.value
        read.update { if (preference in it) it else it + (preference to value) }
        return value
    }
}
