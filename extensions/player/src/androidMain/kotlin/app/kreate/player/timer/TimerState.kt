package app.kreate.player.timer

import app.kreate.player.SleepTimerService


data class TimerState(
    val state: SleepTimerService.TimerState,
    val remainingMillis: Long,
    val totalDurationMillis: Long
) {

    companion object {

        val Idle = TimerState(SleepTimerService.TimerState.STOPPED, 0L, 0L)
    }

    val isActive: Boolean
        get() = state !== SleepTimerService.TimerState.STOPPED && remainingMillis > 0
}