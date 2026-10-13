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

package me.knighthat.kreate.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import me.knighthat.kreate.KreateActivity
import me.knighthat.kreate.R
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Plays music in the background and shows the playback notification.
 *
 * Screens don't touch the player, they connect to [mediaSession] with a `MediaController`.
 * The service owns the player: it is created with the session and released with it.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService(), KoinComponent {

    private var mediaSession: MediaSession? = null
    private var normalizer: LoudnessNormalizer? = null

    // The player is used on the main thread, so is everything that follows it
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                // The icon in the status bar and at the top of the notification
                setSmallIcon(R.drawable.ic_splash)
            }
        )

        val player = get<KreatePlayer>()
        normalizer = LoudnessNormalizer(player, get(), scope)

        mediaSession = MediaSession.Builder(this, player)
            // Tapping the notification brings the app back instead of opening a second copy
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, KreateActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    ),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    /**
     * Swiping the app away stops the service unless it is playing, so music goes on and a paused
     * player doesn't leave a notification behind.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0)
            stopSelf()
    }

    override fun onDestroy() {
        normalizer?.release()
        normalizer = null
        scope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
