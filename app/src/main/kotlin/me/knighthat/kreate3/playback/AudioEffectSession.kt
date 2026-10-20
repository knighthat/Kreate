/*
 * Kreate
 * Copyright (C) 2026 KnightHat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.knighthat.kreate3.playback

import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession

/** Key of the player's audio session id in the session's extras, see [AudioEffectSession]. */
internal const val EXTRA_AUDIO_SESSION_ID = "audio_session_id"

/**
 * Lets equalizers work on the player's sound. Equalizer apps are told about its audio session,
 * so they can put their effects on it, and [session] keeps its id in its extras, for the app's
 * equalizer button, see [PlaybackController.audioSessionId]. Both follow the player's session.
 */
@OptIn(UnstableApi::class)
internal class AudioEffectSession(
    private val context: Context,
    private val player: ExoPlayer,
    private val session: MediaSession,
) : Player.Listener {

    private var opened = C.AUDIO_SESSION_ID_UNSET

    init {
        player.addListener(this)
        open(player.audioSessionId)
    }

    override fun onAudioSessionIdChanged(audioSessionId: Int) = open(audioSessionId)

    fun release() {
        player.removeListener(this)
        close()
    }

    private fun open(audioSessionId: Int) {
        close()
        // The sleep timer keeps its state there too
        session.sessionExtras =
            Bundle(session.sessionExtras).apply { putInt(EXTRA_AUDIO_SESSION_ID, audioSessionId) }
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return

        broadcast(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION, audioSessionId)
        opened = audioSessionId
    }

    private fun close() {
        if (opened == C.AUDIO_SESSION_ID_UNSET) return
        broadcast(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION, opened)
        opened = C.AUDIO_SESSION_ID_UNSET
    }

    private fun broadcast(action: String, audioSessionId: Int) = context.sendBroadcast(
        Intent(action)
            .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
            .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
            .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
    )
}
