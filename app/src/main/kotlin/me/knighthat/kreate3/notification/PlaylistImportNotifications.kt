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
import android.icu.text.NumberFormat
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import me.knighthat.kreate3.R
import me.knighthat.kreate3.playlist.FailedSong
import me.knighthat.kreate3.playlist.ImportStep
import me.knighthat.kreate3.playlist.PlaylistOutcome
import me.knighthat.kreate3.playlist.SearchCount
import me.knighthat.kreate3.playlist.text
import me.knighthat.kreate3.ui.component.labelRes
import java.util.concurrent.ConcurrentHashMap

/**
 * The notifications of [AppChannel.PLAYLIST_IMPORT]. While a file is imported, one shows what the
 * import does, like its placeholders on the Playlists tab, with a progress bar while songs are
 * searched for. Then each playlist gets one saying how it went; the songs that didn't go in are
 * its children, one each, by name. Imports going on at the same time each have their own.
 */
class PlaylistImportNotifications(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    /** When each import's progress was last shown, by import id. */
    private val shownAt = ConcurrentHashMap<Long, Long>()

    /** The step and names each import's progress last showed, by import id. */
    private val shownState = ConcurrentHashMap<Long, Pair<ImportStep, List<String>>>()

    /**
     * Shows what the import [importId] of the playlists [names] does: [step], or [searched] songs
     * of how many with a progress bar while searching. A new step or new names show at once, a new
     * count at most once a second: the system drops updates that come too fast.
     */
    fun progress(importId: Long, names: List<String>, step: ImportStep, searched: SearchCount?) {
        val now = SystemClock.elapsedRealtime()
        val state = step to names
        if (shownState[importId] == state && now - (shownAt[importId] ?: 0L) < PROGRESS_INTERVAL_MS)
            return
        shownAt[importId] = now
        shownState[importId] = state

        val counting = step == ImportStep.SEARCHING && searched != null
        val text = if (counting) {
            val numbers =
                NumberFormat.getIntegerInstance(context.resources.configuration.locales[0])
            context.getString(
                R.string.status_import_searched,
                numbers.format(searched.done),
                numbers.format(searched.total),
            )
        } else {
            context.getString(step.labelRes)
        }
        val notification = context.notificationBuilder(AppChannel.PLAYLIST_IMPORT)
            // The names as the file gives them, data, not a sentence
            .setContentTitle(names.joinToString(", "))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .apply {
                when {
                    counting -> setProgress(searched.total, searched.done, false)
                    // Nothing runs while the user is asked
                    step != ImportStep.WAITING -> setProgress(0, 0, true)
                }
            }
            .build()
        context.notify(progressTag(importId), PROGRESS_ID, notification)
    }

    /** Takes the progress of the import [importId] away, it ended or was dropped. */
    fun cancelProgress(importId: Long) {
        shownAt.remove(importId)
        shownState.remove(importId)
        manager.cancel(progressTag(importId), PROGRESS_ID)
    }

    /**
     * Shows how the playlist [key] of an import went, see [PlaylistOutcome]: all its songs went
     * in, some did (each one that didn't as a child), or none did.
     */
    fun result(key: Long, outcome: PlaylistOutcome) {
        val group = "$GROUP_PREFIX$key"
        val summary = context.notificationBuilder(AppChannel.PLAYLIST_IMPORT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
        val songs = context.resources.getQuantityString(
            R.plurals.song,
            outcome.imported,
            outcome.imported,
        )

        if (outcome.failed.isEmpty()) {
            summary
                .setContentTitle(
                    context.getString(
                        R.string.notification_playlist_import_title_imported,
                        outcome.name
                    )
                )
                .setContentText(
                    if (outcome.fromYouTube)
                        context.getString(
                            R.string.notification_playlist_import_subtitle_all_found,
                            songs,
                            context.getString(R.string.source_ytm),
                        )
                    else
                        context.getString(
                            R.string.notification_playlist_import_subtitle_all_imported,
                            songs
                        )
                )
            context.notify(resultTag(key), RESULT_ID, summary.build())
            return
        }

        val total = outcome.imported + outcome.failed.size
        val shown = outcome.failed.take(MAX_FAILED_SHOWN)
        // The children first, a summary posted alone shows nothing
        shown.forEachIndexed { index, song ->
            context.notify(failedTag(key, index), FAILED_ID, failed(song, outcome.name, group))
        }
        val text = if (outcome.imported == 0)
            context.getString(R.string.notification_playlist_import_subtitle_nothing)
        else
            context.getString(
                R.string.notification_playlist_import_subtitle_some_imported,
                outcome.imported,
                context.resources.getQuantityString(R.plurals.song, total, total),
            )
        val title = context.getString(
            if (outcome.imported == 0)
                R.string.notification_playlist_import_title_not_imported
            else
                R.string.notification_playlist_import_title_imported_with_errors,
            outcome.name,
        )
        context.notify(
            resultTag(key),
            RESULT_ID,
            summary
                .setContentTitle(title)
                .setContentText(text)
                // The header of the group, the only text of the summary that shows from Android 7
                // on: the title, else the group would say nothing of the playlist
                .setSubText(
                    if (shown.size == outcome.failed.size)
                        title
                    else
                        context.getString(
                            R.string.notification_playlist_import_subtitle_failures_listed,
                            title,
                            shown.size,
                            outcome.failed.size,
                        )
                )
                .setGroup(group)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                .build()
        )
    }

    /**
     * Takes away the progress of imports that no longer run, as after the app was killed. Call it
     * when the app starts: what came of earlier imports stays.
     */
    fun clearStale() = activeNotifications()
        .filter { it.id == PROGRESS_ID && it.notification.flags and Notification.FLAG_ONGOING_EVENT != 0 }
        .forEach { manager.cancel(it.tag, it.id) }

    /**
     * A child of [group]: [song]'s title, why it didn't go in, and the name of its [playlist] in
     * the header (a group of one child shows only the child).
     */
    private fun failed(song: FailedSong, playlist: String, group: String): Notification =
        context.notificationBuilder(AppChannel.PLAYLIST_IMPORT)
            .setContentTitle(song.title)
            .setContentText(song.reason.text(context.resources).replaceFirstChar { it.uppercase() })
            .setSubText(playlist)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setGroup(group)
            .build()

    private fun activeNotifications() =
        context.getSystemService(NotificationManager::class.java)?.activeNotifications.orEmpty()

    private fun progressTag(importId: Long) = "playlist_import:$importId"

    private fun resultTag(key: Long) = "playlist_import_result:$key"

    private fun failedTag(key: Long, index: Int) = "playlist_import_failed:$key:$index"

    private companion object {

        // Clear of Media3's media notification, 1001, the messages' 2000s, LocalScanNotifications'
        // 3000s, the sleep timer's 4000s and DownloadNotifications' 6000s; tags tell imports apart
        const val PROGRESS_ID = 7000
        const val RESULT_ID = 7001
        const val FAILED_ID = 7002

        const val GROUP_PREFIX = "me.knighthat.kreate3.PLAYLIST_IMPORT_"

        const val PROGRESS_INTERVAL_MS = 1_000L

        /** The system keeps at most 50 notifications of an app, room is left for the others. */
        const val MAX_FAILED_SHOWN = 20
    }
}
