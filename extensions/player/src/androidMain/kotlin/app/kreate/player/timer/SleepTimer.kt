package app.kreate.player.timer

import android.content.Context
import android.content.Intent
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import app.kreate.player.SleepTimerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow


object SleepTimer {

    private val _state = MutableStateFlow(TimerState.Idle)

    val state = _state.asStateFlow()

    @MainThread
    internal fun update( state: TimerState ) {
        this._state.value = state
    }

    fun start( context: Context, durationMillis: Long ) {
        val intent = Intent(context, SleepTimerService::class.java)
            .setAction( SleepTimerService.ACTION_START )
            .putExtra( SleepTimerService.EXTRA_DURATION_MILLIS, durationMillis )

        ContextCompat.startForegroundService( context, intent )
    }

    fun stop( context: Context ) {
        val intent = Intent(context, SleepTimerService::class.java)
            .setAction(SleepTimerService.ACTION_STOP )

        ContextCompat.startForegroundService( context, intent )
    }
}