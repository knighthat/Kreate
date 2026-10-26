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
import me.knighthat.kreate3.R
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
    fun songTitleIsTheOneToShow() {
        assertEquals("Derezzed", song.songTitle())
        assertEquals(null, null.songTitle())
        assertEquals(
            null,
            MediaItem.Builder()
                .setMediaId("2")
                .setMediaMetadata(MediaMetadata.Builder().setTitle("  ").build())
                .build()
                .songTitle(),
        )
    }

    @Test
    fun errorsSayWhyTheSongFailed() {
        assertEquals(
            R.string.error_playback_file_missing,
            error(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND).reasonRes(),
        )
        assertEquals(
            R.string.error_playback_no_connection,
            error(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED).reasonRes(),
        )
        assertEquals(
            R.string.error_playback_format_unsupported,
            error(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED).reasonRes(),
        )
    }

    @Test
    fun sourceErrorsSayWhatIsWrongWithTheSource() {
        assertEquals(R.string.error_playback_source_off, error(ERROR_CODE_SOURCE_OFF).reasonRes())
        assertEquals(
            R.string.error_playback_not_playable,
            error(ERROR_CODE_NOT_PLAYABLE).reasonRes()
        )
    }

    @Test
    fun songWithoutTitleAndUnknownErrorStillSaySomething() {
        val unknown = error(PlaybackException.ERROR_CODE_UNSPECIFIED)
        assertEquals(R.string.error_playback_unknown, unknown.reasonRes())
    }
}
