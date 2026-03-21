package app.kreate.android.service.player

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
@UnstableApi
class ExoPlayerListener(
    private val player: StatefulPlayer,
    private val waitingForNetwork: MutableStateFlow<Boolean>,
    private val sendOpenEqualizerIntent: () -> Unit,
    private val sendCloseEqualizerIntent: () -> Unit,
): Player.Listener, KoinComponent {

    private val context: Context by inject()

    private var volumeNormalizationJob: Job = Job()


    var loudnessEnhancer: LoudnessEnhancer? = null
        private set




    override fun onEvents(player: Player, events: Player.Events) {
        if (
            events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED
            )
        ) {
            val isBufferingOrReady =
                player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY
            if (isBufferingOrReady && player.playWhenReady) {
                sendOpenEqualizerIntent()
            } else {
                sendCloseEqualizerIntent()
                if (!player.playWhenReady) {
                    waitingForNetwork.value = false
                }
            }
        }
    }
}