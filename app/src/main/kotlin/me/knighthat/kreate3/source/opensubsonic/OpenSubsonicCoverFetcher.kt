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

package me.knighthat.kreate3.source.opensubsonic

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.source.LARGE_COVER
import me.knighthat.kreate3.source.SMALL_COVER
import me.knighthat.kreate3.source.coverSize
import me.knighthat.kreate3.source.encodeCover
import me.knighthat.kreate3.source.pictureSide
import me.knighthat.kreate3.source.readCover
import me.knighthat.kreate3.source.saveCover
import java.io.IOException
import androidx.core.net.toUri as toAndroidUri

/**
 * Loads the covers of an OpenSubsonic server, the `getCoverArt` addresses [OpenSubsonicProvider]
 * stores on its items without the sign in. They go through the provider, which signs the request
 * in and trusts what the user chose to trust, like a self-signed certificate. Register it before
 * the network fetcher, which would otherwise load them without the sign in.
 *
 * A cover comes in two sizes, [SMALL_COVER] for rows and cards and [LARGE_COVER] for the player
 * and the detail screens, each kept in Coil's disk cache.
 */
class OpenSubsonicCoverFetcher(
    private val uri: android.net.Uri,
    private val options: Options,
    private val provider: OpenSubsonicProvider,
    private val diskCache: DiskCache?,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        // Never `null`, the network fetcher would then send the address to the server unsigned,
        // even while it is off
        if (!provider.isCoverOfServer(uri)) throw IOException("OpenSubsonic server is off")

        val size = coverSize(options.size)
        val key = "$uri#$size"
        diskCache.readCover(key, options)?.let { return it }

        val served = provider.cover(uri, size) ?: throw IOException("No cover at $uri")
        val bytes = if (size == LARGE_COVER) shrinkOriginal(served) else served
        return diskCache.saveCover(key, bytes, options, DataSource.NETWORK)
    }

    /**
     * A server scales a cover larger than asked down and encodes it itself, a smaller one comes
     * as it is, often a JPEG of much higher quality than a screen needs. That one is encoded
     * again, see [encodeCover].
     */
    private suspend fun shrinkOriginal(bytes: ByteArray): ByteArray =
        withContext(Dispatchers.Default) {
            if (pictureSide(bytes) >= LARGE_COVER) bytes
            else encodeCover(bytes, LARGE_COVER) ?: bytes
        }

    class Factory(private val provider: OpenSubsonicProvider) : Fetcher.Factory<Uri> {

        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val uri = data.toString().toAndroidUri()
            return if (provider.isCoverUri(uri)) OpenSubsonicCoverFetcher(
                uri,
                options,
                provider,
                imageLoader.diskCache,
            ) else null
        }
    }
}
