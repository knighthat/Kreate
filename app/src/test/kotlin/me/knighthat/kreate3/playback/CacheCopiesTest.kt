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

import me.knighthat.kreate3.constant.StreamQuality
import me.knighthat.kreate3.constant.StreamQuality.HIGH
import me.knighthat.kreate3.constant.StreamQuality.LOW
import me.knighthat.kreate3.constant.StreamQuality.MEDIUM
import me.knighthat.kreate3.constant.StreamQuality.ORIGINAL
import me.knighthat.kreate3.database.model.Origin
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CacheCopiesTest {

    private val song = QueuedSong(Origin.YOUTUBE, "v1")

    /** The copies kept of the song, with whether each is whole. */
    private val kept = mutableMapOf<StreamQuality, Boolean>()

    private var quality = HIGH
    private var online = true

    private val copies = CacheCopies(
        copies = { kept.toMap() },
        drop = { kept.remove(it.quality) },
        quality = { quality },
        isOnline = { online },
    )

    @Test
    fun nothingKeptIsStreamedInTheQualitySet() {
        assertEquals(HIGH, copies.pick(song, 0))
    }

    @Test
    fun aBetterWholeCopyIsPlayed() {
        kept[ORIGINAL] = true
        quality = LOW
        assertEquals(ORIGINAL, copies.pick(song, 0))
    }

    @Test
    fun aWholeCopyComesBeforeABetterPart() {
        kept[HIGH] = true
        kept[ORIGINAL] = false
        quality = MEDIUM
        assertEquals(HIGH, copies.pick(song, 0))
    }

    @Test
    fun aBetterPartIsReadOnline() {
        kept[ORIGINAL] = false
        assertEquals(ORIGINAL, copies.pick(song, 0))
    }

    @Test
    fun aLowerWholeCopyWaitsWhileABetterOneIsStreamed() {
        kept[LOW] = true
        assertEquals(HIGH, copies.pick(song, 0))
        assertEquals(mapOf(LOW to true), kept)
    }

    @Test
    fun offlineTheBestWholeCopyIsPlayed() {
        kept[LOW] = true
        kept[MEDIUM] = true
        kept[ORIGINAL] = false
        online = false
        assertEquals(MEDIUM, copies.pick(song, 0))
    }

    @Test
    fun offlineWithoutAWholeCopyTheBestPartIsPlayed() {
        kept[LOW] = false
        online = false
        assertEquals(LOW, copies.pick(song, 0))
    }

    @Test
    fun theRestOfTheSongIsOfTheCopyItStartedWith() {
        kept[LOW] = true
        assertEquals(HIGH, copies.pick(song, 0))
        // A seek after going offline, the HIGH file goes on
        online = false
        assertEquals(HIGH, copies.pick(song, 1024))
        // Played again from the start
        assertEquals(LOW, copies.pick(song, 0))
    }

    @Test
    fun aSeekWithNothingReadYetPicksLikeTheStart() {
        kept[ORIGINAL] = true
        quality = LOW
        assertEquals(ORIGINAL, copies.pick(song, 1024))
    }

    @Test
    fun lowerCopiesAreDroppedOnceABetterOneIsWhole() {
        kept[LOW] = true
        kept[MEDIUM] = false
        kept[HIGH] = false
        kept[ORIGINAL] = false
        copies.readEnded(song.copy(quality = HIGH))
        assertEquals(4, kept.size)

        kept[HIGH] = true
        copies.readEnded(song.copy(quality = HIGH))
        // A better part stays
        assertEquals(mapOf(HIGH to true, ORIGINAL to false), kept)
    }

    @Test
    fun aReadNotThroughTheCacheDropsNothing() {
        kept[LOW] = true
        kept[HIGH] = true
        copies.readEnded(song)
        assertEquals(2, kept.size)
    }
}
