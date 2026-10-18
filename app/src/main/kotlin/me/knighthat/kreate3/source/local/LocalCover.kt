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
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.core.net.toUri
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.key.Keyer
import coil3.request.Options
import me.knighthat.kreate3.source.LARGE_COVER
import me.knighthat.kreate3.source.SMALL_COVER
import me.knighthat.kreate3.source.coverSize
import me.knighthat.kreate3.source.encodeCover
import me.knighthat.kreate3.source.readCover
import me.knighthat.kreate3.source.saveCover
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
 *
 * Reading the picture out of the file is slow, so it is kept in Coil's disk cache in two sizes,
 * [SMALL_COVER] for rows and cards and [LARGE_COVER] for the player and the detail screens. The
 * time the file was last changed is part of the key, a picture changed in the file is read again.
 */
class LocalCoverFetcher(
    private val data: LocalCover,
    private val options: Options,
    private val diskCache: DiskCache?,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val size = coverSize(options.size)
        // Without the time the file changed, a saved picture could be an old one
        val key = dateModified()?.let { "${data.cacheKey}:$it#$size" }
        key?.let { diskCache.readCover(it, options) }?.let { return it }

        val original = embedded() ?: albumArt() ?: return null
        val bytes = encodeCover(original, size) ?: return null

        return diskCache.saveCover(key, bytes, options, DataSource.DISK)
    }

    /** When the song's file last changed, in seconds, `null` when the song is gone. */
    private fun dateModified(): Long? = try {
        options.context.contentResolver.query(
            songUri(),
            arrayOf(MediaStore.Audio.Media.DATE_MODIFIED),
            null,
            null,
            null,
        )?.use { if (it.moveToFirst()) it.getLong(0) else null }
    } catch (_: SecurityException) {
        // The permission to read audio was taken back
        null
    }

    private fun songUri(): Uri =
        ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, data.songId)

    private fun embedded(): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(options.context, songUri())
            retriever.embeddedPicture
        } catch (_: RuntimeException) {
            // Raised for a file that is gone or can't be read, there is no picture then
            null
        } finally {
            retriever.release()
        }
    }

    private fun albumArt(): ByteArray? {
        val uri = data.albumArt ?: return null
        return try {
            options.context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (_: IOException) {
            null
        }
    }

    class Factory : Fetcher.Factory<LocalCover> {

        override fun create(data: LocalCover, options: Options, imageLoader: ImageLoader): Fetcher =
            LocalCoverFetcher(data, options, imageLoader.diskCache)
    }
}
