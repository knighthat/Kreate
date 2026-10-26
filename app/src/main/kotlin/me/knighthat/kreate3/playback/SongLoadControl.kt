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
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.upstream.DefaultAllocator
import me.knighthat.kreate3.playback.SongLoadControl.Companion.AFTER_RUNNING_OUT_US
import me.knighthat.kreate3.playback.SongLoadControl.Companion.CEILING_MS
import me.knighthat.kreate3.preferences.Preferences
import kotlin.time.Duration.Companion.seconds

/**
 * Buffers songs by time, not by size: how far ahead to load ([aheadSeconds]) and how much must be
 * loaded before playing ([toStartSeconds]) are asked at each decision, so a change applies while
 * playing. What was played stays loaded for [keptPlayedMs], which the player reads once when it
 * is made.
 */
@OptIn(UnstableApi::class)
internal class SongLoadControl(
    keptPlayedMs: Int,
    private val aheadSeconds: () -> Int = { Preferences.PLAYBACK_BUFFER_AHEAD_SECONDS.value },
    private val toStartSeconds: () -> Int = { Preferences.PLAYBACK_BUFFER_TO_START_SECONDS.value },
) : DefaultLoadControl(
    DefaultAllocator(true, C.DEFAULT_BUFFER_SEGMENT_SIZE),
    CEILING_MS, CEILING_MS,
    CEILING_MS, CEILING_MS,
    0, 0,
    0, 0,
    C.LENGTH_UNSET,
    true, true,
    keptPlayedMs,
    false,
) {

    /** `null` loads the whole song. */
    private val aheadUs: Long?
        get() = aheadSeconds()
            .takeIf { it > 0 }
            ?.coerceAtMost(CEILING_SECONDS)
            ?.seconds
            ?.inWholeMicroseconds

    /**
     * The parent stops loading at [CEILING_MS] ahead and keeps track of whether it is loading
     * (the next song's preload waits for that), so it gets the buffer scaled to [aheadUs] instead
     * of being skipped. Loading the whole song hands it nothing buffered, it then loads until the
     * heap is nearly full.
     */
    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        val scaled = aheadUs?.let { parameters.bufferedDurationUs * CEILING_US / it } ?: 0
        return super.shouldContinueLoading(parameters.withBuffered(scaled))
    }

    /**
     * Never asks for more than [aheadUs], which would never come in. After running out it waits
     * for [AFTER_RUNNING_OUT_US] at least, so a slow network doesn't stop it again right away.
     */
    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean {
        val ahead = aheadUs ?: Long.MAX_VALUE
        val toStart = toStartSeconds().seconds.inWholeMicroseconds.coerceIn(0, ahead)
        val needed =
            if (parameters.rebuffering) toStart.coerceAtLeast(AFTER_RUNNING_OUT_US)
                .coerceAtMost(ahead)
            else toStart
        val buffered = Util.getPlayoutDurationForMediaDuration(
            parameters.bufferedDurationUs,
            parameters.playbackSpeed,
        )
        return needed <= 0 || buffered >= needed
    }

    private fun LoadControl.Parameters.withBuffered(bufferedDurationUs: Long) =
        LoadControl.Parameters(
            playerId,
            timeline,
            mediaPeriodId,
            playbackPositionUs,
            bufferedDurationUs,
            playbackSpeed,
            playWhenReady,
            rebuffering,
            targetLiveOffsetUs,
            lastRebufferRealtimeMs,
        )

    companion object {
        /** Most seconds the settings can ask to load ahead or before playing. */
        const val CEILING_SECONDS = 60

        /** What was played that stays loaded while [Preferences.PLAYBACK_KEEP_PLAYED] is on. */
        const val KEPT_PLAYED_MS = 60_000

        private const val CEILING_MS = CEILING_SECONDS * 1000
        private const val CEILING_US = CEILING_MS * 1000L

        /** Media3's default for going on after running out. */
        private const val AFTER_RUNNING_OUT_US = 2_000_000L
    }
}
