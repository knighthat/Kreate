package app.kreate.android.service.player

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.MainThread
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import app.kreate.android.Preferences
import it.fast4x.rimusic.service.modern.PlayerServiceModern

class VolumeObserver constructor(
    private val context: Context,
    private val player: ExoPlayer,
    private val volumeFader: VolumeFader
): ContentObserver(Handler(Looper.getMainLooper()))  {

    private val audioManager =
        context.getSystemService( Context.AUDIO_SERVICE ) as AudioManager

    private var pausedByZeroVolume = false

    /**
     * Pause with fade out effect
     *
     * Copied from [PlayerServiceModern.Binder.gracefulPause]
     */
    @MainThread
    private fun gracefulPause() = with( player ) {
        if( !isPlaying ) return

        val duration = Preferences.AUDIO_FADE_DURATION.value.asMillis
        if( duration == 0L ) {
            pause()
            return
        }

        val originalVolume = volume
        volumeFader.startFade(
            start = volume,
            end = 0f,
            durationInMillis = duration,
            doOnEnd = {
                pause()
                volume = originalVolume
            }
        )
    }

    /**
     * Start playing with fade in effect
     *
     * Copied from [PlayerServiceModern.Binder.gracefulPlay]
     */
    @MainThread
    private fun gracefulPlay() = with( player ) {
        if( isPlaying ) return

        val duration = Preferences.AUDIO_FADE_DURATION.value.asMillis
        if( duration == 0L ) {
            if( playbackState == Player.STATE_IDLE )
                prepare()
            play()
            return
        }

        volumeFader.startFade(
            start = 0f,
            end = volume,
            durationInMillis = duration,
            doOnStart = {
                volume = 0f
                if ( playbackState == Player.STATE_IDLE )
                    prepare()
                play()
            }
        )
    }

    fun register() =
        context.contentResolver
               .registerContentObserver( Settings.System.CONTENT_URI, true, this )

    fun unregister() =
        context.contentResolver
               .unregisterContentObserver( this )

    override fun onChange( selfChange: Boolean ) {
        if( !Preferences.PAUSE_WHEN_VOLUME_SET_TO_ZERO.value ) return

        val volume = audioManager.getStreamVolume( AudioManager.STREAM_MUSIC )

        if ( player.isPlaying && volume < 1 ) {
            gracefulPause()
            pausedByZeroVolume = true
        } else if ( pausedByZeroVolume && volume >= 1 ) {
            gracefulPlay()
            pausedByZeroVolume = false
        }
    }
}