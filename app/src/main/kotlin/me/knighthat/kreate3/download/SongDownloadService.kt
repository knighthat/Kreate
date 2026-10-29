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

package me.knighthat.kreate3.download

import android.app.Notification
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import me.knighthat.kreate3.AppLanguage
import me.knighthat.kreate3.notification.DownloadNotifications
import org.koin.android.ext.android.get

/**
 * Runs the downloads of [SongDownloads] in the foreground, its notification is the summary of
 * the songs being downloaded, see [DownloadNotifications.downloading]. Besides Media3's actions it
 * takes [ACTION_CANCEL], which only the active user leaves.
 */
@OptIn(UnstableApi::class)
class SongDownloadService : DownloadService(DownloadNotifications.DOWNLOADING_ID) {

    private val downloads: SongDownloads by lazy { get() }

    // In the language picked in the app, see AppLanguage
    override fun attachBaseContext(newBase: Context) =
        super.attachBaseContext(AppLanguage.wrap(newBase))

    override fun onCreate() {
        super.onCreate()
        // Starting resumed every download, those waiting for the battery pause again
        downloads.applyBatteryPause()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            intent.dataString?.let(downloads::leave)
            // Nothing else to do, the service carries on as for any start
            intent.action = ACTION_INIT
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun getDownloadManager(): DownloadManager = downloads.manager

    // Downloads don't wait for anything but Wi-Fi, which keeps the service in the foreground
    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: List<Download>,
        notMetRequirements: Int,
    ): Notification = get<DownloadNotifications>().downloading(
        downloads,
        notMetRequirements,
        this.downloads.pausedForBattery.value,
    )

    companion object {
        /**
         * The active user leaves the download whose id is the intent's data, see
         * [SongDownloads.leave].
         */
        const val ACTION_CANCEL = "me.knighthat.kreate3.download.CANCEL"
    }
}
