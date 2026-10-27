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

package me.knighthat.kreate3.download

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.offline.DownloadRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The song a download keeps, for its notifications to show and play it. */
@RunWith(RobolectricTestRunner::class)
class SongDownloadsTest {

    private fun request(data: ByteArray) =
        DownloadRequest.Builder("kreate://YOUTUBE/abc", Uri.parse("kreate://YOUTUBE/abc"))
            .setData(data)
            .build()

    @Test
    fun songComesBackFromItsDownload() {
        val item = MediaItem.Builder()
            .setMediaId("abc")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Neon Coast")
                    .setArtist("Afterglow")
                    .setArtworkUri(Uri.parse("https://example.com/cover.jpg"))
                    .setExtras(Bundle().apply { putString("origin", "YOUTUBE") })
                    .build()
            )
            .build()

        val song = request(item.toDownloadData()).songItem!!
        assertEquals("abc", song.mediaId)
        assertEquals("Neon Coast", song.mediaMetadata.title.toString())
        assertEquals("Afterglow", song.mediaMetadata.artist.toString())
        assertEquals(item.mediaMetadata.artworkUri, song.mediaMetadata.artworkUri)
        assertEquals("YOUTUBE", song.mediaMetadata.extras?.getString("origin"))
    }

    @Test
    fun requestWithoutASongHasNone() {
        // Robolectric's Parcel can't tell how damaged bytes would fail on a device
        assertNull(request(ByteArray(0)).songItem)
    }
}
