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
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import okhttp3.Call
import okhttp3.OkHttpClient

/**
 * Reads the songs of the queue. A song on the device is read where it is, no cache is involved.
 * Any other song is read from [SongCaches.downloads] first, then from [SongCaches.cache], and
 * only what neither has is streamed: [resolver] finds the address then, and what is streamed is
 * kept in [SongCaches.cache], never in the downloads.
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
): DataSource.Factory {
    val app = DefaultDataSource.Factory(context, OkHttpDataSource.Factory(client))
    val server = DefaultDataSource.Factory(context, OkHttpDataSource.Factory(openSubsonic))
    val network = ResolvingDataSource.Factory(
        { StreamDataSource(app.createDataSource(), server.createDataSource(), resolver) },
        resolver,
    )
    val keys = CacheKeyFactory { QueuedSong.of(it.uri)?.cacheKey ?: it.key ?: it.uri.toString() }
    val cached = CacheDataSource.Factory()
        .setCache(caches.downloads)
        .setCacheKeyFactory(keys)
        // Read only, downloads are written by the downloader
        .setCacheWriteDataSinkFactory(null)
        .setUpstreamDataSourceFactory(
            CacheDataSource.Factory()
                .setCache(caches.cache)
                .setCacheKeyFactory(keys)
                // A cache that can't be read is skipped, the song is streamed instead
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                .setUpstreamDataSourceFactory(network)
        )

    return DataSource.Factory {
        SongDataSource(cached.createDataSource(), network.createDataSource())
    }
}

/**
 * Reads a song of the device, or an address that isn't a queued song, with [direct], and any
 * other song with [cached].
 */
@OptIn(UnstableApi::class)
private class SongDataSource(
    private val cached: DataSource,
    private val direct: DataSource,
) : DataSource {

    private var current: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        cached.addTransferListener(transferListener)
        direct.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val origin = QueuedSong.of(dataSpec.uri)?.origin
        val source = if (origin == null || origin == Origin.LOCAL) direct else cached
        current = source
        return source.open(dataSpec)
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
}

/**
 * Reads a song resolved by [resolver] with [app], or with [server] when it is of an OpenSubsonic
 * server, and tells [resolver] when YouTube refuses an address.
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

        return try {
            source.open(dataSpec)
        } catch (e: HttpDataSource.InvalidResponseCodeException) {
            // The player retries a failed read, the next one gets another address
            if (e.responseCode == HTTP_FORBIDDEN && song != null) resolver.refused(song)
            throw e
        }
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
    }
}
