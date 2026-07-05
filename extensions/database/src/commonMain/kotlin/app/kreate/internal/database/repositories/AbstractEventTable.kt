package app.kreate.internal.database.repositories

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import app.kreate.database.ext.EventWithSong
import app.kreate.database.models.Album
import app.kreate.database.models.Artist
import app.kreate.database.models.PlaylistPreview
import app.kreate.database.models.Song
import app.kreate.database.repositories.EventTable
import kotlinx.coroutines.flow.Flow


@Dao
@RewriteQueriesToDropUnusedColumns
internal abstract class AbstractEventTable: EventTable {

    override val tableName: String
        get() = "playback_history"

    @Query("SELECT COUNT(*) FROM playback_history")
    abstract override fun countAll(): Flow<Long>

    @Transaction
    @Query("SELECT DISTINCT * FROM playback_history LIMIT :limit")
    abstract override fun allWithSong( limit: Int ): Flow<List<EventWithSong>>

    @Query("""
        SELECT DISTINCT S.*
        FROM songs S
        JOIN playback_history E ON E.song_id = S.id
        WHERE E.created_at BETWEEN :from AND :to
        GROUP BY E.song_id 
        ORDER BY SUM(E.time_spent) DESC
        LIMIT :limit
    """)
    abstract override fun findSongsMostPlayedBetween(
        from: Long,
        to: Long,
        limit: Int
    ): Flow<List<Song>>

    @Query("""
        SELECT DISTINCT A.*
        FROM artists A
        JOIN song_artist_map SAM ON SAM.artist_id = A.id
        JOIN playback_history E ON E.song_id = SAM.song_id
        WHERE E.created_at BETWEEN :from AND :to
        GROUP BY A.id
        ORDER BY SUM(E.time_spent) DESC
        LIMIT :limit
    """)
    abstract override fun findArtistsMostPlayedBetween(
        from: Long,
        to: Long,
        limit: Int
    ): Flow<List<Artist>>

    @Query("""
        SELECT DISTINCT A.*
        FROM albums A
        JOIN song_album_map SAM ON SAM.album_id = A.id
        JOIN playback_history E ON E.song_id = SAM.song_id
        WHERE E.created_at BETWEEN :from AND :to
        GROUP BY A.id
        ORDER BY SUM(E.time_spent) DESC
        LIMIT :limit
    """)
    abstract override fun findAlbumsMostPlayedBetween(
        from: Long,
        to: Long,
        limit: Int
    ): Flow<List<Album>>

    @Query("""
        SELECT DISTINCT P.*, COUNT(SPM.song_id) AS songCount
        FROM playlists P
        JOIN song_playlist_map SPM ON SPM.playlist_id = P.id
        JOIN playback_history E ON E.song_id = SPM.song_id
        WHERE E.created_at BETWEEN :from AND :to
        GROUP BY P.id
        ORDER BY SUM(E.time_spent) DESC
        LIMIT :limit
    """)
    abstract override fun findPlaylistMostPlayedBetweenAsPreview(
        from: Long,
        to: Long,
        limit: Int
    ): Flow<List<PlaylistPreview>>

    @Query("DELETE FROM playback_history")
    abstract override fun deleteAll(): Int
}