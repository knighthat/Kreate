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
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.core.os.HandlerCompat
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import me.knighthat.kreate3.R
import me.knighthat.kreate3.auto.coverSource
import me.knighthat.kreate3.auto.smallCover
import me.knighthat.kreate3.download.SongDownloadService
import me.knighthat.kreate3.download.SongDownloads
import me.knighthat.kreate3.download.songItem
import me.knighthat.kreate3.notification.DownloadNotifications.Companion.MAX_SHOWN
import me.knighthat.kreate3.notification.DownloadNotifications.Companion.RECENT_MS
import me.knighthat.kreate3.notification.DownloadNotifications.Companion.SUMMARY_DELAY_MS
import me.knighthat.kreate3.playback.PlaybackService
import kotlin.time.Duration.Companion.seconds

/**
 * What a downloaded song's notification buttons do, each starts [PlaybackService] with
 * [intentAction] and the download's id as the intent's data.
 */
enum class DownloadedSongAction(@param:StringRes val labelRes: Int) {
    PLAY(R.string.action_play),
    PLAY_NEXT(R.string.action_play_next),
    ENQUEUE(R.string.action_queue);

    val intentAction: String
        get() = "me.knighthat.kreate3.DOWNLOADED_$name"

    companion object {
        /** @return `null` when [action] isn't the [intentAction] of one */
        fun of(action: String?): DownloadedSongAction? =
            entries.firstOrNull { it.intentAction == action }
    }
}

/**
 * The notifications of [AppChannel.DOWNLOADS], in three groups: the songs being downloaded, the
 * songs downloaded and the songs that failed. Each song is a notification of its group, tagged
 * with its download's id, with its cover and name, under a summary counting them. A downloaded
 * song can be played, played next or queued from it ([DownloadedSongAction]), a failed one
 * tried again.
 *
 * Called on the main thread, where the downloads report.
 *
 * @param scope where covers load, the app's global one
 */
