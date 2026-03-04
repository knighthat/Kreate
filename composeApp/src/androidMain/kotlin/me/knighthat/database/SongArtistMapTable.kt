package me.knighthat.database

import android.database.SQLException
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Upsert
import app.kreate.database.models.Artist
import app.kreate.database.models.Song
import app.kreate.database.models.SongArtistMap
import kotlinx.coroutines.flow.Flow

@Dao
@RewriteQueriesToDropUnusedColumns
interface SongArtistMapTable {

    /**
     * Attempt to write the list of [SongArtistMap] to database.
     *
     * If record exist (determined by its primary key),
     * existing record's columns will be replaced
     * by provided data.
     *
     * @param songArtistMaps list of [SongArtistMap] to insert to database
     */
    @Upsert
    fun upsert( songArtistMaps: List<SongArtistMap> )

    /**
     * Attempt to write [songArtistMap] into database.
     *
     * ### Standalone use
     *
     * When error occurs and [SQLException] is thrown,
     * it'll simply be ignored.
     *
     * ### Transaction use
     *
     * When error occurs and [SQLException] is thrown,
     * it'll simply be ignored and the transaction continues.
     *
     * @param songArtistMap data intended to insert in to database
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIgnore( songArtistMap: SongArtistMap )

    /**
     * Attempt to write list of [SongArtistMap] into database.
     *
     * ### Standalone use
     *
     * When error occurs and [SQLException] is thrown,
     * it'll simply be ignored.
     *
     * ### Transaction use
     *
     * When error occurs and [SQLException] is thrown,
     * it'll simply be ignored and the transaction continues.
     *
     * @param songArtistMaps data intended to insert in to database
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIgnore( songArtistMaps: List<SongArtistMap> )

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