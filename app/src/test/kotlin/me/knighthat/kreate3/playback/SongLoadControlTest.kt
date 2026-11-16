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
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SinglePeriodTimeline
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
        val control = control(ahead = 30, toStart = 60)
        assertTrue(control.shouldStartPlayback(parameters(30_000)))
    }

    @Test
    fun loadingFiveMinutesAheadWaitsForAllThatIsAsked() {
        val control = control(ahead = 300, toStart = 60)
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
    fun loadsThirtySecondsAheadAtLeast() {
        // A value below the slider, like the old 1 to 29 seconds, loads 30
        val control = control(ahead = 1, toStart = 60)
        assertFalse(control.shouldStartPlayback(parameters(29_999)))
        assertTrue(control.shouldStartPlayback(parameters(30_000)))
    }

    @Test
    fun loadsAtMostFiveMinutesAhead() {
        // The old whole song (0) and anything past 5 minutes load 5 minutes
        listOf(0, 300, 3_600).forEach { ahead ->
            val control = control(ahead = ahead, toStart = 1)
            control.onPrepared(PlayerId.UNSET)
            assertTrue(control.shouldContinueLoading(playing(bufferedMs = 299_000)))
            assertFalse(control.shouldContinueLoading(playing(bufferedMs = 300_000)))
        }
    }

    /** What the parent is asked while playing: it looks the song up in the timeline. */
    private fun playing(bufferedMs: Long): LoadControl.Parameters {
        val timeline = SinglePeriodTimeline(
            C.TIME_UNSET, true, false, false, null, MediaItem.Builder().setMediaId("song").build(),
        )
        return LoadControl.Parameters(
            PlayerId.UNSET,
            timeline,
            MediaSource.MediaPeriodId(timeline.getUidOfPeriod(0)),
            0,
            bufferedMs * 1000,
            1f,
            true,
            false,
            C.TIME_UNSET,
            C.TIME_UNSET,
        )
    }

    @Test
    fun fasterPlaybackNeedsMoreLoaded() {
        val control = control(ahead = 50, toStart = 4)
        assertFalse(control.shouldStartPlayback(parameters(4_000, speed = 2f)))
        assertTrue(control.shouldStartPlayback(parameters(8_000, speed = 2f)))
    }
}
