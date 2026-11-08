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

package me.knighthat.kreate3.auto

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.playback.OPENED_COVER_FOLDER
import me.knighthat.kreate3.source.CoverFiles
import me.knighthat.kreate3.source.LARGE_COVER
import me.knighthat.kreate3.ui.component.EXTRA_ALBUM_ID
import me.knighthat.kreate3.ui.component.EXTRA_ORIGIN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ServedArtworkTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private val covers = listOf(
        "https://lh3.googleusercontent.com/abc=w226-h226",
        "https://music.example/rest/getCoverArt?id=al-1&size=512",
        "content://media/external/audio/albumart/42",
    ).map { it.toUri() }

    private fun song(cover: String?, origin: Origin? = null, album: String? = null) =
        MediaItem.Builder()
            .setMediaId("song")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setArtworkUri(cover?.toUri())
                    .setExtras(
                        bundleOf(EXTRA_ORIGIN to origin?.name, EXTRA_ALBUM_ID to album)
                    )
                    .build()
            )
            .build()

    @Test
    fun aServedCoverLeadsBackToTheOriginal() = covers.forEach { cover ->
        val served = checkNotNull(context.autoArtworkUri(cover))
        assertEquals("content", served.scheme)
        assertEquals("${context.packageName}.artwork", served.authority)
        assertEquals(cover, served.coverSource())
    }

    @Test
    fun aServedCoverIsntServedTwice() = covers.forEach { cover ->
        val served = context.autoArtworkUri(cover)
        assertEquals(served, context.autoArtworkUri(served))
    }

    @Test
    fun resourcesAndOtherAddressesStayAsTheyAre() {
        val icon = "android.resource://${context.packageName}/drawable/album".toUri()
        assertEquals(icon, context.autoArtworkUri(icon))
        assertNull(context.autoArtworkUri(null))
        covers.forEach { assertEquals(it, it.coverSource()) }
    }

    @Test
    fun queuedSongsCarryTheServedCover() {
        val queued = song(covers.first().toString()).withServedArtwork(context)
        val served = checkNotNull(queued.mediaMetadata.artworkUri)
        assertEquals("content", served.scheme)
        assertEquals("song", served.getQueryParameter("song"))
        assertEquals(covers.first(), served.coverSource())
        assertSame(queued, queued.withServedArtwork(context))

        val bare = song(null)
        assertSame(bare, bare.withServedArtwork(context))
    }

    @Test
    fun aQueuedSongSaysWhereItsCoversAre() {
        val served = song(covers.first().toString(), Origin.YOUTUBE, "album")
            .withServedArtwork(context).mediaMetadata.artworkUri

        assertEquals("YOUTUBE", served?.getQueryParameter("origin"))
        assertEquals("album", served?.getQueryParameter("album"))
    }

    @Test
    fun aDeviceSongWithoutACoverCanStillHaveOneInItsFile() {
        val local = MediaItem.Builder()
            .setMediaId("42")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setExtras(bundleOf(EXTRA_ORIGIN to Origin.LOCAL.name))
                    .build()
            )
            .build()
        val served = checkNotNull(local.withServedArtwork(context).mediaMetadata.artworkUri)

        assertEquals("42", served.getQueryParameter("song"))
        assertNull(served.coverSource())
    }

    @Test
    fun onlyCoverFilesAreServed() {
        val files = CoverFiles(context)
        fun file(file: File) = Uri.fromFile(file)

        assertTrue(context.isServedSource(file(files.downloaded("abc", LARGE_COVER))))
        assertTrue(context.isServedSource(file(File(files.custom("me"), "song-1.jpg"))))
        assertTrue(
            context.isServedSource(file(File(context.cacheDir, "$OPENED_COVER_FOLDER/abc.jpg")))
        )
        covers.forEach { assertTrue(context.isServedSource(it)) }

        assertFalse(context.isServedSource(file(context.getDatabasePath("kreate.db"))))
        assertFalse(
            context.isServedSource(
                file(File(files.downloads, "../../databases/kreate.db"))
            )
        )
        assertFalse(context.isServedSource("ftp://example/cover.jpg".toUri()))
        assertFalse(
            context.isServedSource(
                "content://${context.packageName}.artwork/cover?source=x".toUri()
            )
        )
    }
}
