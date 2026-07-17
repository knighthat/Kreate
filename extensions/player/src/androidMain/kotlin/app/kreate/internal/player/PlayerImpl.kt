package app.kreate.internal.player

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import app.kreate.player.Player


@OptIn(UnstableApi::class)
internal class PlayerImpl(actualPlayer: ExoPlayer) : Player, ExoPlayer by actualPlayer {

    override fun getAudioSessionId(): Int = super.getAudioSessionId()
}