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

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import me.knighthat.kreate3.database.model.SongFormat
import me.knighthat.kreate3.database.repository.SongRepository
import me.knighthat.kreate3.model.Origin
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

@OptIn(ExperimentalCoroutinesApi::class)
class SongFormatRecorderTest {

    /** Formats written to the database. */
    private val saved = mutableListOf<SongFormat>()

    /** A database without songs, it only remembers the formats written to it. */
    private val songs = Proxy.newProxyInstance(
        SongRepository::class.java.classLoader,
        arrayOf(SongRepository::class.java)
    ) { proxy, method, args ->
        when (method.name) {
            "find" -> flowOf(null)
            "saveFormat" -> {
                saved.add(args[0] as SongFormat)
                Unit
            }

            "toString" -> "SongRepository"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args[0]
            else -> throw UnsupportedOperationException(method.name)
        }
    } as SongRepository

    private val recorder = SongFormatRecorder(songs) { null }

    private val song = QueuedSong(Origin.YOUTUBE, "song")

    private val format = SongFormat(song.id, "251", "audio/webm", gain = 6.5f)

    @Test
    fun formatOfUnstoredSongIsKeptInMemory() = runTest {
        recorder.record(song, format)

        assertEquals(listOf(format), recorder.pendingFormatsOf(song.id))
        assertEquals(emptyList<SongFormat>(), saved)
    }

    @Test
    fun followersSeeTheFormatOnceItIsFound() = runTest {
        val seen = mutableListOf<List<SongFormat>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            recorder.pendingFormats(song.id).collect { seen += it }
        }

        recorder.record(song, format)

        assertEquals(listOf(emptyList(), listOf(format)), seen)
    }

    @Test
    fun storingTheSongMovesItsFormatToTheDatabase() = runTest {
        val seen = mutableListOf<List<SongFormat>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            recorder.pendingFormats(song.id).collect { seen += it }
        }
        recorder.record(song, format)

        recorder.flush(song.id)

        assertEquals(emptyList<SongFormat>(), recorder.pendingFormatsOf(song.id))
        assertEquals(listOf(format), saved)
        assertEquals(emptyList<SongFormat>(), seen.last())
    }

    @Test
    fun otherSongsDontChangeWhatIsFollowed() = runTest {
        val seen = mutableListOf<List<SongFormat>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            recorder.pendingFormats(song.id).collect { seen += it }
        }

        recorder.record(QueuedSong(Origin.YOUTUBE, "other"), format.copy(songId = "other"))

        assertEquals(listOf(emptyList<SongFormat>()), seen)
    }
}
