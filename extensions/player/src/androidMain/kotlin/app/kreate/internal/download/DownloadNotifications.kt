package app.kreate.internal.download

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.scheduler.Requirements
import app.kreate.player.DownloadActionReceiver
import app.kreate.player.MediaDownloadService
import app.kreate.player.R
import app.kreate.utils.IS_ANDROID_8_OR_LATER
import app.kreate.utils.IS_ANDROID_9_OR_LATER
import app.kreate.utils.NotificationUtil


/**
 * Central place for every notification this [MediaDownloadService] posts.
 */
@androidx.annotation.OptIn(UnstableApi::class)
internal object DownloadNotifications {

    const val CHANNEL_PROGRESS = NotificationUtil.DOWNLOAD_IN_PROGRESS_CHANNEL_ID
    const val CHANNEL_FINISHED = NotificationUtil.DOWNLOAD_FINISHED_CHANNEL_ID

    private const val GROUP_PROGRESS = "app.kreate.internal.download.group.PROGRESS"
    private const val GROUP_FINISHED = "app.kreate.internal.download.group.FINISHED"

    // Disjoint id spaces so a progress child can never collide with a finished child.
    private const val PROGRESS_CHILD_BASE = 0x10_0000
    private const val FINISHED_CHILD_BASE = 0x20_0000

    /** Progress children we currently have on screen, so stale ones can be canceled. */
    private val shownProgressChildIds = mutableSetOf<Int>()

    private fun progressChildId( requestId: String ) =
        PROGRESS_CHILD_BASE + (requestId.hashCode() and 0xF_FFFF)

    private fun finishedChildId( requestId: String ) =
        FINISHED_CHILD_BASE + (requestId.hashCode() and 0xF_FFFF)

    // ---------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------

    /**
     * The title travels inside DownloadRequest.data (see MediaDownloader), which is the
     * idiomatic media3 way to keep display metadata available after process restarts —
     * the request is all that's persisted in the download index.
     */
    private fun Download.displayTitle(): String =
        request.data.takeIf { it.isNotEmpty() }?.toString(Charsets.UTF_8) ?: request.id

