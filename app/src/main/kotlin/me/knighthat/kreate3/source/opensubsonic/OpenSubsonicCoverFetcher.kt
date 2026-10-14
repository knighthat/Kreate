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
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import okio.Buffer
import androidx.core.net.toUri as toAndroidUri

/**
 * Loads the covers of an OpenSubsonic server, the `getCoverArt` addresses [OpenSubsonicProvider]
 * stores on its items without the sign in. They go through the provider, which signs the request
 * in and trusts what the user chose to trust, like a self-signed certificate. Register it before
 * the network fetcher, which would otherwise load them without the sign in.
 */
class OpenSubsonicCoverFetcher(
    private val uri: android.net.Uri,
    private val options: Options,
    private val provider: OpenSubsonicProvider,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        // The largest side the picture is shown at, the server scales it down before sending
        val size = maxOf(
            options.size.width.pxOrElse { 0 },
            options.size.height.pxOrElse { 0 },
        ).takeIf { it > 0 }
        val bytes = provider.cover(uri, size) ?: return null

        return SourceFetchResult(
            source = ImageSource(Buffer().write(bytes), options.fileSystem),
            mimeType = null,
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory(private val provider: OpenSubsonicProvider) : Fetcher.Factory<Uri> {

        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val uri = data.toString().toAndroidUri()
            return if (provider.isCoverUri(uri)) OpenSubsonicCoverFetcher(
                uri,
                options,
                provider
            ) else null
        }
    }
}
