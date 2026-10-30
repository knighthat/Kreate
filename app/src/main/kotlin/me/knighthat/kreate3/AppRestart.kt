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
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import me.knighthat.kreate3.AppRestart.LOAD_WAIT_MILLIS
import me.knighthat.kreate3.preferences.Preference
import me.knighthat.kreate3.preferences.Preferences
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Settings read once by what the app makes once, like the HTTP client, the song caches and the
 * image loader: a change to one applies after the app restarts. Reading them with [readOnce]
 * lets [needed] tell.
 */
object AppRestart {

    // The value each setting was read with, the first read is the one in use
    private val read = MutableStateFlow<Map<Preference<*>, Any?>>(emptyMap())

    // The files are small and opened when the app starts, a longer wait means they won't come
    private const val LOAD_WAIT_MILLIS = 300L

    // Every read shares one wait, from the first read on: a load that never ends delays only once
    private val waitEnd by lazy { TimeSource.Monotonic.markNow() + LOAD_WAIT_MILLIS.milliseconds }

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

    /**
     * The value of [preference] now, kept as the one in use until the app restarts.
     *
     * Read before the settings are loaded (like the player made when a media button starts the
     * app), it blocks until they are, then takes the default; up to [LOAD_WAIT_MILLIS] from the
     * first read, for all reads together.
     */
    fun <T> readOnce(preference: Preference<T>): T {
        if (!Preferences.isLoaded) {
            val left = -waitEnd.elapsedNow()
            if (left.isPositive())
                runBlocking { withTimeoutOrNull(left) { Preferences.awaitLoaded() } }
        }

        val value = preference.value
        read.update { if (preference in it) it else it + (preference to value) }
        return value
    }
}
