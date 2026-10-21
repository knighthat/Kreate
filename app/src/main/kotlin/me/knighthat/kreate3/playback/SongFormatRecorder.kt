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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import me.knighthat.kreate3.database.model.SongFormat
import me.knighthat.kreate3.database.repository.SongRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.opensubsonic.RAW_FORMAT
import me.knighthat.kreate3.provider.opensubsonic.ServerStream

/**
 * Stores the format of a song when the player is about to read it, so [LoudnessNormalizer] has
 * its gain before it plays. [DataSourceResolver] calls [record] before it gives the address.
 *
 * - YouTube: the format of the stream that was just found, every time a new one is.
 * - OpenSubsonic: the file on the server ([RAW_FORMAT], with the gain), asked to the server once,
 *   while the song has none. And the format the server converts it to, every time a new stream
 *   is found, or once the server answered it ([recordAnswered]) when the server didn't say
 *   before.
 * - Device: nothing, `LoudnessWorker` measures the files.
 *
 * A song not in the database yet, like one shown live that starts playing, can't have its formats
 * stored: they are kept here until [flush] stores them, and [pendingFormatsOf] gives them
 * meanwhile. A failure is logged, the song plays without them.
 */
class SongFormatRecorder(
    private val songs: SongRepository,
    private val openSubsonic: suspend (songId: String) -> SongFormat?,
) {

    constructor(songs: SongRepository, openSubsonic: OpenSubsonicProvider) :
            this(songs, openSubsonic::format)

    /** Formats of songs not stored yet, by song id, the least recently found first. */
    private val pending = object : LinkedHashMap<String, List<SongFormat>>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SongFormat>>) =
            size > MAX_PENDING
    }

    /** Changes each time [pending] does, for [pendingFormats]. */
    private val pendingChanges = MutableStateFlow(0L)

    /**
     * The formats kept for [songId] while it isn't stored, empty when there are none. A lookup in
     * memory, quick enough for the main thread.
     */
    fun pendingFormatsOf(songId: String): List<SongFormat> =
        synchronized(pending) { pending[songId].orEmpty() }

    /**
     * [pendingFormatsOf] now and each time it changes, like when the song is read and its format
     * found. Empty again once [flush] moved them to the database.
     */
    fun pendingFormats(songId: String): Flow<List<SongFormat>> =
        pendingChanges.map { pendingFormatsOf(songId) }.distinctUntilChanged()

    /**
     * @param format the format of the stream just found, `null` when none was or it isn't known
     * before reading
     */
    suspend fun record(song: QueuedSong, format: SongFormat?) {
        if (song.origin == Origin.LOCAL) return
        try {
            val isStored = songs.find(song.id).first() != null
            if (song.origin == Origin.OPEN_SUBSONIC && !hasFormat(song.id, RAW_FORMAT, isStored))
                openSubsonic(song.id)?.let { save(song.id, it, isStored) }
            format?.let { save(song.id, it, isStored) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't store the format of ${song.id}", e)
        }
    }

    /**
     * Stores the format of [stream] of a song of the server that didn't say what it converts to,
     * now that it answered with [contentType], see [ServerStream.answeredFormat].
     */
    suspend fun recordAnswered(song: QueuedSong, stream: ServerStream, contentType: String?) {
        try {
            val isStored = songs.find(song.id).first() != null
            val source = formatsOf(song.id, isStored).firstOrNull { it.tag == RAW_FORMAT }
            val format = stream.answeredFormat(song.id, contentType, source?.mimetype) ?: return
            save(song.id, format, isStored)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't store the format of ${song.id}", e)
        }
    }

    /**
     * Stores the formats kept for [songId] while it wasn't stored, call it once the song is.
     * Nothing happens when there are none.
     */
    suspend fun flush(songId: String) {
        val formats = synchronized(pending) { pending.remove(songId) } ?: return
        pendingChanges.update { it + 1 }
        try {
            formats.forEach { songs.saveFormat(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't store the format of $songId", e)
        }
    }

    private suspend fun save(songId: String, format: SongFormat, isStored: Boolean) {
        if (isStored) return songs.saveFormat(format)
        synchronized(pending) {
            pending[songId] = pending[songId].orEmpty().filter { it.tag != format.tag } + format
        }
        pendingChanges.update { it + 1 }
        // Stored meanwhile, the flush may have run before the format was kept
        if (songs.find(songId).first() != null) flush(songId)
    }

    private suspend fun formatsOf(songId: String, isStored: Boolean): List<SongFormat> =
        if (isStored) songs.formatsOf(songId).first()
        else pendingFormatsOf(songId)

    private suspend fun hasFormat(songId: String, tag: String, isStored: Boolean): Boolean =
        formatsOf(songId, isStored).any { it.tag == tag }

    private companion object {
        const val TAG = "SongFormatRecorder"

        /** Formats kept for songs not stored yet, about a queue's worth. */
        const val MAX_PENDING = 50
    }
}
