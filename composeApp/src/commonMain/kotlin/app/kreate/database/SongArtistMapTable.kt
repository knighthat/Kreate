package app.kreate.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.kreate.database.models.Artist
import app.kreate.database.models.Song
import app.kreate.database.models.SongArtistMap
import app.kreate.database.table.DatabaseTable
import kotlinx.coroutines.flow.Flow

@Dao
@RewriteQueriesToDropUnusedColumns
interface SongArtistMapTable: DatabaseTable<SongArtistMap> {

    override val tableName: String
        get() = "song_artist_map"

    /**
     * @param artistId of artist to look for
     * @param limit number of results cannot go over this value
     *
     * @return all [Song]s that were mapped to artist has [Artist.id] matches [artistId]
     */
    @Query("""
        SELECT DISTINCT songs.*
        FROM song_artist_map sam 
        JOIN songs ON songs.id = sam.song_id
        WHERE sam.artist_id = :artistId
        ORDER BY songs.ROWID
        LIMIT :limit
    """)
    fun allSongsBy( artistId: String, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @return all [Artist]s featured in this song
     */
    @Query("""
        SELECT DISTINCT A.*
        FROM artists A
        JOIN song_artist_map SAM ON SAM.artist_id = A.id
        WHERE SAM.song_id = :songId
        ORDER BY A.ROWID
        LIMIT :limit
    """)
    fun findArtistsOf( songId: String, limit: Int = Int.MAX_VALUE ): Flow<List<Artist>>

    @Query("""
        SELECT DISTINCT S.*
        FROM songs S
        JOIN song_artist_map SAM ON SAM.song_id = S.id
        JOIN artists A ON A.id = SAM.artist_id
        WHERE A.id = :artistId
        ORDER BY S.total_playtime DESC
        LIMIT :limit
    """)
    fun findArtistMostPlayedSongs( artistId: String, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * Delete all mappings where songs aren't exist in `Song` table
     *
     * @return number of rows affected by this operation
     */
    @Query("""
        DELETE FROM song_artist_map 
        WHERE song_id NOT IN (
            SELECT DISTINCT id
            FROM songs
        )
    """)
    fun clearGhostMaps(): Int
}