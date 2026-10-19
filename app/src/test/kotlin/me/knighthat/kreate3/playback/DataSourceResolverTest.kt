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

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import me.knighthat.kreate3.constant.StreamQuality
import me.knighthat.kreate3.database.model.Album
import me.knighthat.kreate3.database.model.Artist
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.database.model.Song
import me.knighthat.kreate3.database.model.SongFormat
import me.knighthat.kreate3.provider.Provider
import me.knighthat.kreate3.provider.Providers
import me.knighthat.kreate3.provider.SearchResults
import me.knighthat.kreate3.provider.opensubsonic.ServerStream
import me.knighthat.kreate3.provider.youtube.YouTubeException
import me.knighthat.kreate3.provider.youtube.YouTubeStream
import me.knighthat.kreate3.ui.component.EXTRA_ORIGIN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
class DataSourceResolverTest {

    private class FakeProvider(
        override val origin: Origin,
        private val uri: (String) -> Uri?,
    ) : Provider {
        override suspend fun search(query: String, limit: Int, offset: Int): SearchResults = TODO()
        override suspend fun song(songId: String): Song? = TODO()
        override suspend fun album(albumId: String): Album? = TODO()
        override suspend fun artist(artistId: String): Artist? = TODO()
        override suspend fun songsOf(albumId: String): List<Song> = TODO()
        override suspend fun albumsOf(artistId: String): List<Album> = TODO()
        override suspend fun playbackUri(songId: String): Uri? = uri(songId)
        override fun artworkUri(artworkId: String, size: Int?): Uri? = TODO()
    }

    private val now = Instant.fromEpochSeconds(1_700_000_000)

    /** Time passed since [now]. */
    private var elapsed = Duration.ZERO
    private val clock = object : Clock {
        override fun now(): Instant = now + elapsed
    }

    private var serverUri: Uri? = "https://music.example/rest/stream?id=s1".toUri()

    /** The `refused` set of every request for a stream, in order. */
    private val asked = mutableListOf<Set<String>>()

    /** The songs read, with the client of the YouTube stream found for them. */
    private val beforeRead = mutableListOf<Pair<QueuedSong, String?>>()

    /** Size of the file the cache has parts of, by cache key. */
    private val cached = mutableMapOf<String, Long>()

    /** The quality of every request for a stream, in order. */
    private val qualities = mutableListOf<StreamQuality>()

    /** The quality of every request for a song of the server, in order. */
    private val serverAsked = mutableListOf<StreamQuality>()

    /** Every answer told with the stream and the media type, in order. */
    private val answered = mutableListOf<Triple<QueuedSong, ServerStream, String?>>()

    /** Answers a request for a song of the server, by quality. */
    private var serverAnswer: (StreamQuality) -> ServerStream? =
        { serverUri?.let { ServerStream(it, null, null) } }

    /** The quality to stream in now. */
    private var quality = StreamQuality.HIGH

    /** Answers a request for a stream, by the clients refused so far. */
    private var answer: (Set<String>) -> YouTubeStream? = { stream("VISIONOS") }

    private val resolver = DataSourceResolver(
        Providers(
            listOf(
                FakeProvider(Origin.LOCAL) { "content://media/external/audio/media/$it".toUri() },
                FakeProvider(Origin.OPEN_SUBSONIC) { serverUri },
                FakeProvider(Origin.YOUTUBE) { error("YouTube streams are asked directly") },
            )
        ),
        { _, refused, asking -> asked += refused; qualities += asking; answer(refused) },
        { song, format -> beforeRead += song to format?.tag },
        { song -> cached[song.cacheKey] },
        { song -> cached.remove(song.cacheKey) },
        clock,
        { quality },
        { _, asking -> serverAsked += asking; serverAnswer(asking) },
        { song, stream, type -> answered += Triple(song, stream, type) },
    )

    private fun stream(
        client: String,
        expiresAt: Instant? = now + 6.hours,
        rangeSize: Long? = null,
        contentLength: Long? = null,
    ) = YouTubeStream(
        url = "https://rr1.googlevideo.com/videoplayback?c=$client",
        headers = mapOf("User-Agent" to client),
        mimeType = null,
        bitrate = null,
        contentLength = contentLength,
        loudnessDb = null,
        expiresAt = expiresAt,
        rangeSize = rangeSize,
        client = client,
        itag = 251,
    )

