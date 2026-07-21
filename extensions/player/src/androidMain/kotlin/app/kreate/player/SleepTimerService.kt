package app.kreate.player

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import androidx.media3.exoplayer.ExoPlayer
import app.kreate.player.timer.SleepTimer
import app.kreate.player.timer.TimerState
import app.kreate.utils.IS_ANDROID_8_OR_LATER
import app.kreate.utils.NotificationUtil
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds


class SleepTimerService : Service(), KoinComponent {

    companion object {
        //<editor-fold defaultstate="collapsed" desc="Actions">
        const val ACTION_START = "app.kreate.player.SleepTimer.START"
        const val ACTION_RESUME = "app.kreate.player.SleepTimer.PLAY_RESUME"
        const val ACTION_PAUSE = "app.kreate.player.SleepTimer.PLAY_PAUSE"
        const val ACTION_RESTART = "app.kreate.player.SleepTimer.RESTART"
        const val ACTION_STOP = "app.kreate.player.SleepTimer.STOP"
        const val ACTION_CANCEL = "app.kreate.player.SleepTimer.CANCEL"
        //</editor-fold>

        const val EXTRA_DURATION_MILLIS = "extra_duration_millis"

        const val NOTIFICATION_ID = NotificationUtil.SLEEP_TIMER_NOTIFICATION_ID
        const val CHANNEL_ID = NotificationUtil.SLEEP_TIMER_CHANNEL_ID
    }

    private val serviceScope: CoroutineScope by inject()
    private val logger = Logger.withTag( "SleepTimerService" )
    private val title: String
        // Use getter to get localized text on each notification update
        get() = getString( R.string.notification_title_sleep_timer )
    private val description: String
        // Use getter to get localized text on each notification update
        get() = getString( R.string.notification_description_sleep_timer )

    private var tickJob: Job? = null
    private var totalDurationMillis: Long = 0L
    private var remainingMillis: Long = 0L
    private var targetElapsedRealtime: Long = 0L
    private var state: TimerState = TimerState.STOPPED

    override fun onCreate() {
        logger.v { "Initializing service" }

        super.onCreate()

        //<editor-fold desc="Create notification channel">
        if( IS_ANDROID_8_OR_LATER ) {          // Required on Android 8+
            val channel = NotificationChannel(CHANNEL_ID, title, NotificationManager.IMPORTANCE_LOW)
            channel.description = this.description
            channel.setShowBadge( false )

            getSystemService<NotificationManager>()?.createNotificationChannel( channel )

            logger.d { "Notification channel created" }
        }
        //</editor-fold>

        logger.d { "Service created" }
    }

