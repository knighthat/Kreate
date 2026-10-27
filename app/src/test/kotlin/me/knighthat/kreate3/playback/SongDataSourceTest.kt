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

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import me.knighthat.kreate3.constant.StreamQuality
import me.knighthat.kreate3.database.model.Album
import me.knighthat.kreate3.database.model.Artist
import me.knighthat.kreate3.database.model.Song
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.provider.Provider
import me.knighthat.kreate3.provider.Providers
import me.knighthat.kreate3.provider.SearchResults
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Where songs are read from: downloads, then the cache, then their address. Songs are set to
 * [StreamQuality.HIGH], so what is streamed through the cache is its HIGH copy.
 */
@RunWith(RobolectricTestRunner::class)
class SongDataSourceTest {

    private class FakeProvider(override val origin: Origin, val uri: () -> Uri?) : Provider {
        /** Times [playbackUri] was asked. */
        var asked = 0

        override suspend fun search(query: String, limit: Int, offset: Int): SearchResults = TODO()
        override suspend fun song(songId: String): Song? = TODO()
        override suspend fun album(albumId: String): Album? = TODO()
        override suspend fun artist(artistId: String): Artist? = TODO()
        override suspend fun songsOf(albumId: String): List<Song> = TODO()
        override suspend fun albumsOf(artistId: String): List<Album> = TODO()
        override suspend fun playbackUri(songId: String): Uri? = uri().also { asked++ }
        override fun artworkUri(artworkId: String, size: Int?): Uri? = TODO()
    }

    private val context: Context = RuntimeEnvironment.getApplication()

    private val audio = ByteArray(64 * 1024) { it.toByte() }
    private val file = File(context.filesDir, "song.bin").apply { writeBytes(audio) }

    /** The song's address, `null` for a source that is off. */
    private var address: Uri? = Uri.fromFile(file)

    private val local = FakeProvider(Origin.LOCAL) { address }
    private val server = FakeProvider(Origin.OPEN_SUBSONIC) { address }

    /** Whether songs of the server skip the cache. */
    private var directPlay = false

    private val caches = SongCaches(context, 1024 * 1024)

    private val factory = songDataSourceFactory(
        context = context,
        client = OkHttpClient(),
        openSubsonic = OkHttpClient(),
        youTube = OkHttpClient(),
        resolver = DataSourceResolver(
            Providers(listOf(local, server, FakeProvider(Origin.YOUTUBE) { null })),
            { _, _, _ -> null },
        ),
        caches = caches,
        copies = CacheCopies(caches::copies, caches::drop, { StreamQuality.HIGH }, { true }),
        directPlay = { directPlay },
    )

    @After
    fun release() {
        caches.cache.release()
        caches.downloads.release()
    }

