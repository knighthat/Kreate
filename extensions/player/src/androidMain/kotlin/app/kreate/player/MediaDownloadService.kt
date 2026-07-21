package app.kreate.player

import android.app.Notification
import android.content.Context
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import app.kreate.utils.NotificationUtil
import co.touchlab.kermit.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.random.Random


// DownloadService isn't meant to be interacted directly with
@OptIn(UnstableApi::class)
internal class MediaDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    DOWNLOAD_NOTIFICATION_CHANNEL_ID,
    R.string.notification_channel_name_download,
    0
), KoinComponent {

    companion object {

        const val FOREGROUND_NOTIFICATION_ID = NotificationUtil.DOWNLOAD_FOREGROUND_NOTIFICATION_ID
        const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "download_channel"
    }

    private val logger = Logger.withTag( "MediaDownloadService" )

    private lateinit var notification: DownloadNotificationHelper
    private lateinit var listener: DownloadManager.Listener

    override fun getDownloadManager(): DownloadManager = get()

    override fun getScheduler(): Scheduler = PlatformScheduler(this, Random.nextInt())

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ) =
        NotificationCompat.Builder(
            /* context = */ this,
            /* notification = */ notification
                .buildProgressNotification(
                    /* context            = */ this,
                    /* smallIcon          = */ android.R.drawable.stat_sys_download,
                    /* contentIntent      = */ null,
                    /* message            = */ "${downloads.size} in progress",
                    /* downloads          = */ downloads,
                    /* notMetRequirements = */ notMetRequirements
                )
        )
        .setChannelId(DOWNLOAD_NOTIFICATION_CHANNEL_ID)
        .build()

    override fun onCreate() {
        logger.v { "Initializing service" }

        super.onCreate()

        this.notification = DownloadNotificationHelper(this, DOWNLOAD_NOTIFICATION_CHANNEL_ID)
        this.listener = TerminalStateNotificationHelper(this, notification, FOREGROUND_NOTIFICATION_ID + 1)
        downloadManager.addListener( listener )

        logger.d { "Service created" }
    }

    override fun onDestroy() {
        logger.v { "Shutting down service" }

        super.onDestroy()

        downloadManager.removeListener( listener )

        logger.d { "Service destroyed" }
    }

    private class TerminalStateNotificationHelper(
        private val context: Context,
        private val notificationHelper: DownloadNotificationHelper,
        firstNotificationId: Int
    ) : DownloadManager.Listener {
        private var nextNotificationId: Int = firstNotificationId

        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?
        ) {
            val notification: Notification = when (download.state) {
                Download.STATE_COMPLETED -> {
                    notificationHelper.buildDownloadCompletedNotification(
                        context,
                        android.R.drawable.stat_sys_download_done,
                        null,
                        Util.fromUtf8Bytes(download.request.data)
                    )
                }
                Download.STATE_FAILED -> {
                    notificationHelper.buildDownloadFailedNotification(
                        context,
                        android.R.drawable.stat_notify_error,
                        null,
                        Util.fromUtf8Bytes(download.request.data)
                    )
                }
                else -> return
            }
            androidx.media3.common.util.NotificationUtil.setNotification(context, nextNotificationId++, notification)
        }
    }
}