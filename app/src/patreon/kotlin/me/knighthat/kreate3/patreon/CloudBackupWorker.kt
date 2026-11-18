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

package me.knighthat.kreate3.patreon

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import me.knighthat.kreate3.logging.logged
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private const val TAG = "CloudBackupWorker"

/** Runs [CloudBackups.run]: a cloud backup when one is due, then the wait for the next. */
class CloudBackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val backups: CloudBackups by inject()

    override suspend fun doWork(): Result = logged(TAG) {
        when (backups.run()) {
            CloudBackups.Outcome.DONE -> Result.success()
            CloudBackups.Outcome.RETRY -> Result.retry()
            CloudBackups.Outcome.FAILED -> Result.failure()
        }
    }
}
