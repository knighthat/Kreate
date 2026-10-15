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

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.database.model.SongFormat
import me.knighthat.kreate3.database.repository.SongRepository
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.youtube.YouTubeStream

/**
 * Stores the format of a song when the player is about to read it, so [LoudnessNormalizer] has
 * its gain before it plays. [DataSourceResolver] calls [record] before it gives the address.
 *
 * - YouTube: the format of the stream that was just found, every time a new one is.
 * - OpenSubsonic: asked to the server once, while the song has no format of the server.
 * - Device: nothing, `LoudnessWorker` measures the files.
 *
 * Only a song in the database gets a format. A failure is logged, the song plays without it.
 */
class SongFormatRecorder(
    private val songs: SongRepository,
    private val openSubsonic: suspend (songId: String) -> SongFormat?,
) {

    constructor(songs: SongRepository, openSubsonic: OpenSubsonicProvider) :
            this(songs, openSubsonic::format)

    /**
     * @param stream the stream just found for a YouTube song, `null` for other origins
     */
    suspend fun record(song: QueuedSong, stream: YouTubeStream?) {
        if (song.origin == Origin.LOCAL) return
        try {
            // A song shown live isn't stored, its format can't be either
            if (songs.find(song.id).first() == null) return

            val format = when (song.origin) {
                Origin.YOUTUBE -> stream?.toSongFormat(song.id)
                Origin.OPEN_SUBSONIC ->
                    if (hasFormat(song.id, Origin.OPEN_SUBSONIC.name)) null
                    else openSubsonic(song.id)

                Origin.LOCAL -> null
            } ?: return
            songs.saveFormat(format)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't store the format of ${song.id}", e)
        }
    }

    private suspend fun hasFormat(songId: String, tag: String): Boolean =
        songs.formatsOf(songId).first().any { it.tag == tag }

    private companion object {
        const val TAG = "SongFormatRecorder"
    }
}
