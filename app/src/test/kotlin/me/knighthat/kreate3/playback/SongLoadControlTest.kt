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

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(UnstableApi::class)
class SongLoadControlTest {

    private fun control(ahead: Int, toStart: Int) =
        SongLoadControl(keptPlayedMs = 0, aheadSeconds = { ahead }, toStartSeconds = { toStart })

    private fun parameters(bufferedMs: Long, rebuffering: Boolean = false, speed: Float = 1f) =
        LoadControl.Parameters(
            PlayerId.UNSET,
            Timeline.EMPTY,
            MediaSource.MediaPeriodId(Any()),
            0,
            bufferedMs * 1000,
            speed,
            true,
            rebuffering,
            C.TIME_UNSET,
            C.TIME_UNSET,
        )

    @Test
    fun startsOnceWhatTheSettingAsksIsLoaded() {
        val control = control(ahead = 50, toStart = 5)
        assertFalse(control.shouldStartPlayback(parameters(4_999)))
        assertTrue(control.shouldStartPlayback(parameters(5_000)))
    }

    @Test
    fun neverWaitsForMoreThanIsLoadedAhead() {
        val control = control(ahead = 3, toStart = 10)
        assertTrue(control.shouldStartPlayback(parameters(3_000)))
    }

    @Test
    fun loadingTheWholeSongWaitsForAllThatIsAsked() {
        val control = control(ahead = 0, toStart = 60)
        assertFalse(control.shouldStartPlayback(parameters(59_999)))
        assertTrue(control.shouldStartPlayback(parameters(60_000)))
    }

    @Test
    fun waitsTwoSecondsAtLeastAfterRunningOut() {
        val control = control(ahead = 50, toStart = 1)
        assertFalse(control.shouldStartPlayback(parameters(1_500, rebuffering = true)))
        assertTrue(control.shouldStartPlayback(parameters(2_000, rebuffering = true)))
    }

    @Test
    fun runningOutWaitsNoLongerThanIsLoadedAhead() {
        val control = control(ahead = 1, toStart = 1)
        assertTrue(control.shouldStartPlayback(parameters(1_000, rebuffering = true)))
    }

    @Test
    fun fasterPlaybackNeedsMoreLoaded() {
        val control = control(ahead = 50, toStart = 4)
        assertFalse(control.shouldStartPlayback(parameters(4_000, speed = 2f)))
        assertTrue(control.shouldStartPlayback(parameters(8_000, speed = 2f)))
    }
}
