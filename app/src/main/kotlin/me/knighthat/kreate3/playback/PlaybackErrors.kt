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

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException

/**
 * What to tell the user when [item] failed to play with this error, like
 * `Couldn't play "Song": its file is gone`.
 */
fun PlaybackException.userMessage(item: MediaItem?): String {
    val title = item?.mediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() }
    val song = title?.let { "\"$it\"" } ?: "this song"
    return "Couldn't play $song: ${reason()}"
}

private fun PlaybackException.reason(): String = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "its file is gone"

    PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "the app isn't allowed to read it"

    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "no connection"

    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> "the server refused it"

    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "its file is damaged"

    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "its format isn't supported"

    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
    PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> "the device couldn't decode it"

    else -> "something went wrong"
}
