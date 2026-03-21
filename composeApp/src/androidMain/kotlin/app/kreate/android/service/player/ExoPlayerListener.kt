package app.kreate.android.service.player

import android.content.Context
import android.media.audiofx.LoudnessEnhancer
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import app.kreate.android.Preferences
import app.kreate.android.R
import it.fast4x.rimusic.service.LoginRequiredException
import it.fast4x.rimusic.service.MissingDecipherKeyException
import it.fast4x.rimusic.service.NoInternetException
import it.fast4x.rimusic.service.PlayableFormatNotFoundException
import it.fast4x.rimusic.service.UnknownException
import it.fast4x.rimusic.service.UnplayableException
import it.fast4x.rimusic.utils.playNext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import me.knighthat.utils.Toaster
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.seconds

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
    private var errorTimestamp = 0L
    private var lastErrorMessage = ""

    var loudnessEnhancer: LoudnessEnhancer? = null
        private set

    @MainThread
    private fun traverseErrorStack( t: Throwable ): Throwable =
        when( t ) {
            is PlayableFormatNotFoundException,
            is UnplayableException,
            is LoginRequiredException,
            is NoInternetException,
            is UnknownException,
            is MissingDecipherKeyException -> t

            else -> t.cause?.let( ::traverseErrorStack ) ?: t
        }

    @MainThread
    private fun printErrorMessage( errMsg: String )  {
        // If the same error is set within 10s, it'll be ignored.
        val timeWindow = errorTimestamp + 10.seconds.inWholeMilliseconds

        if( errMsg == lastErrorMessage
            && System.currentTimeMillis() <= timeWindow
        ) return

        lastErrorMessage = errMsg
        // When field is successfully set, update timestamp.
        errorTimestamp = System.currentTimeMillis()
        // Finally, print the error if not blank
        if( errMsg.isNotBlank() )
            Toaster.e( errMsg, Toast.LENGTH_LONG )
    }

    override fun onPlayerError( error: PlaybackException ) {
        val rootCause = traverseErrorStack( error )

        when( rootCause ) {
            is PlayableFormatNotFoundException -> context.getString( R.string.error_couldn_t_find_a_playable_audio_format )
            is NoInternetException -> context.getString( R.string.no_connection )
            is MissingDecipherKeyException -> context.getString( R.string.error_failed_to_decipher_signature )

            else -> rootCause.message ?: context.getString( R.string.error_unknown )
        }.also( ::printErrorMessage )

        // TODO: Add additional recovery step if type of error allows it

        if ( Preferences.PLAYBACK_SKIP_ON_ERROR.value && player.hasNextMediaItem() )
            player.playNext()
    }

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