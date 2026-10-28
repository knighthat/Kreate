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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * What couldn't be loaded or saved, see [Preferences.failures].
 *
 * @param saving `false` when loading failed, defaults are used then; `true` when saving failed
 * after every attempt, the values went back to what is stored
 */
class PreferenceFailure(val store: Store, val saving: Boolean, val reason: Reason) {

    enum class Store {
        /** [Preferences] of the active profile */
        SETTINGS,

        /** [Secrets] of the active profile */
        SECRETS,

        /** Which profiles exist and which one is active, see [ProfileManager] */
        PROFILES,
    }

    enum class Reason {
        /** The file couldn't be read or written, like when the storage is full */
        STORAGE,

        /** The file is damaged, when loading it was replaced */
        DAMAGED,

        UNKNOWN,
    }
}

/**
 * Where the stores of this module report what failed, see [Preferences.failures].
 */
internal class FailureReports {

    private val queue = Channel<PreferenceFailure>(KEPT, BufferOverflow.DROP_OLDEST)

    /**
     * Each failure goes to one collector, those from before any collector wait for it.
     */
    val failures: Flow<PreferenceFailure> = queue.receiveAsFlow()

    fun report(store: PreferenceFailure.Store, saving: Boolean, reason: PreferenceFailure.Reason) {
        queue.trySend(PreferenceFailure(store, saving, reason))
    }

    private companion object {

        const val KEPT = 4
    }
}

/** Before each attempt of [retrying] */
private val RETRY_DELAYS_MILLIS = longArrayOf(0, 500, 1_000)

/**
 * Runs [block] until it succeeds, at most three times: at once, after half a second, then after
 * another second.
 *
 * @return the error of the last attempt, `null` once one succeeded
 */
internal suspend fun retrying(block: suspend () -> Unit): Exception? {
    lateinit var error: Exception
    for (wait in RETRY_DELAYS_MILLIS) {
        delay(wait)
        try {
            block()
            return null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = e
        }
    }
    return error
}
