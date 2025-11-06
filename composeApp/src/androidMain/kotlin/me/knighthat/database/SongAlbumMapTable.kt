package me.knighthat.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.kreate.database.models.Album
import app.kreate.database.models.Song
import app.kreate.database.models.SongAlbumMap
import app.kreate.database.table.DatabaseTable
import kotlinx.coroutines.flow.Flow

@Dao
@RewriteQueriesToDropUnusedColumns
interface SongAlbumMapTable: DatabaseTable<SongAlbumMap> {

    override val tableName: String
        get() = "song_album_map"

    /**
     * Remove all songs belong to album with id [albumId]
     *
     * @param albumId album to have its songs wiped
     *
     * @return number of rows affected by this operation
     */
    @Query("DELETE FROM song_album_map WHERE album_id = :albumId")
    fun clear( albumId: String ): Int

    /**
     * Delete all mappings where songs aren't exist in `Song` table
     *
     * @return number of rows affected by this operation
     */
    @Query("""
        DELETE FROM song_album_map 
        WHERE song_id NOT IN (
            SELECT DISTINCT id
            FROM songs
        )
    """)
    fun clearGhostMaps(): Int

    /**
     * Results are sorted by [SongAlbumMap.position].
     *
     * @param albumId of artist to look for
     * @param limit number of results cannot go over this value
     *
     * @return all [Song]s that were mapped to album has [Album.id] matches [albumId],
     * sorted by song's position in album
     */
    @Query("""
        SELECT DISTINCT songs.*
        FROM song_album_map
        JOIN songs ON id = song_id
        WHERE album_id = :albumId
        ORDER BY position
        LIMIT :limit
    """)
    fun allSongsOf( albumId: String, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @return [Album] that the song belongs to
     */
    @Query("""
        SELECT A.*
        FROM albums A
        JOIN song_album_map SAM ON SAM.song_id = A.id
        WHERE SAM.song_id = :songId
        LIMIT :limit
    """)
    fun findAlbumOf( songId: String, limit: Int = Int.MAX_VALUE ): Flow<Album?>

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
    fun map( songId: String, albumId: String, position: Int = -1 )
}