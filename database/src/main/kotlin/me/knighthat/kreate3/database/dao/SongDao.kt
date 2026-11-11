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

package me.knighthat.kreate3.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.knighthat.kreate3.database.entity.ArtistEntity
import me.knighthat.kreate3.database.entity.SongArtistMapEntity
import me.knighthat.kreate3.database.entity.SongEntity
import me.knighthat.kreate3.database.entity.UserSongEntity
import me.knighthat.kreate3.model.Origin

@Dao
internal interface SongDao {

    @Query("SELECT * FROM song WHERE id = :id")
    fun find(id: String): Flow<SongEntity?>

    @Query("SELECT * FROM song WHERE id = :id")
    suspend fun get(id: String): SongEntity?

    @Query("SELECT * FROM song WHERE origin = :origin")
    suspend fun withOrigin(origin: Origin): List<SongEntity>

    @Query(
        """
        SELECT song_artist_map.* FROM song_artist_map
        JOIN song ON song.id = song_artist_map.song_id
        WHERE song.origin = :origin
        """,
    )
    suspend fun linksOfOrigin(origin: Origin): List<SongArtistMapEntity>

    @Upsert
    suspend fun upsertAll(songs: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun linkAll(maps: List<SongArtistMapEntity>)

    @Delete
    suspend fun unlinkAll(maps: List<SongArtistMapEntity>)

    @Query(
        """
        SELECT artist.* FROM artist
        JOIN song_artist_map ON song_artist_map.artist_id = artist.id
        WHERE song_artist_map.song_id = :songId
        """,
    )
    fun artistsOf(songId: String): Flow<List<ArtistEntity>>

    /**
     * Inserts or updates in place. Never replaces, a replace would delete the row first
     * and cascade to everything that points at it.
     */
    @Upsert
    suspend fun upsert(song: SongEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun link(map: SongArtistMapEntity)

    @Delete
    suspend fun unlink(map: SongArtistMapEntity)

    @Query("DELETE FROM song WHERE id = :id")
    suspend fun delete(id: String)

    /** Every user's library row of the song [songId]. */
    @Query("SELECT * FROM user_song WHERE song_id = :songId")
    suspend fun libraryRows(songId: String): List<UserSongEntity>

    /** Whether any user has the song [songId] downloaded. */
    @Query(
        """
        SELECT EXISTS (
            SELECT 1 FROM user_song
            WHERE song_id = :songId
            AND downloaded_at IS NOT NULL
        )
        """
    )
    suspend fun isDownloaded(songId: String): Boolean

    /** Moves [from]'s places in playlists to [to], except in playlists that already hold [to]. */
    @Query(
        """
        UPDATE OR IGNORE song_playlist_map
        SET song_id = :to
        WHERE song_id = :from
        """
    )
    suspend fun movePlaylistPlaces(from: String, to: String)

    @Query(
        """
        UPDATE playback_history
        SET song_id = :to
        WHERE song_id = :from
        """
    )
    suspend fun movePlays(from: String, to: String)

    @Query(
        """
        UPDATE persistent_queue
        SET song_id = :to
        WHERE song_id = :from
        """
    )
    suspend fun moveQueued(from: String, to: String)

    /** Moves [from]'s lyrics to [to], except from a source [to] has lyrics of already. */
    @Query(
        """
        UPDATE OR IGNORE lyrics
        SET song_id = :to
        WHERE song_id = :from
        """
    )
    suspend fun moveLyrics(from: String, to: String)
}
