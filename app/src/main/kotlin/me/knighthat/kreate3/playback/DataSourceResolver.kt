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
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import me.knighthat.kreate3.constant.StreamQuality
import me.knighthat.kreate3.database.model.SongFormat
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.playback.DataSourceResolver.Companion.SERVER_ADDRESS_LIFE
import me.knighthat.kreate3.provider.Providers
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.opensubsonic.ServerStream
import me.knighthat.kreate3.provider.youtube.YouTubeException
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.provider.youtube.YouTubeStream
import me.knighthat.kreate3.ui.component.origin
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * A song of the queue, which [DataSourceResolver] finds the address of when the player reads it.
 *
 * @property origin where the song comes from
 * @property id the song's id at its origin, the `mediaId` of its item
 * @property client the YouTube client that found the address, `null` for other origins
 * @property quality the copy of the song read through the cache, see [CacheCopies]. `null` for a
 * song that isn't, which is read in the quality it is set to.
 */
data class QueuedSong(
    val origin: Origin,
    val id: String,
    val client: String? = null,
    val quality: StreamQuality? = null,
) {

    /** The address the item is queued with, see [withSongUri]. */
    val uri: Uri
        get() = Uri.Builder().scheme(SCHEME).authority(origin.name).appendPath(id).build()

    /**
     * What the song is stored under in [SongCaches], a download included: each [quality] is a
     * copy of its own, like `kreate://YOUTUBE/<id>#HIGH`.
     */
    val cacheKey: String
        get() = quality?.let { "$uri#${it.name}" } ?: uri.toString()

    /** The song itself, without the copy or the client it is read with. */
    val song: QueuedSong
        get() = QueuedSong(origin, id)

    companion object {
        private const val SCHEME = "kreate"

        /** @return `null` when [uri] isn't the address of a queued song */
        fun of(uri: Uri): QueuedSong? {
            if (uri.scheme != SCHEME) return null
            val origin = Origin.entries.firstOrNull { it.name == uri.authority } ?: return null
            val id = uri.pathSegments.singleOrNull() ?: return null
            return QueuedSong(origin, id)
        }

        /** @return the copy [key] is the [cacheKey] of, `null` when it isn't one */
        fun ofCacheKey(key: String): QueuedSong? {
            val uri = key.toUri()
            val song = of(uri) ?: return null
            return song.copy(quality = StreamQuality.entries.firstOrNull { it.name == uri.fragment })
        }
    }
}

/**
 * The item with the address of its song, which the player needs to queue it. Items carry no
 * address of their own, it is only found when the song is read, see [DataSourceResolver].
 *
 * @return `null` when the item doesn't say where it comes from
 */
fun MediaItem.withSongUri(): MediaItem? {
    if (localConfiguration != null) return this
    val origin = origin ?: return null
    return buildUpon().setUri(QueuedSong(origin, mediaId).uri).build()
}

/**
 * Finds the address the player reads a song from, from the address it is queued with
 * ([withSongUri]), by asking the song's provider. It runs each time the player opens the song,
 * like after a seek, on the player's loading thread. Other addresses are left as they are.
 *
 * A read through the cache is in the quality of the copy [CacheCopies] picked, which it carries
 * as its `customData`. Any other read is in the [quality] the song is set to, and goes on in the
 * quality it started in: a read past its start, like after a seek, is of the same file.
 *
 * YouTube's addresses take a request and expire, so they are kept until they do, or until the
 * quality changes. When YouTube refuses one, [refused] makes the next read ask another client.
 * The server's address is used again for [SERVER_ADDRESS_LIFE], once it answered, [answered]
 * says with what. The resolved [DataSpec] carries the song as its `customData`.
 *
 * It only runs for what the caches don't have, see [songDataSourceFactory]. A YouTube song with
 * parts in the cache gets a stream of the same size, the parts of another file would garble it.
 *
 * @param beforeRead runs before an address is given, like [SongFormatRecorder.record], with the
 * format of the stream when a new one was found and its format is known. The player doesn't
 * start until it returns.
 * @param cachedLength size of the file the cache has parts of, see [SongCaches.cachedLength]
 * @param dropCached drops what the cache has of a song, see [SongCaches.drop]
 * @param quality the quality the songs of an origin are streamed in now, read at each read
 * @param serverStream what to stream of a song of the OpenSubsonic server in a quality, see
 * [OpenSubsonicProvider.stream]
 * @param answered runs once the server answered a stream of a song whose format it didn't say
 * before, with the media type it answered, like [SongFormatRecorder.recordAnswered]
 */
