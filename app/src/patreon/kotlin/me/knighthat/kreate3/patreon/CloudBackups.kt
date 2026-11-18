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
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.api.PatreonClaims
import me.knighthat.kreate3.api.PatreonFeature
import me.knighthat.kreate3.api.PatreonFileTooLargeException
import me.knighthat.kreate3.api.PatreonFiles
import me.knighthat.kreate3.api.PatreonSignedOutException
import me.knighthat.kreate3.api.PatreonStorageFullException
import me.knighthat.kreate3.api.PatreonTierException
import me.knighthat.kreate3.backup.Backup
import me.knighthat.kreate3.preferences.GlobalPreferences
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.Secrets
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "CloudBackups"

// The worker that runs as the app starts, and the one that waits for the next backup
private const val NOW_NAME = "cloud_backup_now"
private const val NEXT_NAME = "cloud_backup_next"

/** How long after a backup the next one is made. */
private val INTERVAL: Duration = 7.days

/**
 * The weekly cloud backup: the same zip as the Backup page's, uploaded with the Patreon account.
 * [start] runs [CloudBackupWorker] as the app starts; it backs up when the last one is a week old
 * or more ([LAST_FILE]), then waits for the next one with another worker, until cloud backup is
 * off (signed out, the tier without it, or [GlobalPreferences.CLOUD_BACKUP] off).
 */
class CloudBackups internal constructor(
    private val context: Context,
    private val account: PatreonAccount,
    private val files: PatreonFiles,
    private val backup: Backup,
    private val remoteBackups: RemoteBackups,
) {

    // When the last cloud backup was uploaded, in milliseconds since the epoch
    private val lastFile = File(context.noBackupFilesDir, LAST_FILE)

    /** How a run ended, for the worker. */
    enum class Outcome {
        /** Backed up, too early for it, or cloud backup is off. */
        DONE,

        /** It may work later (offline, the API failing), tried again with a wait. */
        RETRY,

        /** It won't work by trying again (no room, too big), the next week tries. */
        FAILED,
    }

    /** Runs a check as the app starts, with the network; one waiting or running already is kept. */
    fun start() {
        Logger.i(tag = TAG) { "Checking the cloud backup" }
        enqueue(NOW_NAME, ExistingWorkPolicy.KEEP, Duration.ZERO)
    }

    /**
     * Backs up when cloud backup is on and the last backup is [INTERVAL] old or more, then waits
     * for the next one.
     */
    internal suspend fun run(): Outcome {
        // The switch and the sign in are read from disk in a process the worker started
        Preferences.awaitLoaded()
        val token = Secrets.PATREON_TOKEN.current()
        when {
            token.isEmpty() -> {
                Logger.i(tag = TAG) { "Skipped, nobody is signed in to Patreon" }
                return Outcome.DONE
            }

            !GlobalPreferences.CLOUD_BACKUP.value -> {
                Logger.i(tag = TAG) { "Skipped, cloud backup is off" }
                return Outcome.DONE
            }

            // A token older than the features claim is refreshed by PatreonAccount, the next start sees
            !PatreonClaims.of(token).has(PatreonFeature.BACKUPS) -> {
                Logger.i(tag = TAG) { "Skipped, the tier doesn't have cloud backup" }
                return Outcome.DONE
            }
        }

        val since = (System.currentTimeMillis() - lastBackup()).milliseconds
        // A clock set back counts as due
        if (since in Duration.ZERO..<INTERVAL) {
            Logger.i(tag = TAG) { "Skipped, the last backup is $since old" }
            scheduleNext(INTERVAL - since)
            return Outcome.DONE
        }

        return try {
            upload()
            withContext(Dispatchers.IO) {
                lastFile.writeText(
                    System.currentTimeMillis().toString()
                )
            }
            remoteBackups.forget()
            Logger.i(tag = TAG) { "Backed up to the cloud" }
            scheduleNext(INTERVAL)
            Outcome.DONE
        } catch (e: PatreonSignedOutException) {
            Logger.w(e, TAG) { "Patreon's sign in ended, no cloud backup" }
            Outcome.DONE
        } catch (e: PatreonTierException) {
            Logger.w(e, TAG) { "The API says the tier has no cloud backup" }
            Outcome.DONE
        } catch (e: PatreonFileTooLargeException) {
            Logger.e(e, TAG) { "The backup is too big for the cloud" }
            scheduleNext(INTERVAL)
            Outcome.FAILED
        } catch (e: PatreonStorageFullException) {
            Logger.e(e, TAG) { "No room left in the cloud" }
            scheduleNext(INTERVAL)
            Outcome.FAILED
        } catch (e: IOException) {
            Logger.w(e, TAG) { "Couldn't back up to the cloud, trying again later" }
            Outcome.RETRY
        }
    }

    private suspend fun upload() {
        val zip = File(context.cacheDir, "cloud_backup.zip")
        try {
            // Cleaned up, less to send
            backup.export(zip.outputStream(), cleanUp = true)
            val data = withContext(Dispatchers.IO) { zip.readBytes() }
            account.withToken { files.upload(it, fileName(), data) }
        } finally {
            withContext(Dispatchers.IO) { zip.delete() }
        }
    }

    /** `0` when there is none or it can't be read. */
    private suspend fun lastBackup(): Long = withContext(Dispatchers.IO) {
        runCatching { lastFile.readText().trim().toLong() }.getOrDefault(0)
    }

    private fun scheduleNext(delay: Duration) {
        Logger.i(tag = TAG) { "The next cloud backup is in $delay" }
        enqueue(NEXT_NAME, ExistingWorkPolicy.REPLACE, delay)
    }

    private fun enqueue(name: String, policy: ExistingWorkPolicy, delay: Duration) {
        val request = OneTimeWorkRequestBuilder<CloudBackupWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .setInitialDelay(delay.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(name, policy, request)
    }

    companion object {

        /** In `noBackupFilesDir`, so it stays on the device. */
        internal const val LAST_FILE = "cloud_backup_last"

        /** Allowed by the API: letters, digits, `.`, `_` and `-`. */
        private fun fileName(): String =
            "kreate_backup_${
                SimpleDateFormat(
                    "yyyy-MM-dd_HH-mm-ss",
                    Locale.ROOT
                ).format(Date())
            }.zip"
    }
}
