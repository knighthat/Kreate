package me.knighthat.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Transaction
import app.kreate.database.models.Album
import app.kreate.database.models.Artist
import app.kreate.database.models.Event
import app.kreate.database.models.PlaylistPreview
import app.kreate.database.models.Song
import app.kreate.database.table.DatabaseTable
import kotlinx.coroutines.flow.Flow
import me.knighthat.database.ext.EventWithSong

@Dao
@RewriteQueriesToDropUnusedColumns
interface EventTable: DatabaseTable<Event> {

    @Query("SELECT COUNT(*) FROM Event")
    fun countAll(): Flow<Long>

    @Transaction
    @Query("SELECT DISTINCT * FROM Event LIMIT :limit")
    fun allWithSong( limit: Int = Int.MAX_VALUE ): Flow<List<EventWithSong>>

    /**
     * Return a list of songs that were listened to by user.
     *
     * Songs must be listened at least once within [from] and [to]
     * be included in the results.
     *
     * Results are sorted from most listened to least listened to.
     *
     * By default, only [from] is required, [to] is set to current time.
     * Meaning fetch all from [from] to present.
     *
     * @param from beginning of period to query in epoch millis format
     * @param to the end of period to query in epoch millis format
     * @param limit trim result to have maximum size of this value
     *
     * @return [Song]s that were listened to at least once in period in descending order
     */
    @Query("""
        SELECT DISTINCT S.*
        FROM Song S
        JOIN Event E ON E.songId = S.id
        WHERE E."timestamp" BETWEEN :from AND :to
        GROUP BY E.songId 
        ORDER BY SUM(E.playtime) DESC
        LIMIT :limit
    """)
    fun findSongsMostPlayedBetween(
        from: Long,
        to: Long = System.currentTimeMillis(),
        limit: Int = Int.MAX_VALUE
    ): Flow<List<Song>>

    /**
     * Return a list of artists that have their songs listened to by user.
     *
     * Songs must be listened at least once within [from] and [to]
     * be included in the results.
     *
     * Results are sorted by total [Song.totalPlayTimeMs] in descending order.
     *
     * By default, only [from] is required, [to] is set to current time.
     * Meaning fetch all from [from] to present.
     *
     * @param from beginning of period to query in epoch millis format
     * @param to the end of period to query in epoch millis format
     * @param limit trim result to have maximum size of this value
     *
     * @return [Artist]s that have their songs listened to at least once in period in descending order
     */
    @Query("""
        SELECT DISTINCT A.*
        FROM Artist A
        JOIN SongArtistMap SAM ON SAM.artistId = A.id
        JOIN Event E ON E.songId = SAM.songId
        WHERE E."timestamp" BETWEEN :from AND :to
        GROUP BY A.id
        ORDER BY SUM(E.playtime) DESC
        LIMIT :limit
    """)
    fun findArtistsMostPlayedBetween(
        from: Long,
        to: Long = System.currentTimeMillis(),
        limit: Int = Int.MAX_VALUE
    ): Flow<List<Artist>>

    /**
     * Return a list of albums that have their songs listened to by user.
     *
     * Songs must be listened at least once within [from] and [to]
     * be included in the results.
     *
     * Results are sorted by total [Song.totalPlayTimeMs] in descending order.
     *
     * By default, only [from] is required, [to] is set to current time.
     * Meaning fetch all from [from] to present.
     *
     * @param from beginning of period to query in epoch millis format
     * @param to the end of period to query in epoch millis format
     * @param limit trim result to have maximum size of this value
     *
     * @return [Album]s that have their songs listened to at least once in period in descending order
     */
    @Query("""
        SELECT DISTINCT A.*
        FROM Album A
        JOIN SongAlbumMap SAM ON SAM.albumId = A.id
        JOIN Event E ON E.songId = SAM.songId
        WHERE E."timestamp" BETWEEN :from AND :to
        GROUP BY A.id
        ORDER BY SUM(E.playtime) DESC
        LIMIT :limit
    """)
    fun findAlbumsMostPlayedBetween(
        from: Long,
        to: Long = System.currentTimeMillis(),
        limit: Int = Int.MAX_VALUE
    ): Flow<List<Album>>

    /**
     * Return a list of playlists that have their songs were listened to by user.
     *
     * Songs must be listened at least once within [from] and [to]
     * be included in the results.
     *
     * Results are converted into [PlaylistPreview] and
     * sorted by total [Song.totalPlayTimeMs] in descending order.
     *
     * By default, only [from] is required, [to] is set to current time.
     * Meaning fetch all from [from] to present.
     *
     * @param from beginning of period to query in epoch millis format
     * @param to the end of period to query in epoch millis format
     * @param limit trim result to have maximum size of this value
     *
     * @return [PlaylistPreview] that their songs were listened to at least once in period in descending order
     */
    @Query("""
        SELECT DISTINCT P.*, COUNT(SPM.songId) AS songCount
        FROM Playlist P
        JOIN SongPlaylistMap SPM ON SPM.playlistId = P.id
        JOIN Event E ON E.songId = SPM.songId
        WHERE E."timestamp" BETWEEN :from AND :to
        GROUP BY P.id
        ORDER BY SUM(E.playtime) DESC
        LIMIT :limit
    """)
    fun findPlaylistMostPlayedBetweenAsPreview(
        from: Long,
        to: Long = System.currentTimeMillis(),
        limit: Int = Int.MAX_VALUE
    ): Flow<List<PlaylistPreview>>

    @Query("DELETE FROM Event")
    fun deleteAll(): Int
}