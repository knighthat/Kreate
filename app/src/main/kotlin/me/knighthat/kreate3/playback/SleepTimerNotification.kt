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
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.IdRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import me.knighthat.kreate3.R
import me.knighthat.kreate3.constant.SleepTimerStep
import me.knighthat.kreate3.notification.AppChannel
import me.knighthat.kreate3.notification.notificationBuilder
import me.knighthat.kreate3.notification.notify
import me.knighthat.kreate3.playback.SleepTimerNotification.Companion.FOREGROUND_ID
import me.knighthat.kreate3.playback.SleepTimerNotification.Companion.ID
import java.util.Date
import kotlin.time.Duration

/** Opacity of a button of the notification that can't be used, like Material's disabled content. */
private const val DISABLED_ALPHA = 0.38f

/**
 * The sleep timer's notification, while it runs: the time left, counted down by the system, and
 * buttons to take time off, put it on hold or let it go on, add time and stop it. The buttons go
 * to [PlaybackService], see [SleepTimerAction.intentAction].
 *
 * It can't be swiped away before Android 14. From then on it can, the timer goes on without it
 * and shows it again at its next change.
 *
 * Once the timer runs out it becomes [finished]. Neither ever makes a sound, vibrates or pops up,
 * the channel is silent: the user may be asleep.
 */
