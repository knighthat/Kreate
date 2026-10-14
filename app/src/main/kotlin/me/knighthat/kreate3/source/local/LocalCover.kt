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

package me.knighthat.kreate3.source.local

import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse
import me.knighthat.kreate3.source.local.LocalCoverFetcher.Companion.MAX_SIZE
import java.io.IOException

private val ALBUM_ART: Uri = "content://media/external/audio/albumart".toUri()

/**
 * Address of the cover of an album in the media index.
 */
fun albumArtUri(albumId: Long): Uri = ContentUris.withAppendedId(ALBUM_ART, albumId)

/**
 * Whether this is the cover of an album in the media index, which is what the scanner gives a
 * song. Anything else, like a file or an address the user picked, is not.
 */
fun Uri.isAlbumArt(): Boolean =
    scheme == "content" && authority == MediaStore.AUTHORITY &&
            pathSegments.take(3) == listOf("external", "audio", "albumart")

/**
 * The cover of a song on this device.
 *
 * @property songId id of the song in the media index
 * @property albumArt cover of the song's album, `null` when the song has no album
 */
data class LocalCover(val songId: Long, val albumArt: Uri?) {

    /** Key of the picture in Coil's caches. */
    val cacheKey: String get() = "local_cover:$songId:$albumArt"
}

class LocalCoverKeyer : Keyer<LocalCover> {

    override fun key(data: LocalCover, options: Options) = data.cacheKey
}

/**
 * Loads a [LocalCover]: the picture inside the song's file first, then the album's cover. A song
 * without either has no cover. The folder of the song is never searched, unrelated songs in
 * the same folder would get each other's pictures.
 */
class LocalCoverFetcher(
    private val data: LocalCover,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val bitmap = embedded() ?: albumArt() ?: return null

        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    private fun embedded(): Bitmap? {
        val uri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            data.songId,
        )
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(options.context, uri)
            retriever.embeddedPicture?.let { decode(it) }
        } catch (_: RuntimeException) {
            // Raised for a file that is gone or can't be read, there is no picture then
            null
        } finally {
            retriever.release()
        }
    }

    private fun albumArt(): Bitmap? {
        val uri = data.albumArt ?: return null
        return try {
            options.context.contentResolver.openInputStream(uri)?.use { stream ->
                decode(stream.readBytes())
            }
        } catch (_: IOException) {
            null
        }
    }

    /**
     * Decodes at most [MAX_SIZE] pixels per side, the pictures in files can be very large. When
     * the request has a size, the picture is made no larger than needed to cover it, a small
     * thumbnail doesn't need to keep a large picture in memory.
     */
    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)

        val sample = sampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            targetWidth = options.size.width.pxOrElse { MAX_SIZE },
            targetHeight = options.size.height.pxOrElse { MAX_SIZE },
        )

        return BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    }

    class Factory : Fetcher.Factory<LocalCover> {

        override fun create(data: LocalCover, options: Options, imageLoader: ImageLoader): Fetcher =
            LocalCoverFetcher(data, options)
    }

    internal companion object {

        const val MAX_SIZE = 1024

        /**
         * The power of two to divide a [width] by [height] picture by, so that neither side is
         * over [MAX_SIZE] and the picture still covers [targetWidth] by [targetHeight].
         */
        fun sampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
            // A target of 0 is covered by any size, the sample would grow until it overflows
            val minWidth = targetWidth.coerceAtLeast(1)
            val minHeight = targetHeight.coerceAtLeast(1)
            var sample = 1
            while (
                width / sample > MAX_SIZE || height / sample > MAX_SIZE ||
                (width / (sample * 2) >= minWidth && height / (sample * 2) >= minHeight)
            )
                sample *= 2
            return sample
        }
    }
}