@OptIn(UnstableApi::class)
class DataSourceResolver internal constructor(
    private val providers: Providers,
    private val youTubeStream: suspend (
        songId: String,
        refused: Set<String>,
        quality: StreamQuality,
    ) -> YouTubeStream?,
    private val beforeRead: suspend (QueuedSong, SongFormat?) -> Unit = { _, _ -> },
    private val cachedLength: (QueuedSong) -> Long? = { null },
    private val dropCached: (QueuedSong) -> Unit = {},
    private val clock: Clock = Clock.System,
    private val quality: suspend (Origin) -> StreamQuality = { StreamQuality.HIGH },
    private val serverStream: suspend (songId: String, quality: StreamQuality) -> ServerStream? =
        { songId, _ ->
            providers[Origin.OPEN_SUBSONIC].playbackUri(songId)
                ?.let { ServerStream(it, null, null) }
        },
    private val answered: suspend (QueuedSong, ServerStream, String?) -> Unit = { _, _, _ -> },
) : ResolvingDataSource.Resolver {

    constructor(
        providers: Providers,
        youTube: YouTubeProvider,
        openSubsonic: OpenSubsonicProvider,
        formats: SongFormatRecorder,
        caches: SongCaches,
        quality: suspend (Origin) -> StreamQuality,
    ) : this(
        providers,
        youTube::stream,
        formats::record,
        caches::cachedLength,
        caches::drop,
        Clock.System,
        quality,
        openSubsonic::stream,
        formats::recordAnswered,
    )

    /** What is known of YouTube songs, the least recently read first. */
    private val youTubeSongs = object : LinkedHashMap<String, YouTubeSong>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, YouTubeSong>) =
            size > MAX_KNOWN_SONGS
    }

    /** What the songs of the server were last read with, by id, the least recent first. */
    private val serverSongs = object : LinkedHashMap<String, ServerSong>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ServerSong>) =
            size > MAX_KNOWN_SONGS
    }

    /**
     * @param stream the address to read, `null` when there is none to use yet
     * @param refused the clients whose addresses YouTube refused for the song
     * @param quality what [stream] was asked in
     */
    private data class YouTubeSong(
        val stream: YouTubeStream?,
        val refused: Set<String>,
        val quality: StreamQuality,
    )

    /**
     * @param quality what [stream] was asked in
     * @param askedAt when [stream] was asked
     * @param answered whether the server answered [stream], see [answered]
     */
    private data class ServerSong(
        val stream: ServerStream,
        val quality: StreamQuality,
        val askedAt: Instant,
        val answered: Boolean = false,
    )

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val queued = QueuedSong.of(dataSpec.uri) ?: return dataSpec
        // The copy of the cache it is read for
        val song = queued.copy(quality = (dataSpec.customData as? QueuedSong)?.quality)

        if (song.origin == Origin.YOUTUBE) {
            val stream = streamOf(song, dataSpec.position)
            return dataSpec.buildUpon()
                .setUri(stream.url.toUri())
                .setHttpRequestHeaders(dataSpec.httpRequestHeaders + stream.headers)
                .setCustomData(song.copy(client = stream.client))
                .build()
        }

        if (song.origin == Origin.OPEN_SUBSONIC) {
            val (stream, isNew) = serverStreamOf(song, dataSpec.position)
                ?: throw unavailable(song.origin)
            runBlocking { beforeRead(song, stream.format?.takeIf { isNew }) }
            return dataSpec.buildUpon().setUri(stream.uri).setCustomData(song).build()
        }

        val uri = ask(song.origin) { providers[song.origin].playbackUri(song.id) }
            ?: throw unavailable(song.origin)
        runBlocking { beforeRead(song, null) }
        return dataSpec.buildUpon().setUri(uri).setCustomData(song).build()
    }

    /**
     * The server answered the stream of [song] with [contentType], the media type of what it
     * sends. Only the first answer to a stream counts.
     */
    fun answered(song: QueuedSong, contentType: String?) {
        val stream = synchronized(serverSongs) {
            val known = serverSongs[song.id]?.takeUnless { it.answered } ?: return
            serverSongs[song.id] = known.copy(answered = true)
            known.stream
        }
        // The format is known already, or nothing was converted
        if (stream.format != null || stream.maxBitRate == null) return
        runBlocking { answered(song, stream, contentType) }
    }

    /**
     * YouTube refused the address it gave for [song], the next read asks without its client.
     */
    fun refused(song: QueuedSong) {
        val client = song.client ?: return
        synchronized(youTubeSongs) {
            val known = youTubeSongs[song.id] ?: return
            if (known.stream?.client != client) return
            youTubeSongs[song.id] = YouTubeSong(null, known.refused + client, known.quality)
        }
    }

    private fun streamOf(song: QueuedSong, position: Long): YouTubeStream {
        val songId = song.id
        val known = synchronized(youTubeSongs) { youTubeSongs[songId] }
        val quality = qualityOf(song, position, known?.quality)
        known?.stream?.takeIf { it.isFresh() && known.quality == quality }?.let { return it }
        val cached = cachedLength(song)

        var refused = known?.refused.orEmpty()
        // Clients with a stream of another file than the cached one, they aren't refused
        var otherFile = emptySet<String>()
        var firstOtherFile: YouTubeStream? = null
        for (attempt in 1..MAX_YOUTUBE_ASKS) {
            val stream = ask(Origin.YOUTUBE) { youTubeStream(songId, refused + otherFile, quality) }
                ?: break
            // The player reads to the end in one request, a client that answers only a range
            // at a time would stop it after the first one
            if (stream.rangeSize != null) {
                refused = refused + stream.client
                continue
            }
            if (cached != null && stream.contentLength != null && stream.contentLength != cached) {
                otherFile = otherFile + stream.client
                firstOtherFile = firstOtherFile ?: stream
                continue
            }
            return keep(song, stream, refused, quality)
        }

        firstOtherFile?.let {
            // No client has the cached file any more, the song is cached again from the start
            dropCached(song)
            return keep(song, it, refused, quality)
        }

        // The next try starts again with every client, a refusal may not last
        synchronized(youTubeSongs) { youTubeSongs.remove(songId) }
        throw unavailable(Origin.YOUTUBE)
    }

    /**
     * What to stream of [song] of the server, read from [position], with whether it was just
     * asked. Past the start it is in the quality the song was read in before, another one would
     * be another file.
     */
    private fun serverStreamOf(song: QueuedSong, position: Long): Pair<ServerStream, Boolean>? {
        val known = synchronized(serverSongs) { serverSongs[song.id] }
        val quality = qualityOf(song, position, known?.quality)
        // A seek reads what was asked when the song started, a converted one is the same file
        known?.takeIf {
            position > 0 && it.quality == quality && clock.now() < it.askedAt + SERVER_ADDRESS_LIFE
        }?.let { return it.stream to false }

        val stream = ask(Origin.OPEN_SUBSONIC) { serverStream(song.id, quality) } ?: return null
        synchronized(serverSongs) {
            serverSongs[song.id] = ServerSong(stream, quality, clock.now())
        }
        return stream to true
    }

    /**
     * The quality to read [song] in from [position]: the one of the cache's copy it is read for,
     * or past the start the quality it was read in [before], or the one it is set to.
     */
    private fun qualityOf(song: QueuedSong, position: Long, before: StreamQuality?): StreamQuality =
        song.quality
            ?: before?.takeIf { position > 0 }
            ?: runBlocking { quality(song.origin) }

    private fun keep(
        song: QueuedSong,
        stream: YouTubeStream,
        refused: Set<String>,
        quality: StreamQuality,
    ): YouTubeStream {
        synchronized(youTubeSongs) { youTubeSongs[song.id] = YouTubeSong(stream, refused, quality) }
        runBlocking { beforeRead(song.copy(client = stream.client), stream.toSongFormat(song.id)) }
        return stream
    }

    private fun YouTubeStream.isFresh(): Boolean =
        expiresAt?.let { clock.now() < it - EXPIRY_MARGIN } ?: true

    /**
     * Runs [block] on this thread, the player waits for the address anyway.
     *
     * @throws DataSourceException when the provider failed
     */
    private fun <T> ask(origin: Origin, block: suspend () -> T): T =
        try {
            runBlocking { block() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw e
        } catch (e: YouTubeException) {
            throw DataSourceException(e.message, e, e.errorCode())
        } catch (e: Exception) {
            throw DataSourceException(
                "Couldn't find where to read the song of $origin",
                e,
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            )
        }

    private fun YouTubeException.errorCode(): Int = when {
        status != null -> PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        generateSequence<Throwable>(this) { it.cause }.any { it is IOException } ->
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED

        else -> PlaybackException.ERROR_CODE_IO_UNSPECIFIED
    }

    private fun unavailable(origin: Origin): DataSourceException = when (origin) {
        Origin.LOCAL -> DataSourceException(
            "The song isn't in the media index",
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        )

        // The server is off, has no address or no sign in
        Origin.OPEN_SUBSONIC -> DataSourceException(
            "The OpenSubsonic server isn't set up",
            ERROR_CODE_SOURCE_OFF,
        )

        Origin.YOUTUBE -> DataSourceException(
            "YouTube has nothing to play for the song",
            ERROR_CODE_NOT_PLAYABLE,
        )
    }

    private companion object {
        /** Songs kept of YouTube, and of the server, each about a long queue. */
        const val MAX_KNOWN_SONGS = 200

        /** Times YouTube is asked for one read, each without the clients that were left out. */
        const val MAX_YOUTUBE_ASKS = 3

        /** An address this close to expiring is asked again, a song may take that long to read. */
        val EXPIRY_MARGIN = 10.minutes

        /**
         * How long a seek reads the address the server gave when the song started. The address
         * of a converted song may expire, the server only says it lasts a while.
         */
        val SERVER_ADDRESS_LIFE = 1.hours
    }
}
