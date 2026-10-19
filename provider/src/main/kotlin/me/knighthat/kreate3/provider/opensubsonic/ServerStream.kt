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
package me.knighthat.kreate3.provider.opensubsonic

import android.net.Uri
import me.knighthat.kreate3.database.model.SongFormat

/** Tag of the format of a song's file as it is on the server, see [OpenSubsonicProvider.format]. */
const val RAW_FORMAT = "raw"

/**
 * What the server sends of a song, see [OpenSubsonicProvider.stream].
 *
 * @property uri where to read it
 * @property format the format the server converts the song to, `null` when it sends the file as
 * it is ([RAW_FORMAT]) or doesn't say what it converts to, see [maxBitRate]
 * @property maxBitRate the highest bitrate in kbps asked of a server that doesn't say what it
 * converts to, which only the answer to the stream tells, see [answeredFormat]. `null` when the
 * format is known.
 */
data class ServerStream(val uri: Uri, val format: SongFormat?, val maxBitRate: Int?) {

    /**
     * The format of the stream from the media type the server answered it with, for a server
     * that didn't say before, tagged `{format}-{kbps}` like `mp3-128`. The format is the one the
     * media type names, `ogg` for an Ogg stream whatever is in it.
     *
     * @param sourceType the media type of the file on the server, a stream of the same type is
     * taken as the file as it is
     * @return `null` when the server sent the file as it is, or when the format was known
     */
    fun answeredFormat(songId: String, contentType: String?, sourceType: String?): SongFormat? =
        if (format != null) null
        else answeredFormat(songId, maxBitRate ?: return null, contentType, sourceType)
}

/** See [ServerStream.answeredFormat], for a stream asked at most [maxBitRate] kbps. */
internal fun answeredFormat(
    songId: String,
    maxBitRate: Int,
    contentType: String?,
    sourceType: String?,
): SongFormat? {
    val type = contentType?.mediaType()?.takeIf { it.startsWith("audio/") } ?: return null
    if (type == sourceType?.mediaType()) return null

    val name = when (val subtype = type.substringAfter('/').removePrefix("x-")) {
        "mpeg", "mp3" -> "mp3"
        else -> subtype
    }
    return SongFormat(
        songId = songId,
        tag = transcodedTag(name, maxBitRate),
        mimetype = type,
        bitrate = maxBitRate * 1000,
    )
}

/** Without its parameters, like `audio/ogg` of `audio/ogg; codecs=opus`. */
private fun String.mediaType(): String = substringBefore(';').trim().lowercase()

/** Tag of a format the server converts songs to, like `opus-96`. */
internal fun transcodedTag(format: String, kbps: Int): String = "$format-$kbps"
