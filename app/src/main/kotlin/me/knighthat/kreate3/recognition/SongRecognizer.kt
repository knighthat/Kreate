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

package me.knighthat.kreate3.recognition

import android.net.Uri
import kotlin.time.Duration

/**
 * A song a [SongRecognizer] heard.
 *
 * @param explicit the recognizer marks it explicit
 * @param pageUrl the song's page on the recognizer's site
 * @param searchQuery what to search for to find the song in the app
 */
data class RecognizedSong(
    val title: String,
    val artist: String,
    val album: String? = null,
    val cover: Uri? = null,
    val explicit: Boolean = false,
    val pageUrl: String? = null,
    val searchQuery: String = "$title $artist",
)

/**
 * Names the song playing around the phone. The microphone is listened to between [start] and
 * [finish]; one listening at a time.
 */
interface SongRecognizer {

    /** Starts listening. Needs the microphone permission, see `hasMicrophonePermission`. */
    fun start()

    /** Stops listening without looking anything up. */
    fun cancel()

    /**
     * Stops listening and looks up what was heard during [heard]. Main-safe.
     *
     * @return the song, `null` when nothing matched
     * @throws Exception when the lookup couldn't be made, or the microphone couldn't be listened to
     */
    suspend fun finish(heard: Duration): RecognizedSong?
}
