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
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.knighthat.kreate3.playback.SleepTimerCommands.RESTART
import me.knighthat.kreate3.playback.SleepTimerCommands.START
import me.knighthat.kreate3.playback.SleepTimerCommands.restartExtras
import me.knighthat.kreate3.playback.SleepTimerCommands.startArgs
import me.knighthat.kreate3.preferences.Preferences
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Action of the intents and session commands that change the sleep timer, see [SleepTimerCommands]. */
private const val ACTION_PREFIX = "me.knighthat.kreate3.SLEEP_TIMER_"

/** What a notification button sends to [PlaybackService] to do this to the sleep timer. */
internal val SleepTimerAction.intentAction: String
    get() = ACTION_PREFIX + name

/**
 * The session commands the app's controller sets the sleep timer with, see
 * [PlaybackController.startSleepTimer] and [PlaybackController.changeSleepTimer].
 */
internal object SleepTimerCommands {

    private const val KEY_MILLIS = "millis"
    private const val KEY_ENDED_SONG = "ended_song"

    /** Starts a timer, the arguments say how long, see [startArgs]. */
    val START = SessionCommand("${ACTION_PREFIX}START", Bundle.EMPTY)

    /**
     * Action of the Restart button of [SleepTimerNotification.finished]: plays again with a timer
     * as long as the one that ended, its extras say how long, see [startArgs].
     */
    const val RESTART = "${ACTION_PREFIX}RESTART"

    /** Every command of the timer, the app's controller is allowed them. */
    val all: List<SessionCommand>
        get() = listOf(START) + SleepTimerAction.entries.map(::of)

    fun of(action: SleepTimerAction) = SessionCommand(action.intentAction, Bundle.EMPTY)

    /**
     * Arguments of [START], or extras of [RESTART], for a timer of [duration], or until the song
     * ends when `null`.
     */
    fun startArgs(duration: Duration?): Bundle =
        Bundle().apply { duration?.let { putLong(KEY_MILLIS, it.inWholeMilliseconds) } }

    /** The duration [startArgs] put in [args], `null` for until the song ends. */
    fun durationOf(args: Bundle): Duration? =
        args.takeIf { it.containsKey(KEY_MILLIS) }?.getLong(KEY_MILLIS)?.milliseconds

    /**
     * Extras of [RESTART]: the timer's [duration], see [startArgs], and the media id of the song
     * it ended with, `null` when it ended in the middle of one.
     */
    fun restartExtras(duration: Duration?, endedSong: String?): Bundle =
        startArgs(duration).apply { endedSong?.let { putString(KEY_ENDED_SONG, it) } }

    /** The song [restartExtras] put in [extras]. */
    fun endedSongOf(extras: Bundle): String? = extras.getString(KEY_ENDED_SONG)

    /** What [action], of an intent or a command, does to the timer, `null` when it isn't one. */
    fun actionOf(action: String?): SleepTimerAction? =
        SleepTimerAction.entries.firstOrNull { it.intentAction == action }
}

/**
 * Keeps the sleep timer next to the player of a session, in the service, so it pauses playback
 * whatever shows on screen, or nothing. Its state goes in the session's extras for the
 * controllers, see [sleepTimer], and in [SleepTimerNotification] while it runs.
 *
 * A countdown waits on [scope], the player's thread. While music plays the player keeps the
 * device awake, so it ends on time. A timer that ends with the song uses
 * [ExoPlayer.setPauseAtEndOfMediaItems], the song plays to its very end.
 *
 * A timer that runs out pauses playback and leaves [SleepTimerNotification.finished] behind, to
 * play again with the same timer. Turning a timer off or dropping it leaves nothing.
 */
