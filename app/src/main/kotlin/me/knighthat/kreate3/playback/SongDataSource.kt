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
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.okhttp.OkHttpDataSource
import me.knighthat.kreate3.constant.StreamQuality
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import okhttp3.Call
import okhttp3.OkHttpClient

/**
 * Reads the songs of the queue. A song on the device is read where it is, no cache is involved.
 * A downloaded song is read from its download, whatever quality the song is set to now. Any
 * other song is read from [SongCaches.downloads] first, then from [SongCaches.cache], and
 * only what neither has is streamed: [resolver] finds the address then, and what is streamed is
 * kept in [SongCaches.cache], never in the downloads. With [directPlay] on, a song of the server
 * skips [SongCaches.cache]: the downloads, then the stream.
 *
 * [SongCaches.cache] keeps a copy of a song for each quality, [copies] picks the one read.
 *
 * Streams go out with the client of their source, [openSubsonic] or [youTube], the app's [client]
 * for any other address.
 *
 * @param openSubsonic the client of the server, see [OpenSubsonicProvider.streamClient]
 * @param youTube the client of YouTube, see [YouTubeProvider.streamClient]
 * @param directPlay whether songs of the server skip the cache now, read at each read, see
 * [Preferences.OPEN_SUBSONIC_DIRECT_PLAY]
 */
@OptIn(UnstableApi::class)
fun songDataSourceFactory(
    context: Context,
    client: OkHttpClient,
    openSubsonic: Call.Factory,
    youTube: Call.Factory,
    resolver: DataSourceResolver,
    caches: SongCaches,
    copies: CacheCopies,
    directPlay: () -> Boolean = { Preferences.OPEN_SUBSONIC_DIRECT_PLAY.value },
): DataSource.Factory {
    val network = streamDataSourceFactory(context, client, openSubsonic, youTube, resolver)

    // Read only, downloads are written by the downloader: any write would throw
    val downloadsCache = ReadOnlyCache(caches.downloads)
    fun downloads(upstream: DataSource.Factory) = CacheDataSource.Factory()
        .setCache(downloadsCache)
        .setCacheKeyFactory(keys)
        .setCacheWriteDataSinkFactory(null)
        .setUpstreamDataSourceFactory(upstream)

    val cached = downloads(
        CacheDataSource.Factory()
            .setCache(caches.cache)
            .setCacheKeyFactory(keys)
            // A cache that can't be read is skipped, the song is streamed instead
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .setUpstreamDataSourceFactory(network)
    )
    val uncached = downloads(network)

    return DataSource.Factory {
        SongDataSource(
            cached = cached.createDataSource(),
            uncached = uncached.createDataSource(),
            direct = network.createDataSource(),
            copies = copies,
            downloaded = caches::downloaded,
            directPlay = directPlay,
        )
    }
}

/**
 * Reads the songs the downloader writes to [SongCaches.downloads], each in the quality of its
 * download (the copy its cache key names, see [QueuedSong.cacheKey]). A copy [SongCaches.cache]
 * has whole is read from there without streaming, which moves a cached song to the downloads;
 * what is streamed isn't kept in the cache.
 *
 * The clients are those of [songDataSourceFactory].
 */
@OptIn(UnstableApi::class)
fun downloadDataSourceFactory(
    context: Context,
    client: OkHttpClient,
    openSubsonic: Call.Factory,
    youTube: Call.Factory,
    resolver: DataSourceResolver,
    caches: SongCaches,
): DataSource.Factory {
    val cached = CacheDataSource.Factory()
        .setCache(caches.cache)
        .setCacheKeyFactory(keys)
        .setCacheWriteDataSinkFactory(null)
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        .setUpstreamDataSourceFactory(
            streamDataSourceFactory(context, client, openSubsonic, youTube, resolver)
        )
    // The resolver streams the quality of the copy it is given
    return ResolvingDataSource.Factory(cached) { dataSpec ->
        dataSpec.buildUpon()
            .setCustomData(dataSpec.key?.let(QueuedSong::ofCacheKey))
            .build()
    }
}

/** The key of the copy read, set by [SongDataSource] and the downloader. */
@OptIn(UnstableApi::class)
private val keys =
    CacheKeyFactory { it.key ?: QueuedSong.of(it.uri)?.cacheKey ?: it.uri.toString() }

