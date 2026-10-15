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
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.provider.Providers
import me.knighthat.kreate3.provider.youtube.YouTubeException
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.provider.youtube.YouTubeStream
import me.knighthat.kreate3.ui.component.origin
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/**
 * A song of the queue, which [DataSourceResolver] finds the address of when the player reads it.
 *
 * @property origin where the song comes from
 * @property id the song's id at its origin, the `mediaId` of its item
 * @property client the YouTube client that found the address, `null` for other origins
 */
data class QueuedSong(val origin: Origin, val id: String, val client: String? = null) {

    /** The address the item is queued with, see [withSongUri]. */
    val uri: Uri
        get() = Uri.Builder().scheme(SCHEME).authority(origin.name).appendPath(id).build()

    /** What the song is stored under in [SongCaches], a download included. */
    val cacheKey: String
        get() = uri.toString()

    companion object {
        private const val SCHEME = "kreate"

        /** @return `null` when [uri] isn't the address of a queued song */
        fun of(uri: Uri): QueuedSong? {
            if (uri.scheme != SCHEME) return null
            val origin = Origin.entries.firstOrNull { it.name == uri.authority } ?: return null
            val id = uri.pathSegments.singleOrNull() ?: return null
            return QueuedSong(origin, id)
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
 * YouTube's addresses take a request and expire, so they are kept until they do. When YouTube
 * refuses one, [refused] makes the next read ask another client. The resolved [DataSpec] carries
 * the song as its `customData`.
 *
 * It only runs for what the caches don't have, see [songDataSourceFactory]. A YouTube song with
 * parts in the cache gets a stream of the same size, the parts of another file would garble it.
 *
 * @param beforeRead runs before an address is given, like [SongFormatRecorder.record], with the
 * YouTube stream when a new one was found. The player doesn't start until it returns.
 * @param cachedLength size of the file the cache has parts of, see [SongCaches.cachedLength]
 * @param dropCached drops what the cache has of a song, see [SongCaches.drop]
 */
@OptIn(UnstableApi::class)
class DataSourceResolver internal constructor(
    private val providers: Providers,
    private val youTubeStream: suspend (songId: String, refused: Set<String>) -> YouTubeStream?,
    private val beforeRead: suspend (QueuedSong, YouTubeStream?) -> Unit = { _, _ -> },
    private val cachedLength: (QueuedSong) -> Long? = { null },
    private val dropCached: (QueuedSong) -> Unit = {},
    private val clock: Clock = Clock.System,
) : ResolvingDataSource.Resolver {

    constructor(
        providers: Providers,
        youTube: YouTubeProvider,
        formats: SongFormatRecorder,
        caches: SongCaches,
    ) : this(providers, youTube::stream, formats::record, caches::cachedLength, caches::drop)

    /** What is known of YouTube songs, the least recently read first. */
    private val youTubeSongs = object : LinkedHashMap<String, YouTubeSong>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, YouTubeSong>) =
            size > MAX_YOUTUBE_SONGS
    }

    /**
     * @param stream the address to read, `null` when there is none to use yet
     * @param refused the clients whose addresses YouTube refused for the song
     */
    private data class YouTubeSong(val stream: YouTubeStream?, val refused: Set<String>)

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val song = QueuedSong.of(dataSpec.uri) ?: return dataSpec

        if (song.origin == Origin.YOUTUBE) {
            val stream = streamOf(song.id)
            return dataSpec.buildUpon()
                .setUri(stream.url.toUri())
                .setHttpRequestHeaders(dataSpec.httpRequestHeaders + stream.headers)
                .setCustomData(song.copy(client = stream.client))
                .build()
        }

        val uri = ask(song.origin) { providers[song.origin].playbackUri(song.id) }
            ?: throw unavailable(song.origin)
        runBlocking { beforeRead(song, null) }
        return dataSpec.buildUpon().setUri(uri).setCustomData(song).build()
    }

    /**
     * YouTube refused the address it gave for [song], the next read asks without its client.
     */
    fun refused(song: QueuedSong) {
        val client = song.client ?: return
        synchronized(youTubeSongs) {
            val known = youTubeSongs[song.id] ?: return
            if (known.stream?.client != client) return
            youTubeSongs[song.id] = YouTubeSong(null, known.refused + client)
        }
    }

    private fun streamOf(songId: String): YouTubeStream {
        val known = synchronized(youTubeSongs) { youTubeSongs[songId] }
        known?.stream?.takeIf { it.isFresh() }?.let { return it }

        var refused = known?.refused.orEmpty()
        val cached = cachedLength(QueuedSong(Origin.YOUTUBE, songId))
        // Clients with a stream of another file than the cached one, they aren't refused
        var otherFile = emptySet<String>()
        var firstOtherFile: YouTubeStream? = null
        for (attempt in 1..MAX_YOUTUBE_ASKS) {
            val stream = ask(Origin.YOUTUBE) { youTubeStream(songId, refused + otherFile) } ?: break
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
            return keep(songId, stream, refused)
        }

        firstOtherFile?.let {
            // No client has the cached file any more, the song is cached again from the start
            dropCached(QueuedSong(Origin.YOUTUBE, songId))
            return keep(songId, it, refused)
        }

        // The next try starts again with every client, a refusal may not last
        synchronized(youTubeSongs) { youTubeSongs.remove(songId) }
        throw unavailable(Origin.YOUTUBE)
    }

    private fun keep(songId: String, stream: YouTubeStream, refused: Set<String>): YouTubeStream {
        synchronized(youTubeSongs) { youTubeSongs[songId] = YouTubeSong(stream, refused) }
        runBlocking { beforeRead(QueuedSong(Origin.YOUTUBE, songId, stream.client), stream) }
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
        /** YouTube songs kept, about a long queue. */
        const val MAX_YOUTUBE_SONGS = 200

        /** Times YouTube is asked for one read, each without the clients that were left out. */
        const val MAX_YOUTUBE_ASKS = 3

        /** An address this close to expiring is asked again, a song may take that long to read. */
        val EXPIRY_MARGIN = 10.minutes
    }
}
