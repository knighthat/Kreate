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

package me.knighthat.kreate3.source

import coil3.size.Dimension
import coil3.size.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class CoverCacheTest {

    @Test
    fun rowsAndCardsLoadTheSmallCover() {
        assertEquals(SMALL_COVER, coverSize(Size(168, 168)))
        assertEquals(SMALL_COVER, coverSize(Size(425, 425)))
    }

    @Test
    fun theLargerSideDecides() {
        assertEquals(LARGE_COVER, coverSize(Size(300, 900)))
    }

    @Test
    fun drawnAtTheSmallSizeLoadsTheLargeCover() {
        // Not scaled down, Coil would take the small cover as the whole picture
        assertEquals(LARGE_COVER, coverSize(Size(SMALL_COVER, SMALL_COVER)))
    }

    @Test
    fun thePlayerLoadsTheLargeCover() {
        assertEquals(LARGE_COVER, coverSize(Size(1440, 1872)))
    }

    @Test
    fun anUnknownSizeLoadsTheLargeCover() {
        assertEquals(LARGE_COVER, coverSize(Size.ORIGINAL))
        assertEquals(LARGE_COVER, coverSize(Size(Dimension.Undefined, Dimension.Undefined)))
    }

    @Test
    fun aSmallPictureIsKeptWhole() {
        assertEquals(1, coverSampleSize(600, 600, LARGE_COVER))
        assertEquals(1, coverSampleSize(SMALL_COVER, SMALL_COVER, SMALL_COVER))
    }

    @Test
    fun aLargePictureStillCoversTheSide() {
        // 3000 / 2 = 1500 still covers 1440, 3000 / 4 wouldn't
        assertEquals(2, coverSampleSize(3000, 3000, LARGE_COVER))
        assertEquals(4, coverSampleSize(3000, 3000, SMALL_COVER))
        assertEquals(2, coverSampleSize(1024, 1024, SMALL_COVER))
    }

    @Test
    fun theShorterSideRunsOutFirst() {
        // 1280 x 720: halved, 360 would be under 512
        assertEquals(1, coverSampleSize(1280, 720, SMALL_COVER))
    }

    @Test
    fun anUnreadablePictureIsNotSampled() {
        // Bounds are -1 when the bytes aren't a picture
        assertEquals(1, coverSampleSize(-1, -1, SMALL_COVER))
    }
}
