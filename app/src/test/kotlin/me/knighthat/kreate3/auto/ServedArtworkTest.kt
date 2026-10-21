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
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ServedArtworkTest {

    private val context: Context = RuntimeEnvironment.getApplication()

    private val covers = listOf(
        "https://lh3.googleusercontent.com/abc=w226-h226",
        "https://music.example/rest/getCoverArt?id=al-1&size=512",
        "content://media/external/audio/albumart/42",
    ).map { it.toUri() }

    private fun song(cover: String?) = MediaItem.Builder()
        .setMediaId("song")
        .setMediaMetadata(MediaMetadata.Builder().setArtworkUri(cover?.toUri()).build())
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
        assertEquals(context.autoArtworkUri(covers.first()), queued.mediaMetadata.artworkUri)
        assertSame(queued, queued.withServedArtwork(context))

        val bare = song(null)
        assertSame(bare, bare.withServedArtwork(context))
    }
}
