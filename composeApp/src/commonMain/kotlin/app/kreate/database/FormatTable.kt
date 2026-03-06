package app.kreate.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.kreate.constant.SongSortBy
import app.kreate.constant.SortOrder
import app.kreate.database.ext.FormatWithSong
import app.kreate.database.models.Format
import app.kreate.database.models.Song
import app.kreate.database.table.DatabaseTable
import app.kreate.util.MODIFIED_PREFIX
import app.kreate.util.toDuration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Dao
@RewriteQueriesToDropUnusedColumns
interface FormatTable: DatabaseTable<Format> {

    override val tableName: String
        get() = "formats"

    /**
     * @return formats & songs of this table
     */
    @Query("""
        SELECT DISTINCT F.*, S.* 
        FROM formats F
        JOIN songs S ON S.id = F.song_id
        WHERE total_playtime >= :excludeHidden
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
        FROM formats F
        JOIN songs S ON S.id = F.song_id
        WHERE total_playtime >= :excludeHidden
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
    @Query("DELETE FROM formats WHERE song_id IN (:songIds)")
    fun deleteBySongId( songIds: List<String> ): Int

    fun deleteBySongId( vararg songIds: String ): Int = deleteBySongId( songIds.toList() )

    /**
     * @param songId of song to look for
     * @return [Format] that has [Format.songId] matches [songId]
     */
    @Query("SELECT DISTINCT * FROM formats WHERE song_id = :songId")
    fun findBySongId( songId: String ): Flow<Format?>

    /**
     * @return stored [Format.contentLength] of song with id [songId], `0` otherwise
     */
    @Query("""
        SELECT COALESCE(
            (
                SELECT length
                FROM formats
                WHERE song_id = :songId
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
    @Query("UPDATE formats SET length = :contentLength WHERE song_id = :songId")
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
        FROM formats F
        JOIN songs S ON S.id = F.song_id
        LEFT JOIN playback_history E ON E.song_id = F.song_id 
        WHERE total_playtime >= :excludeHidden
        ORDER BY E.created_at
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
            list.sortedBy { it.song.durationText.toDuration() }
        }

    @Query("""
        SELECT DISTINCT F.*, S.*
        FROM formats F
        JOIN songs S ON S.id = F.song_id
        LEFT JOIN song_album_map sam ON sam.song_id = S.id
        LEFT JOIN albums A ON A.id = sam.album_id
        WHERE total_playtime >= :excludeHidden
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
        SongSortBy.TOTAL_PLAY_TIME      -> sortAllWithSongsByPlayTime( limit, excludeHidden )
        SongSortBy.RELATIVE_PLAY_TIME   -> sortAllWithSongsByRelativePlayTime( limit, excludeHidden )
        SongSortBy.TITLE                -> sortAllWithSongsByTitle( limit, excludeHidden )
        SongSortBy.DATE_ADDED           -> allWithSongs( limit, excludeHidden )      // Already sorted by ROWID
        SongSortBy.DATE_PLAYED          -> sortAllWithSongsByDatePlayed( limit, excludeHidden )
        SongSortBy.DATE_LIKED           -> sortAllWithSongsByLikedAt( limit, excludeHidden )
        SongSortBy.ARTIST               -> sortAllWithSongsByArtist( limit, excludeHidden )
        SongSortBy.DURATION             -> sortAllWithSongsByDuration( limit, excludeHidden )
        SongSortBy.ALBUM                -> sortAllWithSongsByAlbumName( limit, excludeHidden )
        SongSortBy.RANDOM               -> allWithSongsRandomized()
    }.map( sortOrder::applyTo )
    //</editor-fold>
}