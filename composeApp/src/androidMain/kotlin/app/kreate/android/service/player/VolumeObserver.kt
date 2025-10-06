package app.kreate.android.service.player

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.media3.exoplayer.ExoPlayer
import app.kreate.android.Preferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class VolumeObserver @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val player: ExoPlayer
): ContentObserver(Handler(Looper.getMainLooper()))  {

    private val audioManager =
        context.getSystemService( Context.AUDIO_SERVICE ) as AudioManager

    private var pausedByZeroVolume = false

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
            player.pause()
            pausedByZeroVolume = true
        } else if ( pausedByZeroVolume && volume >= 1 ) {
            player.play()
            pausedByZeroVolume = false
        }
    }
}