    private fun read(song: QueuedSong): ByteArray {
        val source = factory.createDataSource()
        val out = ByteArrayOutputStream()
        try {
            source.open(DataSpec(song.uri))
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val read = source.read(buffer, 0, buffer.size)
                if (read == C.RESULT_END_OF_INPUT) break
                out.write(buffer, 0, read)
            }
        } finally {
            source.close()
        }
        return out.toByteArray()
    }

    /** Bytes [cache] keeps of [song], of its HIGH copy unless [quality] says otherwise. */
    private fun cachedIn(
        cache: Cache,
        song: QueuedSong,
        quality: StreamQuality? = StreamQuality.HIGH,
    ): Long =
        cache.getCachedBytes(song.copy(quality = quality).cacheKey, 0, C.LENGTH_UNSET.toLong())

    @Test
    fun streamedSongIsKeptInTheCacheNotTheDownloads() {
        val song = QueuedSong(Origin.OPEN_SUBSONIC, "s1")

        assertArrayEquals(audio, read(song))
        assertEquals(audio.size.toLong(), cachedIn(caches.cache, song))
        assertEquals(0L, cachedIn(caches.downloads, song))
    }

    @Test
    fun serverSongSkipsTheCacheWithDirectPlay() {
        directPlay = true
        val song = QueuedSong(Origin.OPEN_SUBSONIC, "s1")

        assertArrayEquals(audio, read(song))
        assertEquals(0L, cachedIn(caches.cache, song))
        assertEquals(0L, cachedIn(caches.cache, song, quality = null))

        // Asked again every time
        read(song)
        assertEquals(2, server.asked)
    }

    @Test
    fun cachedSongIsReadWithoutAskingForItsAddress() {
        val song = QueuedSong(Origin.OPEN_SUBSONIC, "s1")
        read(song)

        // The server is off now
        address = null
        assertArrayEquals(audio, read(song))
        assertEquals(1, server.asked)
    }

    @Test
    fun downloadedSongIsReadFromTheDownloads() {
        val song = QueuedSong(Origin.OPEN_SUBSONIC, "s1")
        CacheWriter(
            CacheDataSource(caches.downloads, FileDataSource()),
            DataSpec.Builder()
                .setUri(Uri.fromFile(file))
                .setKey(song.copy(quality = StreamQuality.HIGH).cacheKey)
                .build(),
            null,
            null,
        ).cache()

        address = null
        assertArrayEquals(audio, read(song))
        assertEquals(0, server.asked)
        // Nothing is copied to the cache
        assertEquals(0L, cachedIn(caches.cache, song))
    }

    /** Writes [bytes] to [cache] as the [quality] copy of [song]. */
    private fun store(cache: Cache, song: QueuedSong, quality: StreamQuality, bytes: ByteArray) {
        val source =
            File(context.filesDir, "stored-${quality.name}.bin").apply { writeBytes(bytes) }
        CacheWriter(
            CacheDataSource(cache, FileDataSource()),
            DataSpec.Builder()
                .setUri(Uri.fromFile(source))
                .setKey(song.copy(quality = quality).cacheKey)
                .build(),
            null,
            null,
        ).cache()
    }

    @Test
    fun downloadWinsOverTheCacheWhateverItsQuality() {
        val song = QueuedSong(Origin.OPEN_SUBSONIC, "s1")
        val downloaded = audio.reversedArray()
        store(caches.cache, song, StreamQuality.HIGH, audio)
        store(caches.downloads, song, StreamQuality.LOW, downloaded)

        assertArrayEquals(downloaded, read(song))
        assertEquals(0, server.asked)
    }

    @Test
    fun downloadedSongIsReadFromTheDownloadsWithDirectPlay() {
        directPlay = true
        val song = QueuedSong(Origin.OPEN_SUBSONIC, "s1")
        store(caches.downloads, song, StreamQuality.LOW, audio)

        address = null
        assertArrayEquals(audio, read(song))
        assertEquals(0, server.asked)
    }

    @Test
    fun playbackNeverWritesTheDownloads() {
        val song = QueuedSong(Origin.OPEN_SUBSONIC, "s1")
        // Half downloaded, like while the downloader runs
        store(caches.downloads, song, StreamQuality.HIGH, audio.copyOf(audio.size / 2))
        val before = cachedIn(caches.downloads, song)

        assertArrayEquals(audio.copyOf(audio.size / 2), read(song).copyOf(audio.size / 2))
        assertEquals(before, cachedIn(caches.downloads, song))
    }

    @Test
    fun readOnlyCacheRefusesWrites() {
        val cache = ReadOnlyCache(caches.downloads)
        val key = QueuedSong(Origin.OPEN_SUBSONIC, "s1").cacheKey

        assertThrows(UnsupportedOperationException::class.java) { cache.startFile(key, 0, 1) }
        assertThrows(UnsupportedOperationException::class.java) { cache.removeResource(key) }
        assertThrows(UnsupportedOperationException::class.java) { cache.release() }
    }

    /** Downloads [song] in [quality] the way the downloader does. */
    private fun download(song: QueuedSong, quality: StreamQuality) {
        val upstream = downloadDataSourceFactory(
            context = context,
            client = OkHttpClient(),
            openSubsonic = OkHttpClient(),
            youTube = OkHttpClient(),
            resolver = DataSourceResolver(
                Providers(listOf(local, server, FakeProvider(Origin.YOUTUBE) { null })),
                { _, _, _ -> null },
            ),
            caches = caches,
        )
        CacheWriter(
            CacheDataSource(caches.downloads, upstream.createDataSource()),
            DataSpec.Builder()
                .setUri(song.uri)
                .setKey(song.copy(quality = quality).cacheKey)
                .build(),
            null,
            null,
        ).cache()
    }

    @Test
    fun downloadMovesTheCachedCopyWithoutStreaming() {
        val song = QueuedSong(Origin.OPEN_SUBSONIC, "s1")
        store(caches.cache, song, StreamQuality.HIGH, audio)

        address = null
        download(song, StreamQuality.HIGH)
        assertEquals(0, server.asked)
        assertEquals(audio.size.toLong(), cachedIn(caches.downloads, song))
        assertEquals(StreamQuality.HIGH, caches.downloaded(song))
    }

    @Test
    fun downloadStreamsWhatTheCacheLacksWithoutCachingIt() {
        val song = QueuedSong(Origin.OPEN_SUBSONIC, "s1")

        download(song, StreamQuality.HIGH)
        assertEquals(1, server.asked)
        assertEquals(audio.size.toLong(), cachedIn(caches.downloads, song))
        assertEquals(0L, cachedIn(caches.cache, song))
    }

    @Test
    fun songOnTheDeviceIsNeverCached() {
        val song = QueuedSong(Origin.LOCAL, "42")

        assertArrayEquals(audio, read(song))
        assertEquals(0L, cachedIn(caches.cache, song, quality = null))
        assertEquals(0L, cachedIn(caches.downloads, song, quality = null))

        // Read where it is every time
        read(song)
        assertEquals(2, local.asked)
    }

    @Test
    fun songNeitherCachedNorReachableFails() {
        address = null
        assertThrows(DataSourceException::class.java) {
            read(QueuedSong(Origin.OPEN_SUBSONIC, "s1"))
        }
    }
}
