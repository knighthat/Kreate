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
import androidx.annotation.StringRes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import me.knighthat.kreate3.R

/** The song's source is off or not set up, like an OpenSubsonic server without a sign in. */
const val ERROR_CODE_SOURCE_OFF = PlaybackException.CUSTOM_ERROR_CODE_BASE + 1

/** The song's source has nothing to play for it, like a video YouTube doesn't stream. */
const val ERROR_CODE_NOT_PLAYABLE = PlaybackException.CUSTOM_ERROR_CODE_BASE + 2

/** The title of this song, `null` when it has none to show. */
fun MediaItem?.songTitle(): String? =
    this?.mediaMetadata?.title?.toString()?.takeIf { it.isNotBlank() }

/**
 * What to tell the user when [item] failed to play with this error, like
 * `Couldn't play "Song": file doesn't exist`.
 */
fun PlaybackException.userMessage(context: Context, item: MediaItem?): String =
    cantPlayMessage(context, item, reasonRes())

/** What to tell the user when [item] failed to play because of [reason], see [userMessage]. */
fun cantPlayMessage(context: Context, item: MediaItem?, @StringRes reason: Int): String {
    val song = item.songTitle()?.let { context.getString(R.string.quoted, it) }
        ?: context.getString(R.string.snackbar_this_song)
    return context.getString(R.string.snackbar_cant_play, song, context.getString(reason))
}

/** Why a song failed to play, in words for the user. */
@StringRes
fun PlaybackException.reasonRes(): Int = playbackReasonRes(errorCode)

/**
 * Why reading a song failed, in words for the user, from a [PlaybackException] error code, like
 * a `DataSourceException`'s reason. Downloads say it too.
 */
@StringRes
fun playbackReasonRes(errorCode: Int): Int = when (errorCode) {
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