@OptIn(UnstableApi::class)
internal class SleepTimerRunner(
    private val player: ExoPlayer,
    private val session: MediaSession,
    private val notification: SleepTimerNotification,
    private val scope: CoroutineScope,
    private val startForeground: (Notification) -> Unit,
    private val stopForeground: () -> Unit,
) : Player.Listener {

    private var timer: SleepTimer? = null
    private var countdown: Job? = null
    private var refresh: Job? = null
    private var restarting: Job? = null

    /** How long the timer running was started for, `null` until the song ends. */
    private var startedFor: Duration? = null

    init {
        player.addListener(this)
        // Left by an app that was killed, its timer is gone. One that finished stays.
        notification.cancelRunning()
    }

    /** Starts a timer of [duration], or until the song ends when `null`, in place of the last. */
    fun start(duration: Duration?) {
        // The user's newer choice wins over a restart still waiting for the queue
        restarting?.cancel()
        begin(duration, duration?.let(SleepTimer::after) ?: SleepTimer.EndOfSong())
    }

    /**
     * Plays again with a timer of [duration], or until the song ends when `null`, after one ran
     * out. A service just made puts the saved queue back first, it is waited for a moment.
     *
     * @param endedSong media id of the song the timer ended with: when it is still the one
     * waiting, the next song plays, or it again from its start when it was the last
     */
    fun restart(duration: Duration?, endedSong: String?) {
        restarting?.cancel()
        restarting = scope.launch {
            withTimeoutOrNull(QUEUE_WAIT) {
                while (player.mediaItemCount == 0) delay(QUEUE_CHECK)
            }
            // Nothing left to play, the notification has nothing to do anymore
            if (player.mediaItemCount == 0) return@launch notification.cancel()

            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            // Played from where it ended, it would end the new timer at once
            val ended = player.playbackState == Player.STATE_ENDED ||
                    !player.isPlaying && player.currentMediaItem?.mediaId == endedSong
            if (ended)
                if (player.hasNextMediaItem()) player.seekToNextMediaItem()
                else player.seekToDefaultPosition()

            val timer = duration?.let(SleepTimer::after) ?: SleepTimer.EndOfSong()
            // Android 16 gives audio focus to an app in the background only while its service
            // is in the foreground, which the media notification makes it only once playing
            startForeground(notification.build(timer, removeStep(), addStep()))
            try {
                player.play()
                begin(duration, timer)
                // The media notification takes the foreground over as music plays
                withTimeoutOrNull(HANDOVER_WAIT) {
                    while (notification.isForeground) delay(QUEUE_CHECK)
                }
            } finally {
                // Nothing played, the service mustn't stay in the foreground for nothing
                if (notification.isForeground) stopForeground()
            }
        }
    }

    /** Does [action] to the timer running, when it can. */
    fun change(action: SleepTimerAction) {
        val timer = timer ?: return notification.cancel()
        set(
            when (action) {
                SleepTimerAction.TOGGLE_PAUSE -> timer.togglePaused()
                SleepTimerAction.ADD ->
                    timer.changedBy(Preferences.SLEEP_TIMER_ADD_STEP.value, add = true) ?: timer

                SleepTimerAction.REMOVE ->
                    timer.changedBy(Preferences.SLEEP_TIMER_REMOVE_STEP.value, add = false) ?: timer

                SleepTimerAction.STOP -> null
            }
        )
    }

    /** Before the player is released. */
    fun release() {
        player.removeListener(this)
        countdown?.cancel()
        refresh?.cancel()
        restarting?.cancel()
        player.pauseAtEndOfMediaItems = false
        // The one of a timer that finished stays, its Restart button starts the service again
        notification.cancelRunning()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        val timer = timer as? SleepTimer.Running ?: return
        // It ended while nothing played, like while the device slept: what starts now plays on
        if (isPlaying && timer.remaining == 0L) set(null)
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
            timer is SleepTimer.EndOfSong
        ) finish(endedSong = player.currentMediaItem?.mediaId)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        // The queue played to its end with the song, playback stopped as the timer would have
        if (playbackState == Player.STATE_ENDED && timer is SleepTimer.EndOfSong)
            finish(endedSong = player.currentMediaItem?.mediaId)
    }

    override fun onTimelineChanged(timeline: Timeline, reason: Int) {
        // Playback stopped for good
        if (timeline.isEmpty && timer != null) set(null)
    }

    /**
     * Makes [timer] the one running, `null` turns it off. Every timer set shows the notification,
     * also after the user swiped it away.
     */
    private fun set(timer: SleepTimer?) {
        countdown?.cancel()
        refresh?.cancel()
        this.timer = timer
        player.pauseAtEndOfMediaItems = timer is SleepTimer.EndOfSong && !timer.isPaused
        session.sessionExtras = Bundle(session.sessionExtras).apply { putSleepTimer(timer) }
        if (timer == null) return notification.cancel()

        show(timer)
        if (timer !is SleepTimer.Running) return
        countdown = scope.launch {
            delay(timer.remaining)
            player.pause()
            finish(endedSong = null)
        }
        // Its remove button turns off once it would take all the time left
        val removable = Preferences.SLEEP_TIMER_REMOVE_STEP.value.minutes
            ?.minutes?.inWholeMilliseconds ?: return
        if (timer.remaining > removable)
            refresh = scope.launch {
                delay(timer.remaining - removable)
                // Not shown again once swiped away, only a change of the user's does that
                if (notification.isShowing) show(timer)
            }
    }

    /** Runs [timer], started for [duration]. */
    private fun begin(duration: Duration?, timer: SleepTimer) {
        startedFor = duration
        set(timer)
    }

    /**
     * The timer ran out, playback is paused: says so, with a button to play again.
     *
     * @param endedSong media id of the song the timer ended with, `null` in the middle of one
     */
    private fun finish(endedSong: String?) {
        val restartWith = startedFor
        set(null)
        notification.finished(System.currentTimeMillis(), restartWith, endedSong)
    }

    private fun show(timer: SleepTimer) = notification.show(timer, removeStep(), addStep())

    private fun removeStep() = Preferences.SLEEP_TIMER_REMOVE_STEP.value

    private fun addStep() = Preferences.SLEEP_TIMER_ADD_STEP.value

    private companion object {

        /** How long [restart] waits for the saved queue, it is usually back at once. */
        val QUEUE_WAIT = 5.seconds

        /** How often [restart] looks whether the queue is back, or the foreground taken over. */
        val QUEUE_CHECK = 100.milliseconds

        /** How long the media notification has to take the foreground over after [restart]. */
        val HANDOVER_WAIT = 5.seconds
    }
}
