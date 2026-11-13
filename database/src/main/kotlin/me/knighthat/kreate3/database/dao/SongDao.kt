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

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import me.knighthat.kreate3.database.entity.ArtistEntity
import me.knighthat.kreate3.database.entity.SongArtistMapEntity
import me.knighthat.kreate3.database.entity.SongEntity
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

    /** The songs of [ids] with their album's shared values, in no defined order. */
    @Query(
        """
        SELECT
            s.*,
            al.name AS album_name,
            al.year AS album_year,
            al.thumbnail AS album_thumbnail
        FROM song s
        LEFT JOIN album al ON al.id = s.album_id
        WHERE s.id IN (:ids)
        """,
    )
    suspend fun sourceValues(ids: List<String>): List<SourceSongRow>

    /** The artists credited on the songs of [ids] with their shared names, in credit order. */
    @Query(
        """
        SELECT m.song_id, ar.id AS artist_id, ar.name AS artist_name
        FROM song_artist_map m
        JOIN artist ar ON ar.id = m.artist_id
        WHERE m.song_id IN (:ids)
        ORDER BY m.rowid
        """,
    )
    suspend fun sourceArtists(ids: List<String>): List<SourceArtistRow>

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
}

/** A song with its album's shared name, year and cover. */
internal data class SourceSongRow(
    @Embedded
    val song: SongEntity,
    @ColumnInfo(name = "album_name")
    val albumName: String?,
    @ColumnInfo(name = "album_year")
    val albumYear: Int?,
    @ColumnInfo(name = "album_thumbnail")
    val albumThumbnail: Uri?,
)

/** An artist credited on a song, with its shared name. */
internal data class SourceArtistRow(
    @ColumnInfo(name = "song_id")
    val songId: String,
    @ColumnInfo(name = "artist_id")
    val artistId: String,
    @ColumnInfo(name = "artist_name")
    val artistName: String?,
)
