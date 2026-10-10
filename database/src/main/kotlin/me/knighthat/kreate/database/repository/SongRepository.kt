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

package me.knighthat.kreate.database.repository

import kotlinx.coroutines.flow.Flow
import me.knighthat.kreate.database.model.Artist
import me.knighthat.kreate.database.model.Lyrics
import me.knighthat.kreate.database.model.Song
import me.knighthat.kreate.database.model.SongFormat
import me.knighthat.kreate.database.model.UserSong

/**
 * Songs with their formats, lyrics and artists, and the ones the active user keeps in their library.
 *
 * Functions on the shared catalog act for everyone. Functions on the library only touch
 * the active user's data.
 */
interface SongRepository {

    fun find(id: String): Flow<Song?>

    suspend fun save(song: Song)

    /**
     * Deletes the song for everyone, with its formats and lyrics. To remove it from one
     * library use [removeFromLibrary].
     */
    suspend fun delete(id: String)

    fun artistsOf(songId: String): Flow<List<Artist>>

    /**
     * Credits the artist on the song. Both must be saved first.
     */
    suspend fun linkArtist(songId: String, artistId: String)

    suspend fun unlinkArtist(songId: String, artistId: String)

    fun formatsOf(songId: String): Flow<List<SongFormat>>

    suspend fun saveFormat(format: SongFormat)

    suspend fun deleteFormats(songId: String)

    fun lyricsOf(songId: String): Flow<Lyrics?>

    suspend fun saveLyrics(lyrics: Lyrics)

    suspend fun deleteLyrics(songId: String)

    fun library(): Flow<List<UserSong>>

    fun liked(): Flow<List<UserSong>>

    fun findInLibrary(songId: String): Flow<UserSong?>

    /**
     * Adds to the library, or updates the entry. The [Song] must be saved first.
     */
    suspend fun saveToLibrary(song: UserSong)

    suspend fun removeFromLibrary(songId: String)
}