    private fun spec(origin: Origin, id: String, position: Long = 1024) =
        DataSpec.Builder().setUri(QueuedSong(origin, id).uri).setPosition(position).build()

    private fun resolve(origin: Origin, id: String, position: Long = 1024) =
        resolver.resolveDataSpec(spec(origin, id, position))

    private fun failure(origin: Origin, id: String): Int =
        assertThrows(DataSourceException::class.java) { resolve(origin, id) }.reason

    @Test
    fun queuedSongAddressKeepsOriginAndId() {
        val song = QueuedSong(Origin.OPEN_SUBSONIC, "al-1/2 ?x")
        assertEquals(song, QueuedSong.of(song.uri))
        // The client that found a stream doesn't change where the song is cached
        assertEquals(song.cacheKey, song.copy(client = "VISIONOS").cacheKey)
        assertNull(QueuedSong.of("https://example.com/a".toUri()))
        assertNull(QueuedSong.of("kreate://SOMEWHERE/a".toUri()))
    }

    @Test
    fun itemGetsTheAddressOfItsSong() {
        val item = MediaItem.Builder()
            .setMediaId("42")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setExtras(Bundle().apply { putString(EXTRA_ORIGIN, Origin.LOCAL.name) })
                    .build()
            )
            .build()

        val queued = item.withSongUri()
        assertEquals(QueuedSong(Origin.LOCAL, "42").uri, queued?.localConfiguration?.uri)
        assertSame(queued, queued?.withSongUri())
        assertNull(MediaItem.Builder().setMediaId("42").build().withSongUri())
    }

    @Test
    fun otherAddressesAreLeftAlone() {
        val spec = DataSpec("https://example.com/song.mp3".toUri())
        assertSame(spec, resolver.resolveDataSpec(spec))
    }

    @Test
    fun localAndServerSongsAskTheirProvider() {
        val local = resolve(Origin.LOCAL, "42")
        assertEquals("content://media/external/audio/media/42", local.uri.toString())
        assertEquals(1024, local.position)
        assertEquals(QueuedSong(Origin.LOCAL, "42"), local.customData)

        assertEquals(serverUri, resolve(Origin.OPEN_SUBSONIC, "s1").uri)
        assertEquals(
            listOf(
                QueuedSong(Origin.LOCAL, "42") to null,
                QueuedSong(Origin.OPEN_SUBSONIC, "s1") to null
            ),
            beforeRead,
        )
    }

    @Test
    fun serverThatIsOffSaysSo() {
        serverUri = null
        assertEquals(ERROR_CODE_SOURCE_OFF, failure(Origin.OPEN_SUBSONIC, "s1"))
    }

    @Test
    fun serverSongGoesOnInTheQualityItStartedIn() {
        resolve(Origin.OPEN_SUBSONIC, "s1", position = 0)
        quality = StreamQuality.LOW
        // A seek reads the same address
        resolve(Origin.OPEN_SUBSONIC, "s1")
        // Until it is old, then the same quality is asked again
        elapsed += 2.hours
        resolve(Origin.OPEN_SUBSONIC, "s1")
        // Played again from the start
        resolve(Origin.OPEN_SUBSONIC, "s1", position = 0)

        assertEquals(listOf(StreamQuality.HIGH, StreamQuality.HIGH, StreamQuality.LOW), serverAsked)
    }

    @Test
    fun knownServerFormatIsPassedOnOncePerStream() {
        val format = SongFormat("s1", "opus-96", "audio/ogg; codecs=\"opus\"", 96_000)
        serverAnswer = { ServerStream(serverUri!!, format, null) }
        resolve(Origin.OPEN_SUBSONIC, "s1", position = 0)
        resolve(Origin.OPEN_SUBSONIC, "s1")
        resolver.answered(QueuedSong(Origin.OPEN_SUBSONIC, "s1"), "audio/ogg")

        assertEquals(
            listOf(
                QueuedSong(Origin.OPEN_SUBSONIC, "s1") to "opus-96",
                QueuedSong(Origin.OPEN_SUBSONIC, "s1") to null
            ), beforeRead
        )
        // Nothing left to learn from the answer
        assertEquals(emptyList<Any>(), answered)
    }

    @Test
    fun answerToAStreamOfUnknownFormatIsToldOnce() {
        val song = QueuedSong(Origin.OPEN_SUBSONIC, "s1")
        serverAnswer = { ServerStream(serverUri!!, null, 128) }
        resolve(Origin.OPEN_SUBSONIC, "s1", position = 0)
        resolver.answered(song, "audio/mpeg")
        resolver.answered(song, "audio/mpeg")

        assertEquals(
            listOf(Triple(song, ServerStream(serverUri!!, null, 128), "audio/mpeg")),
            answered
        )
    }

    @Test
    fun youTubeStreamIsKeptUntilItExpires() {
        val first = resolve(Origin.YOUTUBE, "v1")
        assertEquals("https://rr1.googlevideo.com/videoplayback?c=VISIONOS", first.uri.toString())
        assertEquals("VISIONOS", first.httpRequestHeaders["User-Agent"])
        assertEquals(QueuedSong(Origin.YOUTUBE, "v1", "VISIONOS"), first.customData)

        resolve(Origin.YOUTUBE, "v1")
        assertEquals(1, asked.size)
        // Only a new stream is passed on
        assertEquals(listOf(QueuedSong(Origin.YOUTUBE, "v1", "VISIONOS") to "251"), beforeRead)

        answer = { stream("VISIONOS", expiresAt = now + 5.hours) }
        resolve(Origin.YOUTUBE, "v2")
        // Close to expiring, asked again
        answer = { stream("VISIONOS", expiresAt = now) }
        resolve(Origin.YOUTUBE, "v3")
        resolve(Origin.YOUTUBE, "v3")
        assertEquals(4, asked.size)
    }

    @Test
    fun streamIsAskedAgainInAnotherQuality() {
        resolve(Origin.YOUTUBE, "v1", position = 0)
        resolve(Origin.YOUTUBE, "v1", position = 0)
        quality = StreamQuality.LOW
        // A seek goes on with the file of the song's start
        resolve(Origin.YOUTUBE, "v1")
        // Played again from the start
        resolve(Origin.YOUTUBE, "v1", position = 0)
        resolve(Origin.YOUTUBE, "v1")

        assertEquals(listOf(StreamQuality.HIGH, StreamQuality.LOW), qualities)
    }

    @Test
    fun readThroughTheCacheIsInTheQualityOfItsCopy() {
        quality = StreamQuality.LOW
        val copy = QueuedSong(Origin.YOUTUBE, "v1", quality = StreamQuality.HIGH)
        val read = resolver.resolveDataSpec(
            spec(Origin.YOUTUBE, "v1", position = 0).buildUpon().setCustomData(copy).build()
        )
        val server = resolver.resolveDataSpec(
            spec(Origin.OPEN_SUBSONIC, "s1", position = 0).buildUpon()
                .setCustomData(
                    QueuedSong(
                        Origin.OPEN_SUBSONIC,
                        "s1",
                        quality = StreamQuality.MEDIUM
                    )
                )
                .build()
        )

        assertEquals(listOf(StreamQuality.HIGH), qualities)
        assertEquals(listOf(StreamQuality.MEDIUM), serverAsked)
        // The copy is passed on, the cache and the next reads find it
        assertEquals(copy.copy(client = "VISIONOS"), read.customData)
        assertEquals(StreamQuality.MEDIUM, (server.customData as QueuedSong).quality)
    }

    @Test
    fun cachedPartsOfACopyGetAStreamOfTheSameFile() {
        val copy = QueuedSong(Origin.YOUTUBE, "v1", quality = StreamQuality.HIGH)
        cached[copy.cacheKey] = 2_000
        answer = { refused ->
            stream(
                if ("VISIONOS" in refused) "WEB_REMIX" else "VISIONOS",
                contentLength = if ("VISIONOS" in refused) 2_000 else 3_000
            )
        }
        val read = resolver.resolveDataSpec(
            spec(Origin.YOUTUBE, "v1").buildUpon().setCustomData(copy).build()
        )

        assertEquals("WEB_REMIX", (read.customData as QueuedSong).client)
        // Other copies have their own parts
        assertEquals(2_000L, cached[copy.cacheKey])
    }

    @Test
    fun refusedClientIsLeftOutOfTheNextRequest() {
        answer = { refused -> stream(if ("VISIONOS" in refused) "WEB_REMIX" else "VISIONOS") }
        val first = resolve(Origin.YOUTUBE, "v1")

        resolver.refused(first.customData as QueuedSong)
        val second = resolve(Origin.YOUTUBE, "v1")

        assertEquals(listOf(emptySet(), setOf("VISIONOS")), asked)
        assertEquals("WEB_REMIX", (second.customData as QueuedSong).client)
    }

    @Test
    fun refusalOfAnOlderStreamIsIgnored() {
        val first = resolve(Origin.YOUTUBE, "v1").customData as QueuedSong
        resolver.refused(first.copy(client = "IOS"))
        resolve(Origin.YOUTUBE, "v1")
        assertEquals(1, asked.size)
    }

    @Test
    fun streamReadOneRangeAtATimeIsSkipped() {
        answer = { refused ->
            if ("ANDROID_VR" in refused) stream("WEB_REMIX")
            else stream("ANDROID_VR", rangeSize = 1_000_000)
        }
        assertEquals("WEB_REMIX", (resolve(Origin.YOUTUBE, "v1").customData as QueuedSong).client)
        assertEquals(listOf(emptySet(), setOf("ANDROID_VR")), asked)
    }

    @Test
    fun songWithoutStreamFailsAndStartsOverNextTime() {
        answer = { refused -> if (refused.isEmpty()) stream("VISIONOS") else null }
        resolver.refused(resolve(Origin.YOUTUBE, "v1").customData as QueuedSong)

        assertEquals(ERROR_CODE_NOT_PLAYABLE, failure(Origin.YOUTUBE, "v1"))
        resolve(Origin.YOUTUBE, "v1")
        assertEquals(listOf(emptySet(), setOf("VISIONOS"), emptySet()), asked)
    }

    @Test
    fun songWithCachedPartsGetsAStreamOfTheSameFile() {
        cached[QueuedSong(Origin.YOUTUBE, "v1").cacheKey] = 2_000
        answer = { refused ->
            if ("VISIONOS" in refused) stream("WEB_REMIX", contentLength = 2_000)
            else stream("VISIONOS", contentLength = 3_000)
        }

        assertEquals("WEB_REMIX", (resolve(Origin.YOUTUBE, "v1").customData as QueuedSong).client)
        assertEquals(listOf(emptySet(), setOf("VISIONOS")), asked)
        assertEquals(2_000L, cached.values.single())

        // A stream of another file isn't a refusal, the client is asked again next time
        resolver.refused(QueuedSong(Origin.YOUTUBE, "v1", "WEB_REMIX"))
        resolve(Origin.YOUTUBE, "v1")
        assertEquals(setOf("WEB_REMIX", "VISIONOS"), asked.last())
    }

    @Test
    fun cachedPartsNoClientHasAreDropped() {
        val key = QueuedSong(Origin.YOUTUBE, "v1").cacheKey
        cached[key] = 2_000
        answer = { refused ->
            if (refused.isEmpty()) stream(
                "VISIONOS",
                contentLength = 3_000
            ) else null
        }

        assertEquals("VISIONOS", (resolve(Origin.YOUTUBE, "v1").customData as QueuedSong).client)
        assertNull(cached[key])
    }

    @Test
    fun youTubeFailuresBecomePlaybackErrors() {
        answer =
            { throw YouTubeException("Couldn't reach YouTube", cause = IOException("offline")) }
        assertEquals(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            failure(Origin.YOUTUBE, "v1"),
        )

        answer = { throw YouTubeException("YouTube answered 429", status = 429) }
        assertEquals(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, failure(Origin.YOUTUBE, "v1"))
    }
}