/** Streams what [resolver] finds, see [StreamDataSource]. */
@OptIn(UnstableApi::class)
private fun streamDataSourceFactory(
    context: Context,
    client: OkHttpClient,
    openSubsonic: Call.Factory,
    youTube: Call.Factory,
    resolver: DataSourceResolver,
): DataSource.Factory {
    val app = DefaultDataSource.Factory(context, OkHttpDataSource.Factory(client))
    val server = DefaultDataSource.Factory(context, OkHttpDataSource.Factory(openSubsonic))
    val youTubeStreams = DefaultDataSource.Factory(context, OkHttpDataSource.Factory(youTube))
    return ResolvingDataSource.Factory(
        {
            StreamDataSource(
                app = app.createDataSource(),
                server = server.createDataSource(),
                youTube = youTubeStreams.createDataSource(),
                resolver = resolver,
            )
        },
        resolver,
    )
}

/**
 * Reads a song of the device, or an address that isn't a queued song, with [direct], a
 * downloaded song with [uncached] from its download, a song of the server with [uncached] while
 * [directPlay] is on, and any other song with [cached], from the copy [copies] picked. The read
 * carries that copy as its key and its `customData`, [copies] hears when it ends.
 *
 * @param downloaded the quality a song is downloaded in, see [SongCaches.downloaded]
 */
@OptIn(UnstableApi::class)
private class SongDataSource(
    private val cached: DataSource,
    private val uncached: DataSource,
    private val direct: DataSource,
    private val copies: CacheCopies,
    private val downloaded: (QueuedSong) -> StreamQuality?,
    private val directPlay: () -> Boolean,
) : DataSource {

    private var current: DataSource? = null

    /** The copy read through the cache now. */
    private var copy: QueuedSong? = null

    override fun addTransferListener(transferListener: TransferListener) {
        cached.addTransferListener(transferListener)
        uncached.addTransferListener(transferListener)
        direct.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val song = QueuedSong.of(dataSpec.uri)
        val origin = song?.origin
        if (song != null && origin != Origin.LOCAL) downloaded(song)?.let { quality ->
            current = uncached
            val copy = song.copy(quality = quality)
            return uncached.open(
                dataSpec.buildUpon().setKey(copy.cacheKey).setCustomData(copy).build()
            )
        }
        val source = when {
            origin == null || origin == Origin.LOCAL -> direct
            origin == Origin.OPEN_SUBSONIC && directPlay() -> uncached
            else -> cached
        }
        current = source
        if (source !== cached || song == null) return source.open(dataSpec)

        val copy = song.copy(quality = copies.pick(song, dataSpec.position))
        this.copy = copy
        return source.open(dataSpec.buildUpon().setKey(copy.cacheKey).setCustomData(copy).build())
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(current) { "Read before open" }.read(buffer, offset, length)

    override fun getUri(): Uri? = current?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        current?.responseHeaders.orEmpty()

    override fun close() {
        try {
            current?.close()
        } finally {
            current = null
            copy?.let(copies::readEnded)
            copy = null
        }
    }
}

/**
 * Reads a song resolved by [resolver] with the client of its source, [server] for an OpenSubsonic
 * server, [youTube] for YouTube, [app] for anything else, and tells [resolver] when YouTube
 * refuses an address and what the server answered.
 */
@OptIn(UnstableApi::class)
private class StreamDataSource(
    private val app: DataSource,
    private val server: DataSource,
    private val youTube: DataSource,
    private val resolver: DataSourceResolver,
) : DataSource {

    private var current: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        app.addTransferListener(transferListener)
        server.addTransferListener(transferListener)
        youTube.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val song = dataSpec.customData as? QueuedSong
        val source = when (song?.origin) {
            Origin.OPEN_SUBSONIC -> server
            Origin.YOUTUBE -> youTube
            else -> app
        }
        current = source

        val length = try {
            source.open(dataSpec)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            // The player retries a failed read, the next one gets another address
            if (e.responseCode == HTTP_FORBIDDEN && song != null) resolver.refused(song)
            throw e
        }
        if (song?.origin == Origin.OPEN_SUBSONIC) {
            val contentType = source.responseHeaders.entries
                .firstOrNull { it.key.equals(CONTENT_TYPE, ignoreCase = true) }
                ?.value?.firstOrNull()
            resolver.answered(song, contentType)
        }
        return length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(current) { "Read before open" }.read(buffer, offset, length)

    override fun getUri(): Uri? = current?.uri

    override fun getResponseHeaders(): Map<String, List<String>> =
        current?.responseHeaders.orEmpty()

    override fun close() {
        try {
            current?.close()
        } finally {
            current = null
        }
    }

    private companion object {
        const val HTTP_FORBIDDEN = 403
        const val CONTENT_TYPE = "Content-Type"
    }
}
