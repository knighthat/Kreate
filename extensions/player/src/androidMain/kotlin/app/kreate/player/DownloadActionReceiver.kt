package app.kreate.player

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.IntentCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import co.touchlab.kermit.Logger


@OptIn(UnstableApi::class)
internal class DownloadActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_RETRY = "app.kreate.player.downloads.action.RETRY"
        const val ACTION_CANCEL = "app.kreate.player.downloads.action.CANCEL"

        private const val EXTRA_REQUEST = "extra_download_request"
        private const val EXTRA_ID = "extra_download_id"
        private const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

        fun pendingIntent(
            context: Context,
            action: String,
            downloadId: String,
            notificationId: Int,
        ): PendingIntent = PendingIntent.getBroadcast(
            context,
            // Request code must be unique per (item, action) — otherwise FLAG_UPDATE_CURRENT
            // makes Pause and Cancel on the same row overwrite each other.
            notificationId * 31 + action.hashCode(),
            Intent(context, DownloadActionReceiver::class.java)
                .setAction( action )
                .putExtra( EXTRA_ID, downloadId )
                .putExtra( EXTRA_NOTIFICATION_ID, notificationId ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /** Retry additionally carries the Parcelable request, so it survives process death. */
        fun retryPendingIntent(
            context: Context,
            request: DownloadRequest,
            notificationId: Int,
        ): PendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId * 31 + ACTION_RETRY.hashCode(),
            Intent(context, DownloadActionReceiver::class.java)
                .setAction( ACTION_RETRY )
                .putExtra( EXTRA_REQUEST, request )
                .putExtra( EXTRA_ID, request.id )
                .putExtra( EXTRA_NOTIFICATION_ID, notificationId ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    override fun onReceive( context: Context, intent: Intent ) {
        val id = intent.getStringExtra( EXTRA_ID ) ?: return
        val notificationId = intent.getIntExtra( EXTRA_NOTIFICATION_ID, -1 )
        val action = intent.action
        Logger.v( tag = "DownloadActionReceiver" ) { "Received action $action for id $id with notification $notificationId" }

        when( intent.action ) {
            ACTION_CANCEL -> {
                // Dismiss immediately: removal is async, and a lingering row after a tap
                // feels broken.
                if( notificationId != -1 ) {
                    NotificationManagerCompat.from( context ).cancel( notificationId )
                }
                DownloadService.sendRemoveDownload(
                    context,
                    MediaDownloadService::class.java,
                    id,
                    /* foreground = */ true
                )
            }

            ACTION_RETRY -> {
                if( notificationId != -1 ) {
                    NotificationManagerCompat.from( context ).cancel( notificationId )
                }
                val request = IntentCompat.getParcelableExtra( intent, EXTRA_REQUEST, DownloadRequest::class.java ) ?: return
                DownloadService.sendAddDownload(
                    context,
                    MediaDownloadService::class.java,
                    request,
                    /* foreground = */ true
                )
            }
        }

        Logger.d( tag = "DownloadActionReceiver" ) { "Action processed" }
    }
}