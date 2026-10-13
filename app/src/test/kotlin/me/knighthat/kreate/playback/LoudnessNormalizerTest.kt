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

package me.knighthat.kreate.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class LoudnessNormalizerTest {

    @Test
    fun unknownGainChangesNothing() {
        assertEquals(0f, normalizationDb(null), 0f)
    }

    @Test
    fun louderSongIsCutByItsGain() {
        assertEquals(-6.5f, normalizationDb(6.5f), 0f)
    }

    @Test
    fun quieterSongIsBoosted() {
        assertEquals(3f, normalizationDb(-3f), 0f)
    }

    @Test
    fun boostIsLimited() {
        assertEquals(6f, normalizationDb(-20f), 0f)
    }

    @Test
    fun cutIsLimited() {
        assertEquals(-24f, normalizationDb(40f), 0f)
    }

    @Test
    fun sixDecibelsHalvesTheVolume() {
        assertEquals(0.5f, cutToVolume(-6.0206f), 0.001f)
    }

    @Test
    fun noCutKeepsFullVolume() {
        assertEquals(1f, cutToVolume(0f), 0f)
        assertEquals(1f, cutToVolume(3f), 0f)
    }
}
