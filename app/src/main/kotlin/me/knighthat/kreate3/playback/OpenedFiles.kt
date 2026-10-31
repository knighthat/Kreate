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

import android.content.ContentResolver
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.ui.component.toMediaItem
import me.knighthat.kreate3.utils.hasAudioPermission
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * The songs to play for audio files other apps open with the app ("Open with"). A file of the
 * device's library plays as that song. Any other file, or any file while the app can't read the
 * library, plays as an item of the queue only, see [fileItem]: it is never stored, so it has no
 * history and the saved queue leaves it out.
 */
class OpenedFiles(
    private val context: Context,
    private val songs: UserSongRepository,
) {

    private val resolver: ContentResolver = context.contentResolver

    /**
     * The song to play for [uri], `null` when it can't be played: a `file://` address that isn't
     * a song of the library, or a file the app wasn't given access to, the app reads only that.
     */
    suspend fun item(uri: Uri): MediaItem? = withContext(Dispatchers.IO) {
        librarySong(uri) ?: uri.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }
            ?.takeIf(::isReadable)
            ?.let(::fileItem)
    }

    /** Whether the file at [uri] can be opened, the app that sent it may not have allowed it. */
    private fun isReadable(uri: Uri): Boolean = try {
        resolver.openFileDescriptor(uri, "r")?.use { true } ?: false
    } catch (e: Exception) {
        // SecurityException without access, FileNotFoundException for a file that is gone
        Log.w(TAG, "Can't read $uri", e)
        false
    }

    /** The song of the library [uri] is the file of, while the app may read the library. */
    private suspend fun librarySong(uri: Uri): MediaItem? {
        if (!context.hasAudioPermission()) return null
        val id = try {
            mediaStoreId(uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't find $uri in the media index", e)
            null
        } ?: return null
        return songs.find(id.toString()).first()
            ?.takeIf { it.origin == Origin.LOCAL }
            ?.toMediaItem()
    }

    /**
     * Id of the file in the media index, which is the id of its song. Read from the address when
     * it holds one (the index's own, the system's file picker), else found by path for a
     * `file://` address, else by name and size.
     */
    private fun mediaStoreId(uri: Uri): Long? {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val path = uri.path ?: return null
            @Suppress("DEPRECATION")
            return idWhere("${MediaStore.Audio.Media.DATA} = ?", path)
        }

        val parent = uri.pathSegments.dropLast(1)
        val fromAddress = when (uri.authority) {
            // content://media/external/audio/media/<id>, or …/file/<id>: the files table shares
            // its ids. Not the photo picker's, its ids are its own.
            MediaStore.AUTHORITY -> uri.lastPathSegment
                ?.takeIf {
                    parent.takeLast(2) == listOf(
                        "audio",
                        "media"
                    ) || parent.lastOrNull() == "file"
                }
                ?.toLongOrNull()

            // content://com.android.providers.media.documents/document/audio:<id>
            MEDIA_DOCUMENTS -> DocumentsContract.getDocumentId(uri)
                .takeIf { it.startsWith(AUDIO_DOCUMENT) }
                ?.removePrefix(AUDIO_DOCUMENT)
                ?.toLongOrNull()

            else -> null
        }
        if (fromAddress != null) return fromAddress

        val (name, size) = nameAndSize(uri) ?: return null
        return idWhere(
            "${MediaStore.Audio.Media.DISPLAY_NAME} = ? AND ${MediaStore.Audio.Media.SIZE} = ?",
            name,
            size.toString(),
        )
    }

    /** The first file of the media index that matches [selection]. */
    private fun idWhere(selection: String, vararg args: String): Long? =
        resolver.query(
            collection(),
            arrayOf(MediaStore.Audio.Media._ID),
            selection,
            args,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else null
        }

    /** The file's name and size as the app that gave it says, `null` when it says neither. */
    private fun nameAndSize(uri: Uri): Pair<String, Long>? =
        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val name = cursor.getString(0) ?: return null
            if (cursor.isNull(1)) return null
            name to cursor.getLong(1)
        }

    /** The file's name as the app that gave it says, `null` when it can't be asked. */
    private fun displayName(uri: Uri): String? = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    } catch (e: Exception) {
        null
    }

    /**
     * An item for the file at [uri] that isn't a song of the library. It carries the address in
     * its request, which the playback service queues it with (see [withSongUri]), and no origin.
     * Title, artists and album are read from the file's tags, the title is the file's name
     * without them. Its cover is the picture inside the file, see [coverOf].
     */
    private fun fileItem(uri: Uri): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsBrowsable(false)
            .setIsPlayable(true)
        var title: String? = null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            fun tag(key: Int) = retriever.extractMetadata(key)?.takeIf { it.isNotBlank() }

            title = tag(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = tag(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            metadata.setArtist(artist)
                .setSubtitle(artist)
                .setAlbumTitle(tag(MediaMetadataRetriever.METADATA_KEY_ALBUM))
                .setAlbumArtist(tag(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST))
                .setDurationMs(tag(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull())
                .setArtworkUri(retriever.embeddedPicture?.let { coverOf(uri, it) })
        } catch (e: Exception) {
            // Not readable now, the player says why when it tries
            Log.w(TAG, "Couldn't read the tags of $uri", e)
        } finally {
            retriever.release()
        }
        metadata.setTitle(
            title ?: displayName(uri)?.substringBeforeLast('.') ?: uri.lastPathSegment
        )

        return MediaItem.Builder()
            .setMediaId(uri.toString())
            .setMediaMetadata(metadata.build())
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setMediaUri(uri).build())
            .build()
    }

    /**
     * Keeps [picture], the one inside the file at [uri], as a file of the cache: the queue, the
     * notification and Android Auto show a cover only from an address. The last [KEPT_COVERS]
     * files opened keep theirs, older ones are deleted.
     *
     * @return the address of the kept picture, `null` when it couldn't be written
     */
    private fun coverOf(uri: Uri, picture: ByteArray): Uri? = try {
        val folder = File(context.cacheDir, COVER_FOLDER).apply { mkdirs() }
        val name = MessageDigest.getInstance("SHA-1")
            .digest(uri.toString().toByteArray())
            .joinToString("") { "%02x".format(it) }
        val file = File(folder, name)
        file.writeBytes(picture)
        folder.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(KEPT_COVERS)
            ?.forEach { it.delete() }
        Uri.fromFile(file)
    } catch (e: IOException) {
        Log.w(TAG, "Couldn't keep the cover of $uri", e)
        null
    }

    private fun collection(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    private companion object {

        const val TAG = "OpenedFiles"

        /** The system's file picker, for the files of the media index. */
        const val MEDIA_DOCUMENTS = "com.android.providers.media.documents"
        const val AUDIO_DOCUMENT = "audio:"

        /** Folder of the cache with the covers of the files opened, see [coverOf]. */
        const val COVER_FOLDER = "opened_covers"
        const val KEPT_COVERS = 20
    }
}
