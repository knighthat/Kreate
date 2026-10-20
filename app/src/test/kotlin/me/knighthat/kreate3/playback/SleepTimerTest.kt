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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.time.Duration.Companion.minutes

@RunWith(RobolectricTestRunner::class)
class SleepTimerTest {

    private val minute = 1.minutes.inWholeMilliseconds

    // Robolectric's clock stands still, the time left doesn't change during a test
    private fun runningFor(minutes: Int) = SleepTimer.after(minutes.minutes)

    @Test
    fun addingMovesTheEnd() {
        val timer = runningFor(10)
        assertEquals(
            SleepTimer.Running(timer.endsAt + 5 * minute),
            timer.changedBy(SleepTimerStep.FIVE_MINUTES, add = true),
        )
    }

    @Test
    fun removingCantTakeAllThatIsLeft() {
        assertEquals(
            SleepTimer.Running(runningFor(10).endsAt - 5 * minute),
            runningFor(10).changedBy(SleepTimerStep.FIVE_MINUTES, add = false),
        )
        assertNull(runningFor(5).changedBy(SleepTimerStep.FIVE_MINUTES, add = false))
        assertNull(runningFor(2).changedBy(SleepTimerStep.TEN_MINUTES, add = false))
    }

    @Test
    fun aTimerOnHoldChangesWhatIsLeft() {
        val held = SleepTimer.Paused(10 * minute)
        assertEquals(
            SleepTimer.Paused(12 * minute),
            held.changedBy(SleepTimerStep.TWO_MINUTES, true)
        )
        assertEquals(
            SleepTimer.Paused(9 * minute),
            held.changedBy(SleepTimerStep.ONE_MINUTE, false)
        )
    }

    @Test
    fun untilTheSongEndsFollowsTheSongEitherWay() {
        val step = SleepTimerStep.UNTIL_SONG_ENDS
        assertEquals(SleepTimer.EndOfSong(), runningFor(30).changedBy(step, add = true))
        assertEquals(SleepTimer.EndOfSong(), runningFor(30).changedBy(step, add = false))
        // Kept on hold
        assertEquals(
            SleepTimer.EndOfSong(isPaused = true),
            SleepTimer.Paused(minute).changedBy(step, add = true),
        )
        assertNull(SleepTimer.EndOfSong().changedBy(step, add = true))
    }

    @Test
    fun aTimerFollowingTheSongHasNoTimeToChange() {
        assertNull(SleepTimer.EndOfSong().changedBy(SleepTimerStep.FIVE_MINUTES, add = true))
        assertNull(SleepTimer.EndOfSong().changedBy(SleepTimerStep.FIVE_MINUTES, add = false))
    }

    @Test
    fun holdingKeepsWhatIsLeft() {
        val timer = runningFor(7)
        val held = timer.togglePaused()
        assertEquals(SleepTimer.Paused(7 * minute), held)
        assertEquals(
            SleepTimer.Running(SystemClock.elapsedRealtime() + 7 * minute),
            held.togglePaused()
        )
        assertEquals(SleepTimer.EndOfSong(isPaused = true), SleepTimer.EndOfSong().togglePaused())
    }

    @Test
    fun timersGoThroughTheSessionExtras() {
        listOf(
            runningFor(3),
            SleepTimer.Paused(42_000),
            SleepTimer.EndOfSong(),
            SleepTimer.EndOfSong(isPaused = true),
        ).forEach { timer ->
            val extras = Bundle().apply {
                putInt(EXTRA_AUDIO_SESSION_ID, 7)
                putSleepTimer(timer)
            }
            assertEquals(timer, extras.sleepTimer())
            // What else is there stays
            assertEquals(7, extras.getInt(EXTRA_AUDIO_SESSION_ID))
        }
        assertNull(Bundle().apply { putSleepTimer(runningFor(1)); putSleepTimer(null) }
            .sleepTimer())
    }

    @Test
    fun buttonsSayTheStep() {
        assertEquals("+5 min", SleepTimerStep.FIVE_MINUTES.buttonLabel(add = true))
        assertEquals("\u22122 min", SleepTimerStep.TWO_MINUTES.buttonLabel(add = false))
        assertEquals("Song end", SleepTimerStep.UNTIL_SONG_ENDS.buttonLabel(add = true))
    }
}
