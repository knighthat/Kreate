package app.kreate.internal.database.repositories

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.kreate.database.models.Album
import app.kreate.database.models.Song
import app.kreate.database.repositories.SongAlbumMapTable
import kotlinx.coroutines.flow.Flow


@Dao
@RewriteQueriesToDropUnusedColumns
abstract class AbstractSongAlbumMapTable: SongAlbumMapTable {

    override val tableName: String
        get() = "song_album_map"

    @Query("DELETE FROM song_album_map WHERE album_id = :albumId")
    abstract override fun clear( albumId: String ): Int

    @Query("""
        DELETE FROM song_album_map 
        WHERE song_id NOT IN (
            SELECT DISTINCT id
            FROM songs
        )
    """)
    abstract override fun clearGhostMaps(): Int

    @Query("""
        SELECT DISTINCT songs.*
        FROM song_album_map
        JOIN songs ON id = song_id
        WHERE album_id = :albumId
        ORDER BY position
        LIMIT :limit
    """)
    abstract override fun allSongsOf( albumId: String, limit: Int ): Flow<List<Song>>

    @Query("""
        SELECT A.*
        FROM albums A
        JOIN song_album_map SAM ON SAM.song_id = A.id
        WHERE SAM.song_id = :songId
        LIMIT :limit
    """)
    abstract override fun findAlbumOf( songId: String, limit: Int ): Flow<Album?>

    @Query("""
        INSERT OR IGNORE INTO song_album_map ( song_id, album_id, position )
        VALUES( 
            :songId,
            :albumId,
            CASE
                WHEN :position < 0 THEN COALESCE(
                    (
                        SELECT MAX(position) + 1 
                        FROM song_album_map 
                        WHERE album_id = :albumId
                    ), 
                    0
                )
                ELSE :position 
            END
        )
    """)
    abstract override fun map( songId: String, albumId: String, position: Int )
}