internal class SleepTimerNotification(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    /** Whether it shows, the user may have swiped it away. */
    val isShowing: Boolean
        get() = active(ID) != null

    /**
     * Whether the service is still in the foreground with the timer's notification, under
     * [FOREGROUND_ID]. The system takes it away once the media notification takes over.
     */
    val isForeground: Boolean
        get() = active(FOREGROUND_ID) != null

    /**
     * Shows [timer] in place of what showed. The remove and add buttons change it by [removeStep]
     * and [addStep], each is off while it can't.
     */
    fun show(timer: SleepTimer, removeStep: SleepTimerStep, addStep: SleepTimerStep) =
        context.notify(ID, build(timer, removeStep, addStep))

    /** What [show] shows, for the service to go in the foreground with. */
    fun build(
        timer: SleepTimer,
        removeStep: SleepTimerStep,
        addStep: SleepTimerStep
    ): Notification {
        val views = RemoteViews(context.packageName, R.layout.sleep_timer_notification).apply {
            val left = timer.remaining
            setViewVisibility(R.id.sleep_timer_left, if (left != null) View.VISIBLE else View.GONE)
            setViewVisibility(
                R.id.sleep_timer_song_end,
                if (left == null) View.VISIBLE else View.GONE
            )
            if (left != null) {
                // Counted down from the time left, standing still while on hold
                setChronometer(
                    R.id.sleep_timer_left,
                    SystemClock.elapsedRealtime() + left,
                    null,
                    !timer.isPaused,
                )
                setChronometerCountDown(R.id.sleep_timer_left, true)
            } else
                setTextViewText(
                    R.id.sleep_timer_song_end,
                    context.getString(
                        if (timer.isPaused) R.string.notification_sleep_timer_subtitle_on_hold
                        else R.string.notification_sleep_timer_subtitle_until_song_ends
                    ),
                )

            setTextViewText(R.id.sleep_timer_remove, removeStep.buttonLabel(add = false))
            setContentDescription(
                R.id.sleep_timer_remove,
                context.getString(R.string.semantic_remove_time)
            )
            button(
                R.id.sleep_timer_remove,
                SleepTimerAction.REMOVE,
                enabled = timer.changedBy(removeStep, add = false) != null,
            )

            setImageViewResource(
                R.id.sleep_timer_toggle,
                if (timer.isPaused) R.drawable.play_arrow else R.drawable.pause,
            )
            setContentDescription(
                R.id.sleep_timer_toggle,
                context.getString(
                    if (timer.isPaused) R.string.semantic_resume_the_sleep_timer
                    else R.string.semantic_pause_the_sleep_timer
                ),
            )
            button(R.id.sleep_timer_toggle, SleepTimerAction.TOGGLE_PAUSE)

            setTextViewText(R.id.sleep_timer_add, addStep.buttonLabel(add = true))
            setContentDescription(
                R.id.sleep_timer_add,
                context.getString(R.string.semantic_add_time)
            )
            button(
                R.id.sleep_timer_add,
                SleepTimerAction.ADD,
                enabled = timer.changedBy(addStep, add = true) != null,
            )

            setContentDescription(
                R.id.sleep_timer_stop,
                context.getString(R.string.semantic_stop_the_sleep_timer)
            )
            button(R.id.sleep_timer_stop, SleepTimerAction.STOP)
        }

        return context.notificationBuilder(AppChannel.SLEEP_TIMER)
            .setSmallIcon(R.drawable.bedtime)
            // Read out and shown where the custom view isn't
            .setContentTitle(context.getString(R.string.notification_sleep_timer_title_running))
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(views)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .build()
    }

    /**
     * Says the timer ran out [at], milliseconds of the wall clock, and paused playback. It can be
     * swiped away, and its Restart button plays again with a timer of [restartWith], or until the
     * song ends when `null`, see [SleepTimerCommands.RESTART].
     *
     * @param endedSong media id of the song the timer ended with, `null` in the middle of one
     */
    fun finished(at: Long, restartWith: Duration?, endedSong: String?) {
        val restart = PendingIntent.getService(
            context,
            RESTART_REQUEST,
            Intent(context, PlaybackService::class.java)
                .setAction(SleepTimerCommands.RESTART)
                .putExtras(SleepTimerCommands.restartExtras(restartWith, endedSong)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = context.notificationBuilder(AppChannel.SLEEP_TIMER)
            .setSmallIcon(R.drawable.bedtime)
            .setContentTitle(context.getString(R.string.notification_sleep_timer_title_finished))
            // In the user's 12 or 24 hour clock
            .setContentText(
                context.getString(
                    R.string.notification_sleep_timer_subtitle_paused_at,
                    DateFormat.getTimeFormat(context).format(Date(at)),
                )
            )
            .setWhen(at)
            .setShowWhen(true)
            .addAction(R.drawable.play_arrow, context.getString(R.string.action_restart), restart)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        context.notify(ID, notification)
    }

    fun cancel() = manager.cancel(ID)

    /** Takes away the notification of a running timer, one that [finished] stays. */
    fun cancelRunning() {
        val shown = active(ID) ?: return
        if (shown.notification.flags and Notification.FLAG_ONGOING_EVENT != 0) cancel()
    }

    private fun active(id: Int) = context.getSystemService(NotificationManager::class.java)
        ?.activeNotifications.orEmpty()
        .firstOrNull { it.id == id }

    /** Lets the button [id] do [action] while [enabled], dims it otherwise. */
    private fun RemoteViews.button(
        @IdRes id: Int,
        action: SleepTimerAction,
        enabled: Boolean = true,
    ) {
        setBoolean(id, "setEnabled", enabled)
        setFloat(id, "setAlpha", if (enabled) 1f else DISABLED_ALPHA)
        if (enabled) setOnClickPendingIntent(id, intentOf(action))
    }

    private fun intentOf(action: SleepTimerAction): PendingIntent = PendingIntent.getService(
        context,
        action.ordinal,
        Intent(context, PlaybackService::class.java).setAction(action.intentAction),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {

        // Clear of Media3's media notification, 1001, the messages' 2000s and the scans' 3000s
        const val ID = 4000

        /**
         * The service goes in the foreground with a copy of the timer's notification under this
         * id, not [ID]: the system takes it away as the media notification takes over, the
         * timer's own must stay.
         */
        const val FOREGROUND_ID = 4001

        // Clear of the buttons' requests, the ordinals of SleepTimerAction
        private const val RESTART_REQUEST = 100
    }
}