    // TODO: Add support to open `downloading` screen that shows all downloading requests
    private fun launchAppPendingIntent( context: Context ): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        return PendingIntent.getActivity( context, 0, launch, flags )
    }

    fun ensureChannels( context: Context ) {
        if( !IS_ANDROID_8_OR_LATER ) return

        //<editor-fold defaultstate="collapsed" desc="Notification group">
        val group = NotificationChannelGroup(
            NotificationUtil.DOWNLOAD_CHANNEL_GROUP_ID,
            context.getString( R.string.notification_group_name_download )
        )
        if( IS_ANDROID_9_OR_LATER )
            group.description = context.getString( R.string.notification_group_description_download )
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="On-going channel">
        val ongoingChannel = NotificationChannel(
            NotificationUtil.DOWNLOAD_IN_PROGRESS_CHANNEL_ID,
            context.getString( R.string.notification_channel_name_in_progress ),
            // LOW = silent, no peeking — correct for once-per-second progress updates.
            NotificationManager.IMPORTANCE_LOW,
        )
        ongoingChannel.description = context.getString( R.string.notification_channel_description_in_progress )
        ongoingChannel.setShowBadge( false )
        ongoingChannel.group = NotificationUtil.DOWNLOAD_CHANNEL_GROUP_ID
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="Complete channel">
        val completeChannel = NotificationChannel(
            NotificationUtil.DOWNLOAD_FINISHED_CHANNEL_ID,
            context.getString( R.string.notification_channel_name_complete ),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        completeChannel.description = context.getString( R.string.notification_channel_description_complete )
        completeChannel.group = NotificationUtil.DOWNLOAD_CHANNEL_GROUP_ID
        //</editor-fold>

        context.getSystemService<NotificationManager>()?.run {
            createNotificationChannelGroup( group )
            createNotificationChannel( ongoingChannel )
            createNotificationChannel( completeChannel )
        }
    }

    // ---------------------------------------------------------------------------------
    // PROGRESS group
    // ---------------------------------------------------------------------------------

    //region In-progress group
    /** The collapsible parent. Returned from DownloadService.getForegroundNotification(). */
    fun buildProgressSummary(
        context: Context,
        downloads: List<Download>,
        @Requirements.RequirementFlags notMetRequirements: Int,
    ): Notification {
        val downloading = downloads.count { it.state == Download.STATE_DOWNLOADING }
        val title = when {
            notMetRequirements != 0 -> context.getString( R.string.notification_title_downloads_waiting )
            else -> context.getString( R.string.notification_title_downloads_in_progress )
        }
        val text = when {
            // TODO: Implement switch to enable download on unmetered networks, then add string for this
            notMetRequirements and Requirements.NETWORK_UNMETERED != 0 -> "Waiting for Wi-Fi"
            notMetRequirements and Requirements.NETWORK != 0 -> context.getString( R.string.notification_description_downloads_wait_connection )
            notMetRequirements != 0 -> context.getString( R.string.notification_description_downloads_wait_device )
            else -> context.getString( R.string.notification_description_downloads_downloading, downloading, downloads.size )
        }

        return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
                                 .setSmallIcon( android.R.drawable.stat_sys_download )
                                 .setContentTitle( title )
                                 .setContentText( text )
                                 .setGroup( GROUP_PROGRESS )
                                 .setGroupSummary( true )
                                 .setOngoing( true )
                                 .setOnlyAlertOnce( true )
                                 .setForegroundServiceBehavior( NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE )
                                 .setContentIntent( launchAppPendingIntent(context) )
                                 .build()
    }

    /**
     * Called every notification-update tick from the service with the current downloads.
     * Posts/updates one child per active item and cancels children of items that left the
     * in-progress set.
     */
    @SuppressLint("MissingPermission") // guarded by canPostNotifications()
    fun updateProgressChildren( context: Context, downloads: List<Download> ) {
        val nm = NotificationManagerCompat.from(context)
        val active = HashSet<Int>()

        if ( NotificationUtil.canPostNotification(context) ) {
            downloads.filter { it.state == Download.STATE_DOWNLOADING }
                     .forEach { download ->
                         val id = progressChildId( download.request.id )
                         active += id
                         nm.notify( id, buildProgressChild(context, download) )
                     }
        }

        // This cancels all "stale" notification, and set all just-notified
        // ids to shownProgressChildIds
        synchronized( shownProgressChildIds ) {
            (shownProgressChildIds - active).forEach( nm::cancel )
            shownProgressChildIds.clear()
            shownProgressChildIds += active
        }
    }

    private fun buildProgressChild( context: Context, download: Download ): Notification {
        val percent = download.percentDownloaded // C.PERCENTAGE_UNSET (-1f) if size unknown
        val indeterminate = download.state != Download.STATE_DOWNLOADING || percent < 0f
        val text = when( download.state ) {
            Download.STATE_RESTARTING -> context.getString( R.string.notification_description_state_restarting )
            Download.STATE_DOWNLOADING -> context.getString( R.string.notification_description_state_downloading, percent.toInt() )
            else -> ""
        }

        return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
                                 .setSmallIcon( android.R.drawable.stat_sys_download )
                                 .setContentTitle( download.displayTitle() )
                                 .setContentText( text )
                                 .setProgress( 100, percent.toInt().coerceIn(0, 100), indeterminate )
                                 .setGroup( GROUP_PROGRESS )
                                 .setSortKey( download.displayTitle() ) // stable ordering inside the group
                                 .setOngoing( true )                    // not swipeable while in flight
                                 .setOnlyAlertOnce( true )
                                 .setContentIntent( launchAppPendingIntent(context) )
                                 // This prevents notification from going off everytime new download
                                 // gets added to the queue.
                                 .setGroupAlertBehavior( NotificationCompat.GROUP_ALERT_SUMMARY )
                                 .setCategory( NotificationCompat.CATEGORY_PROGRESS )
                                 .addAction(
                                     app.kreate.resources.R.drawable.cancel_circle,
                                     context.getString( R.string.notification_button_cancel ),
                                     DownloadActionReceiver.pendingIntent(
                                         context,
                                         DownloadActionReceiver.ACTION_CANCEL,
                                         download.request.id,
                                         progressChildId( download.request.id )
                                     )
                                 )
                                 .build()
    }

    /**
     * Safety net used by [TerminalStateNotifier]: the periodic tick stops once the last
     * download ends, so the final cleanup has to happen from the listener.
     */
    fun cancelProgressChild( context: Context, requestId: String ) {
        val id = progressChildId( requestId )
        NotificationManagerCompat.from( context ).cancel( id )
        synchronized( shownProgressChildIds ) { shownProgressChildIds -= id }
    }
    //endregion

    // ---------------------------------------------------------------------------------
    // FINISHED group
    // ---------------------------------------------------------------------------------

    @SuppressLint("MissingPermission") // guarded by canPostNotifications()
    fun postFinishedChild(
        context: Context,
        download: Download
    ) {
        if ( !NotificationUtil.canPostNotification(context) ) return

        val nm = NotificationManagerCompat.from( context )
        val failed = download.state == Download.STATE_FAILED
        val childId = finishedChildId( download.request.id )
        val icon = if( failed ) android.R.drawable.stat_notify_error else android.R.drawable.stat_sys_download_done
        val contentText = context.getString(
            if( failed )
                R.string.notification_description_downloads_failure
            else
                R.string.notification_description_downloads_success
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_FINISHED)
            .setSmallIcon( icon )
            .setContentTitle( download.displayTitle() )
            .setGroup( GROUP_FINISHED )
            .setAutoCancel( true )
            .setOnlyAlertOnce( true )
            .setContentIntent( launchAppPendingIntent(context) )
            .setContentText( contentText )
        if( failed )
            builder.addAction(
                app.kreate.resources.R.drawable.replay,
                context.getString( R.string.notification_button_restart ),
                DownloadActionReceiver.retryPendingIntent( context, download.request, childId )
            )
        nm.notify( childId, builder.build() )

        // (Re)post the finished-group parent so children collapse under it.
        nm.notify(
            NotificationUtil.DOWNLOAD_FINISHED_SUMMARY_ID,
            NotificationCompat.Builder(context, CHANNEL_FINISHED)
                              .setSmallIcon( android.R.drawable.stat_sys_download_done )
                              .setContentTitle( context.getString(R.string.notification_title_downloads_complete)  )
                              .setGroup( GROUP_FINISHED )
                              .setGroupSummary( true )
                              .setAutoCancel( true )
                              .setOnlyAlertOnce( true )
                              .setContentIntent( launchAppPendingIntent(context) )
                              // This prevents notification from going off everytime new download
                              // gets added to the queue.
                              .setGroupAlertBehavior( NotificationCompat.GROUP_ALERT_SUMMARY )
                              .build()
        )
    }
}
