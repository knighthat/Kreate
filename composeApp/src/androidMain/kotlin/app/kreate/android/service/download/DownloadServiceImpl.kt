package app.kreate.android.service.download

import android.app.Notification
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.service.playback.PlaybackService
import app.kreate.di.CacheType
import app.kreate.di.DatasourceType
import co.touchlab.kermit.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.Executors

@OptIn(UnstableApi::class)
class DownloadServiceImpl :
    DownloadService(
        FOREGROUND_NOTIFICATION_ID,
        DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
        CHANNEL_ID,
        R.string.notification_category_download,
        R.string.notification_description_download
    ),
    DownloadManager.Listener,
    KoinComponent
{

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 100
        private const val DOWNLOADS_STATUS_NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "download"
        private const val GROUP_KEY = "group.download.status"
    }

    private val executor = Executors.newCachedThreadPool()
    private val logger = Logger.withTag("DownloadService")

    private lateinit var notificationHelper: DownloadNotificationHelper

    override fun getDownloadManager(): DownloadManager {
        val cache: Cache by inject(CacheType.DOWNLOAD)
        val upstreamFactory: ResolvingDataSource.Factory by inject(DatasourceType.DOWNLOADER)
        val databaseProvider = StandaloneDatabaseProvider(this)

        return DownloadManager(this, databaseProvider, cache, upstreamFactory, executor)
            .also {
                it.addListener( this@DownloadServiceImpl )
                val cacheState: CacheState by inject()
                it.addListener( cacheState as CacheStateImpl )

                it.maxParallelDownloads = Preferences.DOWNLOAD_MAX_PARALLEL.value
                it.minRetryCount = 2
            }
    }

    override fun getScheduler(): Scheduler = PlatformScheduler(this, 1)

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        val activeCount = downloads.count { it.state == Download.STATE_QUEUED || it.state == Download.STATE_DOWNLOADING }
        val title = getString(
            R.string.notification_message_download_in_progress,
            resources.getQuantityString( R.plurals.download, activeCount, activeCount )
        )

        return notificationHelper.buildProgressNotification(
            this,
            R.drawable.download_progress,
            null,
            title,
            downloads,
            notMetRequirements
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logger.v { "Received onStartCommand(${intent?.action}, $flags, $startId)" }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        logger.v { "Starting DownloadService..." }

        /**
         * [notificationHelper] cannot be init at the same time with the service
         * because [android.content.Context] isn't available by then.
         *
         * However, it must be initialized before [getForegroundNotification] is called,
         * which is some time after [onCreate]. Thus, this place is perfect for creating
         * this helper.
         */
        notificationHelper = DownloadNotificationHelper(this, CHANNEL_ID)

        super.onCreate()
    }

    override fun onDestroy() {
        logger.v { "Stopping DownloadService..." }

        downloadManager.removeListener( this )
        super.onDestroy()
    }

    /*
     *  Download manager listener
     */

    private fun buildNotification(
        @DrawableRes smallIconId: Int,
        title: String,
        content: String?,
        isGroupSummary: Boolean
    ) =
        NotificationCompat.Builder(this, CHANNEL_ID)
                          .setSmallIcon( smallIconId )
                          .setContentTitle( title )
                          .setContentText( content )
                          .setGroup( GROUP_KEY )
                          .setGroupSummary( isGroupSummary )
                          .setOngoing( false )
                          .setShowWhen( true )
                          .build()

    override fun onDownloadChanged(
        downloadManager: DownloadManager,
        download: Download,
        finalException: Exception?
    ) {
        logger.v { "Download changed. State: ${download.state}, bytes downloaded: ${download.bytesDownloaded}" }

        val downloadId = download.request.id
        finalException?.also {
            logger.e( it ) { "Download %s failed at %.2f%%".format(downloadId, download.percentDownloaded) }
        }

        //<editor-fold desc="Send request to update media control">
        val intent = Intent(this, PlaybackService::class.java)
            .setAction( PlaybackService.ACTION_UPDATE_MEDIA_CONTROL )
            .putExtra( PlaybackService.KEY_CURRENT_SONG_ID, downloadId )
        startService( intent )
        //</editor-fold>
        //<editor-fold desc="Finish notification">
        val filename = String(download.request.data)
        buildNotification(
            smallIconId = R.drawable.downloaded,
            title = filename,
            content = getString( androidx.media3.exoplayer.R.string.exo_download_failed ),
            isGroupSummary = false
        )
        val notification = when( download.state ) {
            Download.STATE_COMPLETED ->
                buildNotification(
                    smallIconId = R.drawable.downloaded,
                    title = filename,
                    content = getString( androidx.media3.exoplayer.R.string.exo_download_completed ),
                    isGroupSummary = false
                )

            Download.STATE_FAILED ->
                buildNotification(
                    smallIconId = R.drawable.downloaded,
                    title = filename,
                    content = getString( androidx.media3.exoplayer.R.string.exo_download_failed ),
                    isGroupSummary = false
                )

            else -> null
        }
        notification?.let {
            logger.v { "Notifying user about finished download ($downloadId) $filename: ${download.state}" }

            NotificationUtil.setNotification(this, downloadId.hashCode(), it)
            // To make all finished notification group together,
            // a group summary must be posted along with them
            val summaryNotification = buildNotification(
                smallIconId = R.drawable.folder,
                title = getString( androidx.media3.exoplayer.R.string.exo_download_notification_channel_name ),
                content = null,
                isGroupSummary = true
            )
            NotificationUtil.setNotification(this, DOWNLOADS_STATUS_NOTIFICATION_ID, summaryNotification)
        }
        //</editor-fold>
    }

    override fun onDownloadRemoved( downloadManager: DownloadManager, download: Download ) {
        logger.v { "Download removed. State: ${download.state}, bytes downloaded: ${download.bytesDownloaded}" }

        //<editor-fold desc="Send request to update media control">
        val intent = Intent(this, PlaybackService::class.java)
            .setAction( PlaybackService.ACTION_UPDATE_MEDIA_CONTROL )
            .putExtra( PlaybackService.KEY_CURRENT_SONG_ID, download.request.id )
        startService( intent )
        //</editor-fold>
    }
}