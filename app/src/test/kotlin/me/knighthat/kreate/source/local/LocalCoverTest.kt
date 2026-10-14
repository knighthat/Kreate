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

package me.knighthat.kreate.source.local

import me.knighthat.kreate.source.local.LocalCoverFetcher.Companion.MAX_SIZE
import me.knighthat.kreate.source.local.LocalCoverFetcher.Companion.sampleSize
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalCoverTest {

    @Test
    fun aSmallPictureIsKeptWhole() {
        assertEquals(1, sampleSize(500, 500, MAX_SIZE, MAX_SIZE))
    }

    @Test
    fun aLargePictureIsShrunkUnderTheLimit() {
        assertEquals(4, sampleSize(3000, 3000, MAX_SIZE, MAX_SIZE))
        // One side over the limit is enough
        assertEquals(2, sampleSize(2000, 100, MAX_SIZE, MAX_SIZE))
    }

    @Test
    fun aPictureStillCoversWhatIsAsked() {
        // 1000 / 8 = 125 still covers 100, 1000 / 16 wouldn't
        assertEquals(8, sampleSize(1000, 1000, 100, 100))
        assertEquals(1, sampleSize(150, 150, 100, 100))
    }

    @Test
    fun bothSidesMustStillCover() {
        // The height runs out first
        assertEquals(2, sampleSize(1000, 400, 100, 200))
    }

    @Test
    fun anUnreadablePictureIsNotSampled() {
        // Bounds are -1 when the bytes aren't a picture
        assertEquals(1, sampleSize(-1, -1, 100, 100))
    }
}