    override fun onStartCommand( intent: Intent?, flags: Int, startId: Int ): Int {
        val action = intent?.action
        logger.d { "Received start command with action $action, flags $flags, id $startId" }

        when( action ) {
            ACTION_START        -> {
                val duration = intent.getLongExtra( EXTRA_DURATION_MILLIS, 1000L )
                if( duration < 1000L ) {
                    logger.w { "Can't start timer with less than a second" }
                } else
                    handleStart( duration )
            }
            ACTION_RESUME,
            ACTION_PAUSE        -> handlePlayPause()
            ACTION_RESTART      -> handleRestart()
            ACTION_STOP         -> handleStop()
            ACTION_CANCEL       -> handleCancel()
            // No action + no in-memory state (e.g. process was killed and
            // the system redelivered a null intent) -> nothing to resume.
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onBind( intent: Intent? ): IBinder? = null

    override fun onDestroy() {
        logger.v { "Shutting down service" }

        tickJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()

        logger.d { "Service destroyed" }
    }

    // ---------------------------------------------------------------------
    // Action handlers
    // ---------------------------------------------------------------------

    private fun handleStart( totalDuration: Long ) {
        totalDurationMillis = totalDuration
        remainingMillis = totalDurationMillis
        state = TimerState.RUNNING

        // Must be called within seconds of startForegroundService() being invoked.
        startForeground( NOTIFICATION_ID, buildNotification() )
        scheduleTicks()

        logger.d { "handleStart finished" }
    }

    private fun handlePlayPause() {
        when( state ) {
            TimerState.RUNNING  -> pause()
            TimerState.PAUSED,
            TimerState.STOPPED  -> resume()
        }

        logger.d { "handlePlayPause finished" }
    }

    private fun handleRestart() {
        remainingMillis = totalDurationMillis
        state = TimerState.RUNNING
        scheduleTicks()
        postNotification()

        logger.d { "handleRestart finished" }
    }

    private fun handleStop() {
        // Halts and resets the countdown but keeps the notification/service
        // alive so the user can hit play again.
        stopTicking()
        remainingMillis = totalDurationMillis
        state = TimerState.STOPPED
        postNotification()

        logger.d { "handleStop finished" }
    }

    private fun handleCancel() {
        // Fully dismisses the timer and tears the service down.
        stopTicking()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()

        logger.d { "handleCancel finished" }
    }

    private fun pause() {
        remainingMillis = (targetElapsedRealtime - SystemClock.elapsedRealtime()).coerceAtLeast(0)
        stopTicking()
        state = TimerState.PAUSED
        postNotification()

        logger.d { "Sleep timer paused" }
    }

    private fun resume() {
        state = TimerState.RUNNING
        scheduleTicks()
        postNotification()

        logger.d { "Sleep timer resumed" }
    }

    // ---------------------------------------------------------------------
    // Ticking - based on elapsedRealtime deltas rather than a naive -1000ms
    // per loop, so it can't drift from coroutine scheduling delays.
    // ---------------------------------------------------------------------

    private fun stopTicking() {
        tickJob?.cancel()
        tickJob = null

        logger.d { "Ticking stopped" }
    }

    private fun scheduleTicks() {
        stopTicking()
        targetElapsedRealtime = SystemClock.elapsedRealtime() + remainingMillis
        tickJob = serviceScope.launch {
            while( isActive ) {
                val now = SystemClock.elapsedRealtime()
                remainingMillis = (targetElapsedRealtime - now).coerceAtLeast(0)

                if( remainingMillis <= 0L ) {
                    onFinished()
                    break
                } else {
                    postNotification()
                }

                delay( 1.seconds )
            }
        }

        logger.d { "Ticking scheduled" }
    }

    private fun onFinished() {
        get<ExoPlayer>().stop()

        state = TimerState.STOPPED
        remainingMillis = 0L
        postNotification( finished = true )

        logger.d { "Timer finished" }
    }

    // ---------------------------------------------------------------------
    // Notification
    // ---------------------------------------------------------------------

    private fun postNotification( finished: Boolean = false ) {
        SleepTimer.update( TimerState(state, remainingMillis, totalDurationMillis) )

        // POST_NOTIFICATIONS only exists/matters from API 33 onward; below
        // that, notifications don't require a runtime grant.
        if( NotificationUtil.canPostNotification(this) )
            @SuppressLint("MissingPermission")
            NotificationManagerCompat.from( this ).notify( NOTIFICATION_ID, buildNotification(finished) )
        else
            logger.i { "Missing permission to post notification" }
    }

    private fun buildNotification(finished: Boolean = false): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setOnlyAlertOnce( true )
            .setContentTitle( title )
            .setSmallIcon( app.kreate.resources.R.drawable.timer )
            // Prohibit dismissal while it's running
            .setOngoing( state === TimerState.RUNNING )
            .setPriority( NotificationCompat.PRIORITY_LOW )
            .setCategory( NotificationCompat.CATEGORY_STOPWATCH )
            .setForegroundServiceBehavior( NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE )
            .setDeleteIntent( CancelButton().actionIntent )

        if( finished ) {
            val content = resources.getString( R.string.notification_content_sleep_timer_finished )
            builder.setContentText( content )
                   .addAction( RestartButton() )
        } else {
            val content = formatRemaining( remainingMillis )
            val primaryButton = when( state ) {
                TimerState.RUNNING -> PauseButton()
                TimerState.PAUSED  -> ResumeButton()
                TimerState.STOPPED -> RestartButton()
            }
            val secondaryButton = if( state !== TimerState.STOPPED ) StopButton() else null

            builder.setContentText( content )
                   .addAction( primaryButton )
            // Only add secondary button if it's not `null`, which is when timer isn't stopped
            secondaryButton?.also( builder::addAction )
            builder.addAction( CancelButton() )
        }

        return builder.build()
    }

    private fun actionIntent( action: String, requestCode: Int ): PendingIntent {
        val intent = Intent(this, SleepTimerService::class.java)
            .setAction( action )
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        return PendingIntent.getService( this, requestCode, intent, flags )
    }

    private fun formatRemaining( millis: Long ): String =
        millis.milliseconds.toComponents { hours, minutes, seconds, _ ->
            resources.getString( R.string.notification_content_sleep_timer_ongoing, hours, minutes, seconds )
        }

    //<editor-fold defaultstate="collapsed" desc="Pre-defined notification action buttons">
    private inner class ResumeButton : NotificationCompat.Action(
        app.kreate.resources.R.drawable.timer_play,
        resources.getString(R.string.notification_button_resume),
        actionIntent(ACTION_RESUME, 1)
    )

    private inner class PauseButton : NotificationCompat.Action(
        app.kreate.resources.R.drawable.timer_pause,
        resources.getString(R.string.notification_button_pause),
        actionIntent(ACTION_PAUSE, 2)
    )

    private inner class StopButton : NotificationCompat.Action(
        app.kreate.resources.R.drawable.stop_circle,
        resources.getString(R.string.notification_button_stop),
        actionIntent(ACTION_STOP, 3)
    )

    private inner class RestartButton : NotificationCompat.Action(
        app.kreate.resources.R.drawable.replay,
        resources.getString(R.string.notification_button_restart),
        actionIntent(ACTION_RESTART, 4)
    )

    private inner class CancelButton : NotificationCompat.Action(
        app.kreate.resources.R.drawable.cancel_circle,
        resources.getString(R.string.notification_button_cancel),
        actionIntent(ACTION_CANCEL, 5)
    )
    //</editor-fold>

    enum class TimerState { RUNNING, PAUSED, STOPPED }
}