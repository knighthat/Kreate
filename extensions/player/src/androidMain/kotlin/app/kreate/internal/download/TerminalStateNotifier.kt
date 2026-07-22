package app.kreate.internal.download

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import co.touchlab.kermit.Logger


/**
 * Because the DownloadManager outlives the service, this keeps firing even while the
 * service is winding down — which is exactly when the terminal notifications need to be
 * posted and the last progress children need to be cleaned up.
 */
@OptIn(UnstableApi::class)
class TerminalStateNotifier(
    private val context: Context
) : DownloadManager.Listener {

    override fun onDownloadChanged(
        downloadManager: DownloadManager,
        download: Download,
        finalException: Exception?,
    ) {
        // Once an item leaves the in-progress states, drop its progress child. The service's
        // periodic tick normally does this, but the tick stops when the last item finishes.
        when( download.state ) {
            Download.STATE_DOWNLOADING,
            Download.STATE_QUEUED,
            Download.STATE_RESTARTING -> Unit
            else -> DownloadNotifications.cancelProgressChild( context, download.request.id )
        }

        when( download.state ) {
            Download.STATE_COMPLETED,
            Download.STATE_FAILED -> DownloadNotifications.postFinishedChild( context, download )

            else -> Unit
        }

        if( finalException != null )
            Logger.e( "Failed to download ${download.request.id}", finalException, "TerminalStateNotifier" )
    }

    override fun onDownloadRemoved( downloadManager: DownloadManager, download: Download ) {
        DownloadNotifications.cancelProgressChild( context, download.request.id )
    }
}
