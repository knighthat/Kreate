package me.knighthat.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Upsert
import app.kreate.database.models.Format
import app.kreate.database.models.Song
import app.kreate.util.MODIFIED_PREFIX
import it.fast4x.rimusic.enums.SongSortBy
import it.fast4x.rimusic.enums.SortOrder
import it.fast4x.rimusic.utils.durationToMillis
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.knighthat.database.ext.FormatWithSong

@Dao
@RewriteQueriesToDropUnusedColumns
interface FormatTable {

    /**
     * @return formats & songs of this table
     */
    @Query("""
        SELECT DISTINCT F.*, S.* 
        FROM Format F
        JOIN Song S ON S.id = F.songId
        WHERE totalPlayTimeMs >= :excludeHidden
        ORDER BY S.ROWID 
        LIMIT :limit
    """)
    fun allWithSongs(
        limit: Int = Int.MAX_VALUE,
        excludeHidden: Boolean = false
    ): Flow<List<FormatWithSong>>

    /**
     * @return formats & songs of this table in randomized order
     */
    @Query("""
        SELECT DISTINCT F.*, S.* 
        FROM Format F
        JOIN Song S ON S.id = F.songId
        WHERE totalPlayTimeMs >= :excludeHidden
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    fun allWithSongsRandomized(
        limit: Int = Int.MAX_VALUE,
        excludeHidden: Boolean = false
    ): Flow<List<FormatWithSong>>

    /**
     * [Format] with [Format.songId] inside [songIds] will be removed.
     *
     * @return number of rows affected by this operation
     */
    @Query("DELETE FROM Format WHERE songId IN (:songIds)")
    fun deleteBySongId( songIds: List<String> ): Int

    fun deleteBySongId( vararg songIds: String ): Int = deleteBySongId( songIds.toList() )


    /**
     * @param songId of song to look for
     * @return [Format] that has [Format.songId] matches [songId]
     */
    @Query("SELECT DISTINCT * FROM Format WHERE songId = :songId")
    fun findBySongId( songId: String ): Flow<Format?>

    /**
     * Attempt to write [format] into database.
     *
     * If [format] exist (determined by its primary key),
     * existing record's columns will be replaced
     * by provided [format]' data.
     *
     * @param format data intended to insert in to database
     */
    @Upsert
    fun upsert( format: Format )

    /**
     * @return stored [Format.contentLength] of song with id [songId], `0` otherwise
     */
    @Query("""
        SELECT COALESCE(
            (
                SELECT contentLength
                FROM Format
                WHERE songId = :songId
            ),
            0
        )
    """)
    fun findContentLengthOf( songId: String ): Flow<Long>

    /**
     * Set [Format.contentLength] of song with id [songId] to [contentLength]
     *
     * @return number of rows affected by this operation
     */
    @Query("UPDATE Format SET contentLength = :contentLength WHERE songId = :songId")
    fun updateContentLengthOf( songId: String, contentLength: Long = 0L ): Int

    //<editor-fold defaultstate="collapsed" desc="Sort all with songs">
    fun sortAllWithSongsByPlayTime( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<FormatWithSong>> =
        allWithSongs( limit, excludeHidden ).map { list ->
            list.sortedBy { it.song.totalPlayTimeMs }
        }

    fun sortAllWithSongsByRelativePlayTime( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<FormatWithSong>> =
        allWithSongs( limit, excludeHidden ).map { list ->
            list.sortedBy { it.song.relativePlayTime() }
        }

    fun sortAllWithSongsByTitle( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<FormatWithSong>> =
        allWithSongs( limit, excludeHidden ).map { list ->
            list.sortedBy { it.song.cleanTitle() }
        }

    @Query("""
        SELECT DISTINCT F.*, S.*
        FROM Format F
        JOIN Song S ON S.id = F.songId
        LEFT JOIN Event E ON E.songId = F.songId 
        WHERE totalPlayTimeMs >= :excludeHidden
        ORDER BY E.timestamp
        LIMIT :limit
    """)
    fun sortAllWithSongsByDatePlayed( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<FormatWithSong>>

    fun sortAllWithSongsByLikedAt( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<FormatWithSong>> =
        allWithSongs( limit, excludeHidden ).map { list ->
            list.sortedBy { it.song.likedAt }
        }

    fun sortAllWithSongsByArtist( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<FormatWithSong>> =
        allWithSongs( limit, excludeHidden ).map { list ->
            list.sortedBy { it.song.cleanArtistsText() }
        }

    fun sortAllWithSongsByDuration( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<FormatWithSong>> =
        allWithSongs( limit, excludeHidden ).map { list ->
            list.sortedBy {
                durationToMillis( it.song.durationText ?: "0:0" )
            }
        }

    @Query("""
        SELECT DISTINCT F.*, S.*
        FROM Format F
        JOIN Song S ON S.id = F.songId
        LEFT JOIN SongAlbumMap sam ON sam.songId = S.id
        LEFT JOIN Album A ON A.id = sam.albumId
        WHERE totalPlayTimeMs >= :excludeHidden
        ORDER BY 
            CASE 
                WHEN A.title LIKE '$MODIFIED_PREFIX%' THEN SUBSTR(A.title, LENGTH('$MODIFIED_PREFIX') + 1)
                ELSE A.title
            END
        LIMIT :limit
    """)
    fun sortAllWithSongsByAlbumName( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<FormatWithSong>>

    /**
     * Fetch all formats & songs from the database and sort them
     * according to [sortBy] and [sortOrder] based on Song's properties.
     * It also excludes songs if condition of [excludeHidden] is met.
     *
     * [sortBy] sorts all based on each song's property
     * such as [SongSortBy.Title], [SongSortBy.PlayTime], etc.
     * While [sortOrder] arranges order of sorted songs
     * to follow alphabetical order A to Z, or numerical order 0 to 9, etc.
     *
     * [excludeHidden] is an optional parameter that indicates
     * whether the final results contain songs that are hidden
     * (in)directly by the user.
     * `-1` shows hidden while `0` does not.
     *
     * @param sortBy which song's property is used to sort
     * @param sortOrder what order should results be in
     * @param excludeHidden whether to include hidden songs in final results or not
     *
     * @return a **SORTED** list of [Song]s that are continuously
     * updated to reflect changes within the database - wrapped by [Flow]
     *
     * @see SongSortBy
     * @see SortOrder
     */
    fun sortAllWithSongs(
        sortBy: SongSortBy,
        sortOrder: SortOrder,
        limit: Int = Int.MAX_VALUE,
        excludeHidden: Boolean = false
    ): Flow<List<FormatWithSong>> = when( sortBy ){
        SongSortBy.PlayTime         -> sortAllWithSongsByPlayTime( limit, excludeHidden )
        SongSortBy.RelativePlayTime -> sortAllWithSongsByRelativePlayTime( limit, excludeHidden )
        SongSortBy.Title            -> sortAllWithSongsByTitle( limit, excludeHidden )
        SongSortBy.DateAdded        -> allWithSongs( limit, excludeHidden )      // Already sorted by ROWID
        SongSortBy.DatePlayed       -> sortAllWithSongsByDatePlayed( limit, excludeHidden )
        SongSortBy.DateLiked        -> sortAllWithSongsByLikedAt( limit, excludeHidden )
        SongSortBy.Artist           -> sortAllWithSongsByArtist( limit, excludeHidden )
        SongSortBy.Duration         -> sortAllWithSongsByDuration( limit, excludeHidden )
        SongSortBy.AlbumName        -> sortAllWithSongsByAlbumName( limit, excludeHidden )
        SongSortBy.RANDOM           -> allWithSongsRandomized()
    }.map( sortOrder::applyTo )
    //</editor-fold>
}