package app.kreate.android.service.player

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaLibraryInfo
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.kreate.widgets.WidgetBroadcastReceiver
import org.koin.core.component.KoinComponent
import org.koin.core.component.get


class WidgetListener : Player.Listener, KoinComponent {

    private fun sendSyncBroadcast( context: Context = get(), builder: Intent.() -> Intent ) {
        Intent(context, WidgetBroadcastReceiver::class.java)
            .setAction( WidgetBroadcastReceiver.ACTION_SYNC )
            .run( builder )
            .also( context::sendBroadcast )
    }

    @OptIn(UnstableApi::class)
    override fun onMediaItemTransition( mediaItem: MediaItem?, reason: Int ) {
        sendSyncBroadcast {
            val state = mediaItem?.mediaMetadata?.toBundle(MediaLibraryInfo.INTERFACE_VERSION)
            putExtra( WidgetBroadcastReceiver.EXTRA_SONG_STATE, state )
        }
    }

    override fun onIsPlayingChanged( isPlaying: Boolean ) {
        sendSyncBroadcast {
            putExtra( WidgetBroadcastReceiver.EXTRA_IS_PLAYING, isPlaying )
        }
    }
}