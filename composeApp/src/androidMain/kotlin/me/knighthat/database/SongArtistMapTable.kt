package me.knighthat.database

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

    /**
     * @param artistId of artist to look for
     * @param limit number of results cannot go over this value
     *
     * @return all [Song]s that were mapped to artist has [Artist.id] matches [artistId]
     */
    @Query("""
        SELECT DISTINCT Song.*
        FROM SongArtistMap sam 
        JOIN Song ON Song.id = sam.songId
        WHERE sam.artistId = :artistId
        ORDER BY Song.ROWID
        LIMIT :limit
    """)
    fun allSongsBy( artistId: String, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @return all [Artist]s featured in this song
     */
    @Query("""
        SELECT DISTINCT A.*
        FROM Artist A
        JOIN SongArtistMap SAM ON SAM.artistId = A.id
        WHERE SAM.songId = :songId
        ORDER BY A.ROWID
        LIMIT :limit
    """)
    fun findArtistsOf( songId: String, limit: Int = Int.MAX_VALUE ): Flow<List<Artist>>

    @Query("""
        SELECT DISTINCT S.*
        FROM Song S
        JOIN SongArtistMap SAM ON SAM.songId = S.id
        JOIN Artist A ON A.id = SAM.artistId
        WHERE A.id = :artistId
        ORDER BY S.totalPlayTimeMs DESC
        LIMIT :limit
    """)
    fun findArtistMostPlayedSongs( artistId: String, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * Delete all mappings where songs aren't exist in `Song` table
     *
     * @return number of rows affected by this operation
     */
    @Query("""
        DELETE FROM SongArtistMap 
        WHERE songId NOT IN (
            SELECT DISTINCT id
            FROM Song
        )
    """)
    fun clearGhostMaps(): Int
}