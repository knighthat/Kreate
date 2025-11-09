package app.kreate.database.table

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.kreate.database.models.Album
import app.kreate.database.models.Song
import app.kreate.database.models.SongAlbumMap
import kotlinx.coroutines.flow.Flow

@Dao
@RewriteQueriesToDropUnusedColumns
interface SongAlbumMapTable: DatabaseTable<SongAlbumMap> {

    override val tableName: String
        get() = SongAlbumMap::class.simpleName!!

    /**
     * Remove all songs belong to album with id [albumId]
     *
     * @param albumId album to have its songs wiped
     *
     * @return number of rows affected by this operation
     */
    @Query("DELETE FROM SongAlbumMap WHERE albumId = :albumId")
    fun clear( albumId: String ): Int

    /**
     * Delete all mappings where songs aren't exist in `Song` table
     *
     * @return number of rows affected by this operation
     */
    @Query("""
        DELETE FROM SongAlbumMap 
        WHERE songId NOT IN (
            SELECT DISTINCT id
            FROM Song
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
        SELECT DISTINCT Song.*
        FROM SongAlbumMap
        JOIN Song ON id = songId
        WHERE albumId = :albumId
        ORDER BY position
        LIMIT :limit
    """)
    fun allSongsOf( albumId: String, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @return [Album] that the song belongs to
     */
    @Query("""
        SELECT A.*
        FROM Album A
        JOIN SongAlbumMap SAM ON SAM.albumId = A.id
        WHERE SAM.songId = :songId
        LIMIT :limit
    """)
    fun findAlbumOf( songId: String, limit: Int = Int.MAX_VALUE ): Flow<Album?>

    @Query("""
        INSERT OR IGNORE INTO SongAlbumMap ( songId, albumId, position )
        VALUES( 
            :songId,
            :albumId,
            CASE
                WHEN :position < 0 THEN COALESCE(
                    (
                        SELECT MAX(position) + 1 
                        FROM SongAlbumMap 
                        WHERE albumId = :albumId
                    ), 
                    0
                )
                ELSE :position 
            END
        )
    """)
    fun map( songId: String, albumId: String, position: Int = -1 )
}