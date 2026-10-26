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

package me.knighthat.kreate3.notification

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import me.knighthat.kreate3.R

/**
 * The two notifications of [AppChannel.LOCAL_SCAN]: one for reading the device's music, one for
 * measuring its loudness. Each shows a progress bar while its work runs, then what came of it.
 * The songs that couldn't be measured are listed as children of the loudness one.
 */
class LocalScanNotifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    /** When the loudness progress was last shown, `0` before the first time of a run. */
    @Volatile
    private var measuringShownAt = 0L

    /** Shows that the device's music is being read. */
    fun scanning() = context.notify(
        SCAN_ID,
        progress("Scanning music")
            .setProgress(0, 0, true)
            .build()
    )

    /** Shows that [count] songs were read. */
    fun scanned(count: Int) = context.notify(
        SCAN_ID,
        done("Music scan finished")
            .setContentText(if (count == 1) "1 song scanned" else "$count songs scanned")
            .build()
    )

    /** Takes the scan's notification away, for a scan that was stopped or failed. */
    fun cancelScan() = manager.cancel(SCAN_ID)

    /**
     * Shows that [measured] of [total] songs went through, measured or not. Shown at most once a
     * second, the system drops updates that come too fast.
     */
    fun measuring(measured: Int, total: Int) {
        val now = SystemClock.elapsedRealtime()
        // The songs that failed last time are tried again, their old notifications go
        if (measuringShownAt == 0L) cancelFailures()
        else if (now - measuringShownAt < PROGRESS_INTERVAL_MS) return
        measuringShownAt = now

        context.notify(
            LOUDNESS_ID,
            progress("Measuring loudness")
                .setContentText("$measured/$total")
                .setProgress(total, measured, false)
                .build()
        )
    }

    /**
     * Shows that [analyzed] of [total] songs were measured, with a child notification for each
     * song in [failed], by name.
     */
    fun measured(analyzed: Int, total: Int, failed: List<String>) {
        measuringShownAt = 0L
        cancelFailures()

        val summary = done("Loudness measured")
            .setContentText("$analyzed/$total")
        if (failed.isEmpty()) {
            context.notify(LOUDNESS_ID, summary.build())
            return
        }

        val shown = failed.take(MAX_FAILED_SHOWN)
        // The children first, a summary posted alone shows nothing
        shown.forEachIndexed { index, name ->
            context.notify(
                FAILED_FIRST_ID + index,
                done(name)
                    .setContentText(context.getString(R.string.error_loudness_failed))
                    .setGroup(FAILED_GROUP)
                    .build()
            )
        }
        context.notify(
            LOUDNESS_ID,
            summary
                // The header of the group, the only text of the summary that shows from Android 7 on
                .setSubText(
                    if (shown.size == failed.size) "$analyzed/$total measured"
                    else "$analyzed/$total, ${shown.size} of ${failed.size} failures listed"
                )
                .setGroup(FAILED_GROUP)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                .build()
        )
    }

    /** Takes the loudness notification away, for a run that was stopped. */
    fun cancelLoudness() {
        measuringShownAt = 0L
        cancelFailures()
        manager.cancel(LOUDNESS_ID)
    }

    /**
     * Takes away the progress of work that no longer runs, as after the app was killed. Call it
     * when the app starts, before any work: what came of earlier work stays.
     */
    fun clearStale() = activeNotifications()
        .filter { it.notification.flags and Notification.FLAG_ONGOING_EVENT != 0 }
        .filter { it.id == SCAN_ID || it.id == LOUDNESS_ID }
        .forEach { manager.cancel(it.id) }

    private fun cancelFailures() = activeNotifications()
        .filter { it.notification.group == FAILED_GROUP && it.id != LOUDNESS_ID }
        .forEach { manager.cancel(it.id) }

    private fun activeNotifications() =
        context.getSystemService(NotificationManager::class.java)?.activeNotifications.orEmpty()

    /** Work still running, which the user can't swipe away. */
    private fun progress(title: String) = context.notificationBuilder(AppChannel.LOCAL_SCAN)
        .setContentTitle(title)
        .setOngoing(true)
        .setOnlyAlertOnce(true)

    /** What came of the work. */
    private fun done(title: String) = context.notificationBuilder(AppChannel.LOCAL_SCAN)
        .setContentTitle(title)
        .setAutoCancel(true)
        .setOnlyAlertOnce(true)

    private companion object {

        // Clear of Media3's media notification, 1001, and the messages' 2000s
        const val SCAN_ID = 3000
        const val LOUDNESS_ID = 3001
        const val FAILED_FIRST_ID = 3100

        const val FAILED_GROUP = "me.knighthat.kreate3.LOUDNESS_FAILED"

        const val PROGRESS_INTERVAL_MS = 1_000L

        /** The system keeps at most 50 notifications of an app, room is left for the others. */
        const val MAX_FAILED_SHOWN = 40
    }
}
