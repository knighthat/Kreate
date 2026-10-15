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

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// A PlaybackException reads the Android clock when it's made
@RunWith(RobolectricTestRunner::class)
class PlaybackErrorsTest {

    private fun error(code: Int) = PlaybackException("test", null, code)

    private val song = MediaItem.Builder()
        .setMediaId("1")
        .setMediaMetadata(MediaMetadata.Builder().setTitle("Derezzed").build())
        .build()

    @Test
    fun messageNamesTheSongAndWhyItFailed() {
        assertEquals(
            "Couldn't play \"Derezzed\": its file is gone",
            error(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND).userMessage(song),
        )
        assertEquals(
            "Couldn't play \"Derezzed\": no connection",
            error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED).userMessage(song),
        )
        assertEquals(
            "Couldn't play \"Derezzed\": its format isn't supported",
            error(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED).userMessage(song),
        )
    }

    @Test
    fun sourceErrorsSayWhatIsWrongWithTheSource() {
        assertEquals(
            "Couldn't play \"Derezzed\": its source is off or not set up",
            error(ERROR_CODE_SOURCE_OFF).userMessage(song),
        )
        assertEquals(
            "Couldn't play \"Derezzed\": its source has nothing to play",
            error(ERROR_CODE_NOT_PLAYABLE).userMessage(song),
        )
    }

    @Test
    fun songWithoutTitleAndUnknownErrorStillSaySomething() {
        assertEquals(
            "Couldn't play this song: something went wrong",
            error(PlaybackException.ERROR_CODE_UNSPECIFIED).userMessage(null),
        )
    }
}
