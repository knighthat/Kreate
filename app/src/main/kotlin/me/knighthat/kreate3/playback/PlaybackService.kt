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

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.app.ServiceCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import me.knighthat.kreate3.KreateActivity
import me.knighthat.kreate3.R
import me.knighthat.kreate3.preferences.Preferences
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Plays music in the background and shows the playback notification.
 *
 * Screens don't touch the player, they connect to [mediaSession] with a `MediaController`.
 * The service owns the player: it is created with the session and released with it.
 *
 * [PlaybackLifecycle] can release the session before the service ends, see [stop]. A controller
 * that connects afterwards gets a new one, unless the lifecycle holds playback stopped.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService(), KoinComponent {

    private val playbackLifecycle: PlaybackLifecycle = get()

    private var mediaSession: MediaSession? = null
    private var normalizer: LoudnessNormalizer? = null
    private var persistentQueue: PersistentQueue? = null
    private var audioEffects: AudioEffectSession? = null
    private var sleepTimer: SleepTimerRunner? = null

    // The player is used on the main thread, so is everything that follows it. One per session.
    private var scope: CoroutineScope? = null

    // Parent of the writes of a session, they come after its player is released, so it is a child
    // of the global scope. Once the session is released, it ends when the last write is done.
    private var writes: CompletableJob? = null

    override fun onCreate() {
        super.onCreate()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                // The icon in the status bar and at the top of the notification
                setSmallIcon(R.drawable.ic_monochrome)
            }
        )

        playbackLifecycle.attach(this)
        if (!playbackLifecycle.isStopped) createSession()
    }

    private fun createSession(): MediaSession {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val global = get<CoroutineScope>()
        val writes = SupervisorJob(global.coroutineContext[Job])
        val writeScope = CoroutineScope(global.coroutineContext + writes)
        this.scope = scope
        this.writes = writes

        val player = get<KreatePlayer>()
        // How much of the next song loads ahead follows the setting
        scope.launch {
            Preferences.PLAYBACK_PRELOAD_SECONDS.state.collect {
                player.preloadConfiguration = preloadConfiguration(it)
            }
        }
        normalizer = LoudnessNormalizer(player, get(), get(), scope)
        PlaybackHistoryRecorder(player, get(), writeScope)
        persistentQueue = PersistentQueue(player, get(), get(), scope, writeScope)
        PlayedSongRecorder(
            player = player,
            library = get(),
            catalog = get(),
            formats = get(),
            youTube = get(),
            openSubsonic = get(),
            scope = writeScope,
            playerScope = scope,
            onStored = { persistentQueue?.refresh() },
        )

        return MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
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
            .also {
                mediaSession = it
                audioEffects = AudioEffectSession(this, player, it)
                sleepTimer = SleepTimerRunner(
                    player = player,
                    session = it,
                    notification = SleepTimerNotification(this),
                    scope = scope,
                    startForeground = ::startForegroundWith,
                    stopForeground = {
                        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    },
                )
            }
    }

    /**
     * Releases the player and its session, the service ends once nothing is bound to it.
     *
     * @return the writes of the session, the job completes when the last one is done; `null` when
     * there was no session
     */
    internal fun stop(): Job? {
        val writes = releaseSession()
        stopSelf()
        return writes
    }

    private fun releaseSession(): Job? {
        val session = mediaSession ?: return null
        mediaSession = null

        normalizer?.release()
        normalizer = null
        audioEffects?.release()
        audioEffects = null
        sleepTimer?.release()
        sleepTimer = null
        // Before the player is released, it saves where the song is
        persistentQueue?.release()
        persistentQueue = null
        scope?.cancel()
        scope = null
        session.player.release()
        session.release()

        // No more writes are started, the job ends with the last one
        return writes?.also { it.complete() }.also { writes = null }
    }

    /**
     * Gives the items added to the queue the address of their song, see [withSongUri]. Items that
     * don't say where they come from can't be played, adding them fails.
     *
     * The app's own controllers may also set the sleep timer, see [SleepTimerCommands].
     */
    private inner class SessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            if (controller.packageName != packageName) return super.onConnect(session, controller)

            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .apply { SleepTimerCommands.all.forEach(::add) }
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val timer = sleepTimer
            val action = SleepTimerCommands.actionOf(customCommand.customAction)
            when {
                timer == null -> Unit
                customCommand == SleepTimerCommands.START ->
                    timer.start(SleepTimerCommands.durationOf(args))

                action != null -> timer.change(action)
                else -> return super.onCustomCommand(session, controller, customCommand, args)
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            val queued = mediaItems.map {
                it.withSongUri() ?: return Futures.immediateFailedFuture(
                    IllegalArgumentException("${it.mediaId} has no origin")
                )
            }
            return Futures.immediateFuture(queued)
        }
    }

    /**
     * The sleep timer's notification buttons come here, see [SleepTimerNotification], also the
     * Restart of a timer that finished, which may start the service again.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == SleepTimerCommands.RESTART) {
            val extras = intent.extras ?: Bundle.EMPTY
            sleepTimer?.restart(
                duration = SleepTimerCommands.durationOf(extras),
                endedSong = SleepTimerCommands.endedSongOf(extras),
            ) ?: SleepTimerNotification(this).cancel()
            return START_NOT_STICKY
        }
        val action = SleepTimerCommands.actionOf(intent?.action)
            ?: return super.onStartCommand(intent, flags, startId)
        // Without a session, there is no timer either, its notification was left behind
        sleepTimer?.change(action) ?: SleepTimerNotification(this).cancel()
        return START_NOT_STICKY
    }

    /**
     * Puts the service in the foreground with a copy of the sleep timer's [notification], until
     * the media notification takes over once music plays, see
     * [SleepTimerNotification.FOREGROUND_ID]. Allowed for a while after a notification's button
     * was tapped; when it isn't, playing is tried all the same.
     */
    private fun startForegroundWith(notification: Notification) {
        try {
            ServiceCompat.startForeground(
                this,
                SleepTimerNotification.FOREGROUND_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                else 0,
            )
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException, from Android 12 on
            Log.w(TAG, "Couldn't go in the foreground for the sleep timer", e)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession ?: if (playbackLifecycle.isStopped) null else createSession()

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
        releaseSession()
        playbackLifecycle.detach(this)
        super.onDestroy()
    }

    private companion object {

        const val TAG = "PlaybackService"
    }
}
