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

package me.knighthat.kreate3.logging

import android.os.Build
import android.os.SystemClock
import androidx.work.ListenableWorker
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException

/**
 * Runs [work] of a background task and logs how it went under [tag]: that it started and on which
 * attempt, then how it ended and after how long. A task that is stopped says why where Android
 * tells (API 31+). A crash of it is logged before it goes on to [ListenableWorker]'s own handling.
 */
internal suspend fun ListenableWorker.logged(
    tag: String,
    work: suspend () -> ListenableWorker.Result,
): ListenableWorker.Result {
    val start = SystemClock.elapsedRealtime()
    Logger.i(tag = tag) { "Started, attempt $runAttemptCount" }
    try {
        val result = work()
        Logger.i(tag = tag) { "Finished with ${result::class.simpleName} after ${SystemClock.elapsedRealtime() - start} ms" }
        return result
    } catch (e: CancellationException) {
        Logger.i(tag = tag) {
            val reason = if (Build.VERSION.SDK_INT >= 31) ", reason code $stopReason" else ""
            "Stopped after ${SystemClock.elapsedRealtime() - start} ms$reason"
        }
        throw e
    } catch (e: Throwable) {
        Logger.e(e, tag) { "Crashed after ${SystemClock.elapsedRealtime() - start} ms" }
        throw e
    }
}
