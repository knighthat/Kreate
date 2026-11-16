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
import androidx.room.Query

/** A playlist [CleanupDao.deleteEmptyPlaylists] is about to delete, with its picture. */
internal data class EmptyPlaylistRow(
    @ColumnInfo(name = "playlist_id")
    val playlistId: Long,
    @ColumnInfo(name = "user_id")
    val userId: String,
    val thumbnail: Uri?,
)

/**
 * Deletes what no user keeps, for every user at once. Device songs, albums and artists are left
 * to the library scan, which puts their library rows back each time it runs.
 */
@Dao
internal interface CleanupDao {

    /**
     * Library rows of songs the user changed nothing of, never played and has in none of their
     * playlists (their lists read the library).
     */
    @Query(
        """
        DELETE FROM user_song
        WHERE name IS NULL
          AND artists IS NULL
          AND thumbnail IS NULL
          AND liked_at IS NULL
          AND album_position IS NULL
          AND downloaded_at IS NULL
          AND music_brainz_id IS NULL
          AND song_id NOT IN (SELECT id FROM song WHERE origin = 'LOCAL')
          AND NOT EXISTS (
              SELECT 1 FROM playback_history h
              WHERE h.user_id = user_song.user_id
                AND h.song_id = user_song.song_id
          )
          AND NOT EXISTS (
              SELECT 1 FROM song_playlist_map m
              JOIN user_playlist p ON p.playlist_id = m.playlist_id
              WHERE p.user_id = user_song.user_id
                AND m.song_id = user_song.song_id
          )
        """
    )
    suspend fun deleteUnusedUserSongs(): Int

    /** Library rows of albums the user changed nothing of and didn't bookmark. */
    @Query(
        """
        DELETE FROM user_album
        WHERE name IS NULL
          AND thumbnail IS NULL
          AND artists IS NULL
          AND bookmarked_at IS NULL
          AND album_id NOT IN (SELECT id FROM album WHERE origin = 'LOCAL')
        """
    )
    suspend fun deleteUnusedUserAlbums(): Int

    /** Library rows of artists the user changed nothing of and didn't follow. */
    @Query(
        """
        DELETE FROM user_artist
        WHERE name IS NULL
          AND thumbnail IS NULL
          AND follow_since IS NULL
          AND artist_id NOT IN (SELECT id FROM artist WHERE origin = 'LOCAL')
        """
    )
    suspend fun deleteUnusedUserArtists(): Int

    @Query(
        """
        SELECT playlist_id, user_id, thumbnail FROM user_playlist
        WHERE NOT EXISTS (
            SELECT 1 FROM song_playlist_map m
            WHERE m.playlist_id = user_playlist.playlist_id
        )
        """
    )
    suspend fun emptyPlaylists(): List<EmptyPlaylistRow>

    @Query(
        """
        DELETE FROM user_playlist
        WHERE NOT EXISTS (
            SELECT 1 FROM song_playlist_map m
            WHERE m.playlist_id = user_playlist.playlist_id
        )
        """
    )
    suspend fun deleteEmptyPlaylists(): Int

    /**
     * Songs no user has in their library, a playlist, their saved queue or their plays, and
     * whose lyrics weren't imported from a file. Their formats and other lyrics go with them.
     */
    @Query(
        """
        DELETE FROM song
        WHERE origin != 'LOCAL'
          AND NOT EXISTS (SELECT 1 FROM user_song us WHERE us.song_id = song.id)
          AND NOT EXISTS (SELECT 1 FROM song_playlist_map m WHERE m.song_id = song.id)
          AND NOT EXISTS (SELECT 1 FROM persistent_queue q WHERE q.song_id = song.id)
          AND NOT EXISTS (SELECT 1 FROM playback_history h WHERE h.song_id = song.id)
          AND NOT EXISTS (SELECT 1 FROM lyrics l WHERE l.song_id = song.id AND l.source = 'FILE')
        """
    )
    suspend fun deleteUnusedSongs(): Int

    /** Albums no song is on and no user has in their library. */
    @Query(
        """
        DELETE FROM album
        WHERE NOT EXISTS (SELECT 1 FROM song s WHERE s.album_id = album.id)
          AND NOT EXISTS (SELECT 1 FROM user_album ua WHERE ua.album_id = album.id)
        """
    )
    suspend fun deleteUnusedAlbums(): Int

    /** Artists no song credits and no user has in their library. */
    @Query(
        """
        DELETE FROM artist
        WHERE NOT EXISTS (SELECT 1 FROM song_artist_map m WHERE m.artist_id = artist.id)
          AND NOT EXISTS (SELECT 1 FROM user_artist ua WHERE ua.artist_id = artist.id)
        """
    )
    suspend fun deleteUnusedArtists(): Int
}
