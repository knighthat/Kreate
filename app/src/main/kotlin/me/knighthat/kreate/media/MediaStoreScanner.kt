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

package me.knighthat.kreate.media

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.net.toUri
import me.knighthat.kreate.database.model.LocalAlbum
import me.knighthat.kreate.database.model.LocalArtist
import me.knighthat.kreate.database.model.LocalSong
import kotlin.time.Duration.Companion.milliseconds

/**
 * Reads the music on the device from Android's media index.
 */
class MediaStoreScanner(private val resolver: ContentResolver) {

    /**
     * Every music file the index knows about, empty when the permission is missing.
     */
    fun scan(): List<LocalSong> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.ARTIST_ID,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ALBUM,
        )

        // Volumes exist since Android 10, they include storage the device can add, like an SD card
        val collection =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val songs = mutableListOf<LocalSong>()
        resolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            null,
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val track = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val year = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val artistId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST_ID)
            val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumId = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)

            while (cursor.moveToNext()) {
                val albumIdValue = cursor.getLong(albumId)
                songs += toLocalSong(
                    id = cursor.getLong(id),
                    title = cursor.getString(title),
                    durationMs = cursor.getLong(duration),
                    track = cursor.getInt(track),
                    year = cursor.getInt(year),
                    artistId = cursor.getLong(artistId),
                    artist = cursor.getString(artist),
                    albumId = albumIdValue,
                    album = cursor.getString(album),
                    albumArt = ContentUris.withAppendedId(ALBUM_ART, albumIdValue).toString(),
                )
            }
        }
        return songs
    }

    private companion object {

        val ALBUM_ART: Uri = "content://media/external/audio/albumart".toUri()
    }
}

/**
 * Turns the values of one row into a song. The index fills missing text with `<unknown>`
 * and missing numbers with `0`, those become `null`.
 *
 * @param track disc and track in one number, the disc is the thousands (`1003` is disc 1, track 3)
 */
internal fun toLocalSong(
    id: Long,
    title: String?,
    durationMs: Long,
    track: Int,
    year: Int,
    artistId: Long,
    artist: String?,
    albumId: Long,
    album: String?,
    albumArt: String?,
): LocalSong {
    fun String?.known() = this?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }

    return LocalSong(
        id = id.toString(),
        name = title.known(),
        duration = durationMs.takeIf { it > 0 }?.milliseconds,
        albumPosition = (track % 1000).takeIf { it > 0 },
        artist = artist.known()?.let { LocalArtist(artistId.toString(), it) },
        album = album.known()?.let {
            LocalAlbum(albumId.toString(), it, albumArt, year.takeIf { y -> y > 0 })
        },
    )
}
