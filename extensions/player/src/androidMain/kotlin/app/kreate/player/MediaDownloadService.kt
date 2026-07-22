package app.kreate.player

import android.app.Notification
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import app.kreate.internal.download.DownloadNotifications
import app.kreate.utils.NotificationUtil
import co.touchlab.kermit.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.random.Random


// DownloadService isn't meant to be interacted directly with
@OptIn(UnstableApi::class)
internal class MediaDownloadService : DownloadService(
    NotificationUtil.DOWNLOAD_FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
), KoinComponent {

    private val logger = Logger.withTag( "MediaDownloadService" )

    override fun getDownloadManager(): DownloadManager = get()

    // TODO: Convert to WorkManagerScheduler from `androidx.media3:media3-exoplayer-workmanager`
    override fun getScheduler(): Scheduler = PlatformScheduler(this, Random.nextInt())

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        // Piggyback on the periodic callback to refresh the per-item child notifications.
        DownloadNotifications.updateProgressChildren( this, downloads )
        // The returned notification is the collapsible group's parent (summary).
        return DownloadNotifications.buildProgressSummary( this, downloads, notMetRequirements )
    }

    override fun onCreate() {
        logger.v { "Initializing service" }

        // We use the 2-arg super constructor (no auto-created channel), so channels must
        // exist before super.onCreate() can promote us to the foreground.
        DownloadNotifications.ensureChannels( this )
        super.onCreate()

        logger.d { "Service created" }
    }

    override fun onDestroy() {
        logger.v { "Shutting down service" }

        super.onDestroy()

        logger.d { "Service destroyed" }
    }
}