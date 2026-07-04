package app.kreate.android.service.player

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaLibraryInfo
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.kreate.widgets.WidgetBroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.time.Duration.Companion.seconds


class WidgetListener : Player.Listener, KoinComponent {

    private var updateProgressJob: Job? = null

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

        if( isPlaying ) {
            updateProgressJob = get<CoroutineScope>().launch {
                while( true ) {
                    val (position, duration) = withContext( Dispatchers.Main ) {
                        with( get<Player>() ) { currentPosition to duration }
                    }

                    val context: Context = get()
                    Intent(context, WidgetBroadcastReceiver::class.java)
                        .setAction( WidgetBroadcastReceiver.ACTION_UPDATE_PROGRESS )
                        .putExtra( WidgetBroadcastReceiver.EXTRA_PROGRESS, position )
                        .putExtra( WidgetBroadcastReceiver.EXTRA_DURATION, duration )
                        .also( context::sendBroadcast )

                    delay( 1.seconds )
                }
            }
        } else {
            updateProgressJob?.cancel()
            updateProgressJob = null
        }
    }
}