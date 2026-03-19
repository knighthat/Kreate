package app.kreate.android.service.playback

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import androidx.compose.runtime.getValue
import androidx.core.content.getSystemService
import androidx.media3.common.Player
import app.kreate.android.Preferences
import co.touchlab.kermit.Logger


class AudioHandler(
    context: Context,
    private val handler: Handler,
    private val player: Player
) : SharedPreferences.OnSharedPreferenceChangeListener {

    private val manager = context.getSystemService<AudioManager>()
    private val callback = Callback()

    init {
        val isEnabled by Preferences.RESUME_PLAYBACK_WHEN_CONNECT_TO_AUDIO_DEVICE
        if( isEnabled )
            register()
    }

    fun register() {
        manager?.registerAudioDeviceCallback( callback, handler )

        Logger.d( tag = this::class.java.simpleName ) { "AudioHandler registered" }
    }

    fun unregister() {
        manager?.unregisterAudioDeviceCallback( callback )

        Logger.d( tag = this::class.java.simpleName ) { "AudioHandler unregistered" }
    }

    override fun onSharedPreferenceChanged( pref: SharedPreferences, key: String? ) {
        when( key ) {
            Preferences.Key.RESUME_PLAYBACK_WHEN_CONNECT_TO_AUDIO_DEVICE -> {
                val isEnabled = pref.getBoolean(key, false)
                if( isEnabled )
                    register()
                else
                    unregister()
            }
        }
    }

    private inner class Callback : AudioDeviceCallback() {
        private fun canPlayMusic(audioDeviceInfo: AudioDeviceInfo): Boolean {
            if ( !audioDeviceInfo.isSink ) return false

            return when( audioDeviceInfo.type ) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET        -> true
                else                                    -> false
            }
        }

        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
            if( player.isPlaying ) return

            if( addedDevices.any(::canPlayMusic) )
                player.play()
        }
    }
}