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

package me.knighthat.kreate.source.local

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import me.knighthat.kreate.source.local.LocalMusicScheduler.scanNow
import java.util.concurrent.TimeUnit

/**
 * Decides when [LocalMusicWorker] runs.
 */
object LocalMusicScheduler {

    private const val PERIODIC_NAME = "local_music_scan"
    private const val ONCE_NAME = "local_music_scan_now"
    private const val LOUDNESS_NAME = "local_music_loudness"

    private val MIN_INTERVAL_MINUTES =
        TimeUnit.MILLISECONDS.toMinutes(PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS)

    /**
     * Runs the scan every [intervalMinutes], the first one after a full interval. Use [scanNow]
     * for a scan right away. Calling it again with another interval changes the schedule
     * without restarting the wait. The system doesn't run periodic
     * work more often than every 15 minutes, smaller values are raised to that.
     */
    fun schedule(context: Context, intervalMinutes: Int) {
        val request = PeriodicWorkRequestBuilder<LocalMusicWorker>(
            maxOf(intervalMinutes.toLong(), MIN_INTERVAL_MINUTES),
            TimeUnit.MINUTES,
        ).setInitialDelay(maxOf(intervalMinutes.toLong(), MIN_INTERVAL_MINUTES), TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /**
     * Runs one extra scan right away, for example after the permission was granted.
     * Does nothing while an earlier extra scan is still waiting or running.
     */
    fun scanNow(context: Context) {
        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                ONCE_NAME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<LocalMusicWorker>().build()
            )
    }

    /**
     * Measures the loudness of the songs that have none yet with [LoudnessWorker], while the
     * battery isn't low. Does nothing while an earlier run is still waiting or running, that run
     * picks up the new songs too.
     */
    fun measureLoudness(context: Context) {
        val request = OneTimeWorkRequestBuilder<LoudnessWorker>()
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(LOUDNESS_NAME, ExistingWorkPolicy.KEEP, request)
    }
}
