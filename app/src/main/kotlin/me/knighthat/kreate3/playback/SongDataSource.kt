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
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import okhttp3.Call
import okhttp3.OkHttpClient

/**
 * Reads the songs of the queue. A song on the device is read where it is, no cache is involved.
 * Any other song is read from [SongCaches.downloads] first, then from [SongCaches.cache], and
 * only what neither has is streamed: [resolver] finds the address then, and what is streamed is
 * kept in [SongCaches.cache], never in the downloads. With [Preferences.OPEN_SUBSONIC_DIRECT_PLAY]
 * on, a song of the server skips [SongCaches.cache]: the downloads, then the stream.
 *
 * [SongCaches.cache] keeps a copy of a song for each quality, [copies] picks the one read.
 *
 * Streams go out with the app's [client], or with [openSubsonic] for an OpenSubsonic server.
 *
 * @param openSubsonic the client of the server, see [OpenSubsonicProvider.streamClient]
 */
@OptIn(UnstableApi::class)
fun songDataSourceFactory(
    context: Context,
    client: OkHttpClient,
    openSubsonic: Call.Factory,
    resolver: DataSourceResolver,
    caches: SongCaches,
    copies: CacheCopies,
): DataSource.Factory {
    val app = DefaultDataSource.Factory(context, OkHttpDataSource.Factory(client))
    val server = DefaultDataSource.Factory(context, OkHttpDataSource.Factory(openSubsonic))
    val network = ResolvingDataSource.Factory(
        { StreamDataSource(app.createDataSource(), server.createDataSource(), resolver) },
        resolver,
    )
    // The key of the copy read, set by SongDataSource
    val keys = CacheKeyFactory { it.key ?: QueuedSong.of(it.uri)?.cacheKey ?: it.uri.toString() }

    // Read only, downloads are written by the downloader
    fun downloads(upstream: DataSource.Factory) = CacheDataSource.Factory()
        .setCache(caches.downloads)
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
        )
    }
}

/**
 * Reads a song of the device, or an address that isn't a queued song, with [direct], a song of
 * the server with [uncached] while [Preferences.OPEN_SUBSONIC_DIRECT_PLAY] is on, and any other
 * song with [cached], from the copy [copies] picked. The read carries that copy as its key and
 * its `customData`, [copies] hears when it ends.
 */
@OptIn(UnstableApi::class)
private class SongDataSource(
    private val cached: DataSource,
    private val uncached: DataSource,
    private val direct: DataSource,
    private val copies: CacheCopies,
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
        val source = when {
            origin == null || origin == Origin.LOCAL -> direct
            origin == Origin.OPEN_SUBSONIC && Preferences.OPEN_SUBSONIC_DIRECT_PLAY.value -> uncached
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
 * Reads a song resolved by [resolver] with [app], or with [server] when it is of an OpenSubsonic
 * server, and tells [resolver] when YouTube refuses an address and what the server answered.
 */
@OptIn(UnstableApi::class)
private class StreamDataSource(
    private val app: DataSource,
    private val server: DataSource,
    private val resolver: DataSourceResolver,
) : DataSource {

    private var current: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        app.addTransferListener(transferListener)
        server.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val song = dataSpec.customData as? QueuedSong
        val source = if (song?.origin == Origin.OPEN_SUBSONIC) server else app
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
