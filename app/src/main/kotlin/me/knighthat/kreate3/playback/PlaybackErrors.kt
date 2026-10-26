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

import androidx.annotation.StringRes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import me.knighthat.kreate3.R

/** The song's source is off or not set up, like an OpenSubsonic server without a sign in. */
const val ERROR_CODE_SOURCE_OFF = PlaybackException.CUSTOM_ERROR_CODE_BASE + 1

/** The song's source has nothing to play for it, like a video YouTube doesn't stream. */
const val ERROR_CODE_NOT_PLAYABLE = PlaybackException.CUSTOM_ERROR_CODE_BASE + 2

/**
 * What to tell the user when [item] failed to play with this error, like
 * `Couldn't play "Song": its file is gone`.
 *
 * @param reason why it failed, see [reasonRes]
 */
fun PlaybackException.userMessage(item: MediaItem?, reason: String): String {
    val title = item?.mediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() }
    val song = title?.let { "\"$it\"" } ?: "this song"
    return "Couldn't play $song: $reason"
}

/** Why a song failed to play, in words for the user. */
@StringRes
fun PlaybackException.reasonRes(): Int = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> R.string.error_playback_file_missing

    PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> R.string.error_playback_permission_denied

    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> R.string.error_playback_no_connection

    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
    PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> R.string.error_playback_server_refused

    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> R.string.error_playback_file_damaged

    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> R.string.error_playback_format_unsupported

    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
    PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> R.string.error_playback_decoding_failed

    ERROR_CODE_SOURCE_OFF -> R.string.error_playback_source_off

    ERROR_CODE_NOT_PLAYABLE -> R.string.error_playback_not_playable

    else -> R.string.error_playback_unknown
}