@OptIn(UnstableApi::class)
class DownloadNotifications(
    private val context: Context,
    private val scope: CoroutineScope = MainScope(),
) {

    private val manager = NotificationManagerCompat.from(context)

    /** What is shown of each song being downloaded, by its download's id. */
    private val shown = mutableMapOf<String, String>()

    /** Posts the summaries a moment later, see [updateSummary]. */
    private val handler = Handler(Looper.getMainLooper())

    /** When each downloaded and failed song was last shown, by notification id then download id. */
    private val posted = mutableMapOf<Int, MutableMap<String, Long>>()

    /** The covers of the songs being downloaded, by download id, `null` while loading or none. */
    private val covers = mutableMapOf<String, Bitmap?>()

    /**
     * The summary of the songs being downloaded, the foreground notification of the download
     * service, after showing a notification for each of the first
     * [MAX_SHOWN] of [downloads]: its progress with Pause and Cancel, or Resume and Cancel once
     * paused. Called again about every second while they run, only a song whose progress or state
     * changed, or whose cover came, is shown again.
     *
     * @param notMetRequirements what the downloads wait for, like Wi-Fi, see `Requirements`
     * @param waitingForBattery whether the downloads wait for the battery
     */
    fun downloading(
        downloads: List<Download>,
        notMetRequirements: Int,
        waitingForBattery: Boolean,
    ): Notification {
        val unfinished = downloads.filter {
            it.state == Download.STATE_QUEUED
                    || it.state == Download.STATE_DOWNLOADING
                    || it.state == Download.STATE_RESTARTING
                    || it.state == Download.STATE_STOPPED
        }
        val listed = unfinished.take(MAX_SHOWN).associateBy { it.request.id }
        // The songs no longer downloaded
        (shown.keys - listed.keys).forEach { manager.cancel(it, DOWNLOADING_SONG_ID) }
        shown.keys.retainAll(listed.keys)
        covers.keys.retainAll(listed.keys)

        val waitingFor = when {
            waitingForBattery -> R.string.notification_downloads_subtitle_waiting_for_battery
            notMetRequirements and Requirements.NETWORK_UNMETERED != 0 ->
                R.string.notification_downloads_subtitle_waiting_for_wifi

            else -> null
        }
        listed.forEach { (id, download) ->
            val paused = download.state == Download.STATE_STOPPED
            val percent = download.percentDownloaded
                .takeIf {
                    download.state == Download.STATE_DOWNLOADING
                            && it != C.PERCENTAGE_UNSET.toFloat()
                }
                ?.toInt() ?: UNKNOWN
            if (id !in covers) {
                covers[id] = null
                // Shown again with it on the next update
                scope.launch {
                    val cover = coverOf(download) ?: return@launch
                    withContext(Dispatchers.Main) {
                        if (id !in covers) return@withContext
                        covers[id] = cover
                        shown.remove(id)
                    }
                }
            }
            val state = "$percent $paused $waitingFor"
            if (shown[id] == state) return@forEach
            shown[id] = state

            // A song that waits isn't in the service's group: the service stops while nothing
            // runs, and Android takes a group's songs away with its summary
            val waits = paused || waitingForBattery
            val song = song(download, if (waits) WAITING_GROUP else DOWNLOADING_GROUP, covers[id])
            when {
                paused -> song
                    .setContentText(context.getString(R.string.notification_downloads_subtitle_paused))
                    .addAction(0, context.getString(R.string.action_resume), resumeIntent(id))

                waitingFor != null -> song
                    .setContentText(context.getString(waitingFor))
                    .addAction(0, context.getString(R.string.action_pause), pauseIntent(id))

                else -> song
                    .setOngoing(true)
                    .setProgress(100, percent.coerceAtLeast(0), percent == UNKNOWN)
                    .addAction(0, context.getString(R.string.action_pause), pauseIntent(id))
            }
            song.addAction(0, context.getString(R.string.action_cancel), cancelIntent(id))
            context.notify(id, DOWNLOADING_SONG_ID, song.build())
        }

        val count =
            context.resources.getQuantityString(R.plurals.song, unfinished.size, unfinished.size)
        return context.notificationBuilder(AppChannel.DOWNLOADS)
            .setContentTitle(context.getString(R.string.notification_downloads_title_downloading))
            .setContentText(waitingFor?.let { "$count · ${context.getString(it)}" } ?: count)
            // The header of the group, the only text of the summary that shows from Android 7 on
            .setSubText(context.getString(R.string.notification_downloads_title_downloading))
            .setGroup(DOWNLOADING_GROUP)
            .setGroupSummary(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    /**
     * Moves the song of [download] to the downloaded ones, with buttons to play it, play it next
     * and queue it.
     */
    fun downloaded(download: Download) {
        val id = download.request.id
        val known = covers[id]
        cancelSong(id)
        scope.launch {
            val cover = known ?: coverOf(download)
            withContext(Dispatchers.Main) {
                val song = song(download, DOWNLOADED_GROUP, cover)
                DownloadedSongAction.entries.forEach {
                    song.addAction(0, context.getString(it.labelRes), playbackIntent(it, id))
                }
                finished(
                    song,
                    id,
                    DOWNLOADED_SONG_ID,
                    DOWNLOADED_ID,
                    DOWNLOADED_GROUP,
                    R.string.notification_downloads_title_downloaded,
                )
            }
        }
    }

    /** Moves the song of [download] to the failed ones, with a button to try it again. */
    fun failed(download: Download) {
        val id = download.request.id
        val known = covers[id]
        cancelSong(id)
        scope.launch {
            val cover = known ?: coverOf(download)
            withContext(Dispatchers.Main) {
                finished(
                    song(download, FAILED_GROUP, cover)
                        .setContentText(context.getString(R.string.error_download_failed))
                        .addAction(
                            0,
                            context.getString(R.string.action_retry),
                            retryIntent(download),
                        ),
                    id,
                    FAILED_SONG_ID,
                    FAILED_ID,
                    FAILED_GROUP,
                    R.string.notification_downloads_title_failed,
                )
            }
        }
    }

    /** The download with [id] is tried again: it leaves the failed ones. */
    fun retrying(id: String) {
        manager.cancel(id, FAILED_SONG_ID)
        posted[FAILED_SONG_ID]?.remove(id)
        updateSummary(
            FAILED_ID,
            FAILED_SONG_ID,
            FAILED_GROUP,
            R.string.notification_downloads_title_failed,
            leaving = id,
        )
    }

    /**
     * Takes away every notification of the download with [id], like when it is deleted or tried
     * again.
     */
    fun removed(id: String) {
        cancelSong(id)
        manager.cancel(id, DOWNLOADED_SONG_ID)
        posted[DOWNLOADED_SONG_ID]?.remove(id)
        updateSummary(
            DOWNLOADED_ID,
            DOWNLOADED_SONG_ID,
            DOWNLOADED_GROUP,
            R.string.notification_downloads_title_downloaded,
            leaving = id,
        )
        retrying(id)
    }

    /**
     * Takes away the progress of downloads left by a killed process. Call it when the app starts,
     * the download service shows the downloads that go on again.
     */
    fun clearStale() = activeNotifications()
        .filter { it.id == DOWNLOADING_SONG_ID }
        // A paused song stays, it can be resumed from there
        .filter { it.notification.flags and Notification.FLAG_ONGOING_EVENT != 0 }
        .forEach { manager.cancel(it.tag, it.id) }

    private fun cancelSong(id: String) {
        shown.remove(id)
        covers.remove(id)
        manager.cancel(id, DOWNLOADING_SONG_ID)
    }

    /** Shows [song] in [group], then its summary, dropping the oldest songs past [MAX_SHOWN]. */
    private fun finished(
        song: NotificationCompat.Builder,
        id: String,
        songId: Int,
        summaryId: Int,
        group: String,
        @StringRes title: Int,
    ) {
        // The children first, a summary posted alone shows nothing
        context.notify(id, songId, song.setAutoCancel(true).build())
        activeNotifications()
            .filter { it.id == songId && it.tag != id }
            .sortedByDescending { it.postTime }
            .drop(MAX_SHOWN - 1)
            .forEach {
                manager.cancel(it.tag, it.id)
                posted[songId]?.remove(it.tag)
            }
        posted.getOrPut(songId) { mutableMapOf() }[id] = SystemClock.elapsedRealtime()
        updateSummary(summaryId, songId, group, title, leaving = null)
    }

    /**
     * Shows the summary of [group] with the number of its songs, or takes it away when it has
     * none, [SUMMARY_DELAY_MS] from now: songs that finish together make one update, Android drops
     * updates that come too fast. The song tagged [leaving] doesn't count.
     */
    private fun updateSummary(
        summaryId: Int,
        songId: Int,
        group: String,
        @StringRes title: Int,
        leaving: String?,
    ) {
        leaving?.let { posted[songId]?.remove(it) }
        handler.removeCallbacksAndMessages(summaryId)
        HandlerCompat.postDelayed(
            handler,
            { showSummary(summaryId, songId, group, title, leaving) },
            summaryId,
            SUMMARY_DELAY_MS,
        )
    }

    /**
     * Android lists a notification a moment after it is posted, so the songs posted [RECENT_MS]
     * ago or less count too, once.
     */
    private fun showSummary(
        summaryId: Int,
        songId: Int,
        group: String,
        @StringRes title: Int,
        leaving: String?,
    ) {
        val now = SystemClock.elapsedRealtime()
        val recent = posted[songId].orEmpty().filterValues { now - it <= RECENT_MS }.keys
        val listed = activeNotifications().filter { it.id == songId }.mapNotNull { it.tag }
        val songs = (listed + recent - setOfNotNull(leaving)).toSet().size
        if (songs == 0) {
            manager.cancel(summaryId)
            return
        }
        context.notify(
            summaryId,
            context.notificationBuilder(AppChannel.DOWNLOADS)
                .setContentTitle(context.getString(title))
                .setContentText(context.resources.getQuantityString(R.plurals.song, songs, songs))
                .setSubText(context.getString(title))
                .setGroup(group)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()
        )
    }

    /** A song's notification in [group]: its [cover], its title, and its artist under it. */
    private fun song(
        download: Download,
        group: String,
        cover: Bitmap?
    ): NotificationCompat.Builder {
        val metadata = download.request.songItem?.mediaMetadata
        return context.notificationBuilder(AppChannel.DOWNLOADS)
            .setContentTitle(metadata?.title)
            .setContentText(metadata?.artist ?: metadata?.subtitle)
            .setLargeIcon(cover)
            .setGroup(group)
            .setOnlyAlertOnce(true)
    }

    /** The cover of the song of [download], `null` when it has none or it can't be loaded. */
    private suspend fun coverOf(download: Download): Bitmap? {
        val cover = download.request.songItem?.mediaMetadata?.artworkUri ?: return null
        return withTimeoutOrNull(COVER_WAIT) { context.smallCover(cover.coverSource()) }
    }

    /** Has [PlaybackService] do [action] with the song downloaded as [id]. */
    private fun playbackIntent(action: DownloadedSongAction, id: String): PendingIntent {
        val intent = Intent(context, PlaybackService::class.java)
            .setAction(action.intentAction)
            .setData(id.toUri())
        // Playing has to put the service in the foreground first
        return if (action == DownloadedSongAction.PLAY) foregroundService(intent)
        else PendingIntent.getService(context, 0, intent, PENDING_FLAGS)
    }

    /** Adds the download again, once more with its retries. */
    private fun retryIntent(download: Download): PendingIntent = foregroundService(
        DownloadService.buildAddDownloadIntent(
            context,
            SongDownloadService::class.java,
            download.request,
            true,
        )
            // One for each song
            .setData(download.request.id.toUri())
    )

    /** Pauses the download with [id]. */
    private fun pauseIntent(id: String): PendingIntent = PendingIntent.getService(
        context,
        PAUSE_REQUEST,
        DownloadService.buildSetStopReasonIntent(
            context,
            SongDownloadService::class.java,
            id,
            SongDownloads.STOP_REASON_PAUSED,
            false,
        ).setData(id.toUri()),
        PENDING_FLAGS,
    )

    /** Resumes the download with [id], the service goes in the foreground again. */
    private fun resumeIntent(id: String): PendingIntent = foregroundService(
        DownloadService.buildSetStopReasonIntent(
            context,
            SongDownloadService::class.java,
            id,
            Download.STOP_REASON_NONE,
            true,
        ).setData(id.toUri()),
        RESUME_REQUEST,
    )

    /** The active user leaves the download with [id], it stops once nobody wants it. */
    private fun cancelIntent(id: String): PendingIntent = PendingIntent.getService(
        context,
        CANCEL_REQUEST,
        Intent(context, SongDownloadService::class.java)
            .setAction(SongDownloadService.ACTION_CANCEL)
            .setData(id.toUri()),
        PENDING_FLAGS,
    )

    private fun foregroundService(intent: Intent, requestCode: Int = 0): PendingIntent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            PendingIntent.getForegroundService(context, requestCode, intent, PENDING_FLAGS)
        else
            PendingIntent.getService(context, requestCode, intent, PENDING_FLAGS)

    private fun activeNotifications() =
        context.getSystemService(NotificationManager::class.java)?.activeNotifications.orEmpty()

    companion object {

        // Clear of Media3's media notification, 1001, the messages' 2000s, the scan's 3000s,
        // the sleep timer's 4000s and the resumption's 5000. Each song is tagged with its id.
        /** The summary of the songs being downloaded, the download service's notification. */
        const val DOWNLOADING_ID = 6000
        private const val DOWNLOADED_ID = 6001
        private const val FAILED_ID = 6002
        private const val DOWNLOADING_SONG_ID = 6010
        private const val DOWNLOADED_SONG_ID = 6011
        private const val FAILED_SONG_ID = 6012

        private const val DOWNLOADING_GROUP = "me.knighthat.kreate3.DOWNLOADING"
        private const val WAITING_GROUP = "me.knighthat.kreate3.DOWNLOAD_WAITING"
        private const val DOWNLOADED_GROUP = "me.knighthat.kreate3.DOWNLOADED"
        private const val FAILED_GROUP = "me.knighthat.kreate3.DOWNLOAD_FAILED"

        // Each kind of button its own, so they never replace each other
        private const val PAUSE_REQUEST = 1
        private const val RESUME_REQUEST = 2
        private const val CANCEL_REQUEST = 3

        private const val PENDING_FLAGS =
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        /** How long Android may take to list a notification just posted. */
        private const val RECENT_MS = 3_000L

        /** How long a summary waits for the other songs that finish with it. */
        private const val SUMMARY_DELAY_MS = 1_000L

        /** Progress not known, like a song waiting for its turn. */
        private const val UNKNOWN = -1

        /** How long a cover may load, the notification shows without it after that. */
        private val COVER_WAIT = 10.seconds

        /** The system keeps at most 50 notifications of an app, room is left for the others. */
        private const val MAX_SHOWN = 12
    }
}
