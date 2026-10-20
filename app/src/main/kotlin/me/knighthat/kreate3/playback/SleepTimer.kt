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

import android.os.Bundle
import android.os.SystemClock
import me.knighthat.kreate3.constant.SleepTimerStep
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * When playback pauses by itself, see [PlaybackController.sleepTimer]. It is kept by the
 * service, next to the player, see [SleepTimerRunner].
 */
sealed interface SleepTimer {

    /** Whether the timer is on hold: it doesn't count down nor pause anything until resumed. */
    val isPaused: Boolean

    /** Milliseconds left, `null` for a timer that follows the song instead of the clock. */
    val remaining: Long?

    /**
     * Pauses at [endsAt], milliseconds of `SystemClock.elapsedRealtime`, which goes on while the
     * device sleeps. A timer that ends while nothing plays is dropped, playing again isn't paused.
     */
    data class Running(val endsAt: Long) : SleepTimer {

        override val isPaused: Boolean
            get() = false

        /** `0` once it ended. */
        override val remaining: Long
            get() = (endsAt - SystemClock.elapsedRealtime()).coerceAtLeast(0)
    }

    /** A countdown on hold with [remaining] milliseconds left. */
    data class Paused(override val remaining: Long) : SleepTimer {

        override val isPaused: Boolean
            get() = true
    }

    /** Pauses as the song playing ends, unless [isPaused]. */
    data class EndOfSong(override val isPaused: Boolean = false) : SleepTimer {

        override val remaining: Long?
            get() = null
    }

    companion object {

        /** Ends [duration] from now. */
        fun after(duration: Duration): Running =
            Running(SystemClock.elapsedRealtime() + duration.inWholeMilliseconds)
    }
}

/** What can be done to a running sleep timer, from the full player or its notification. */
enum class SleepTimerAction {
    /** Puts it on hold, or lets it go on. */
    TOGGLE_PAUSE,

    /** Adds [me.knighthat.kreate3.preferences.Preferences.SLEEP_TIMER_ADD_STEP]. */
    ADD,

    /** Takes [me.knighthat.kreate3.preferences.Preferences.SLEEP_TIMER_REMOVE_STEP] off. */
    REMOVE,

    /** Turns it off. */
    STOP,
}

/** The timer put on hold or let go on. */
fun SleepTimer.togglePaused(): SleepTimer = when (this) {
    is SleepTimer.Running -> SleepTimer.Paused(remaining)
    is SleepTimer.Paused -> SleepTimer.Running(SystemClock.elapsedRealtime() + remaining)
    is SleepTimer.EndOfSong -> copy(isPaused = !isPaused)
}

/**
 * The timer with [step] added, or taken off when not [add], `null` when it can't change so.
 * [SleepTimerStep.UNTIL_SONG_ENDS] makes it end with the song, whichever way. A timer that ends
 * with the song has no time to change, and removing can't take all the time left.
 */
fun SleepTimer.changedBy(step: SleepTimerStep, add: Boolean): SleepTimer? {
    val minutes = step.minutes
        ?: return if (this is SleepTimer.EndOfSong) null else SleepTimer.EndOfSong(isPaused)
    val change = minutes.minutes.inWholeMilliseconds * if (add) 1 else -1
    val left = remaining ?: return null
    if (left + change <= 0) return null

    return when (this) {
        is SleepTimer.Running -> SleepTimer.Running(endsAt + change)
        is SleepTimer.Paused -> SleepTimer.Paused(left + change)
        is SleepTimer.EndOfSong -> null
    }
}

/** What a button shows for [step], like "+5 min", or "−5 min" when it takes off. */
fun SleepTimerStep.buttonLabel(add: Boolean): String =
    minutes?.let { "${if (add) "+" else "−"}$it min" } ?: "Song end"

private const val KEY_SLEEP_TIMER = "sleep_timer"
private const val KEY_ENDS_AT = "ends_at"
private const val KEY_REMAINING = "remaining"
private const val KEY_PAUSED = "paused"

/** Puts [timer] in the session's extras, for the controllers to read with [sleepTimer]. */
internal fun Bundle.putSleepTimer(timer: SleepTimer?) {
    if (timer == null) return remove(KEY_SLEEP_TIMER)
    putBundle(KEY_SLEEP_TIMER, Bundle().apply {
        when (timer) {
            is SleepTimer.Running -> putLong(KEY_ENDS_AT, timer.endsAt)
            is SleepTimer.Paused -> putLong(KEY_REMAINING, timer.remaining)
            is SleepTimer.EndOfSong -> putBoolean(KEY_PAUSED, timer.isPaused)
        }
    })
}

/** The timer [putSleepTimer] put in the session's extras, `null` when there is none. */
internal fun Bundle.sleepTimer(): SleepTimer? {
    val timer = getBundle(KEY_SLEEP_TIMER) ?: return null
    return when {
        timer.containsKey(KEY_ENDS_AT) -> SleepTimer.Running(timer.getLong(KEY_ENDS_AT))
        timer.containsKey(KEY_REMAINING) -> SleepTimer.Paused(timer.getLong(KEY_REMAINING))
        else -> SleepTimer.EndOfSong(timer.getBoolean(KEY_PAUSED))
    }
}
