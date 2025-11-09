package app.kreate.database.table

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.RoomRawQuery
import app.kreate.constant.SongSortBy
import app.kreate.constant.SortOrder
import app.kreate.database.models.Song
import app.kreate.util.MODIFIED_PREFIX
import app.kreate.util.toDuration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import org.jetbrains.annotations.Blocking

@Dao
@RewriteQueriesToDropUnusedColumns
interface SongTable: DatabaseTable<Song> {

    override val tableName: String
        get() = Song::class.simpleName!!

    /**
     * @return all records from this table
     */
    @Query("""
        SELECT DISTINCT * 
        FROM Song 
        WHERE totalPlayTimeMs >= :excludeHidden
        ORDER BY ROWID 
        LIMIT :limit
    """)
    fun all(
        limit: Int = Int.MAX_VALUE,
        excludeHidden: Boolean = false
    ): Flow<List<Song>>

    /**
     * This will block current thread in till it's finished.
     *
     * @return all songs that aren't disliked
     */
    @Blocking
    override fun blockingAll( limit: Int ): List<Song> {
        val statement = RoomRawQuery("""
            SELECT DISTINCT *
            FROM $tableName 
            WHERE totalPlayTimeMs >= 0 
            ORDER BY ROWID 
            LIMIT $limit
        """.trimIndent())
        return blockingGet( statement )
    }

    /**
     * @return all records from this table in randomized order
     */
    @Query("""
        SELECT DISTINCT * 
        FROM Song 
        WHERE totalPlayTimeMs >= :excludeHidden
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    fun allRandomized(
        limit: Int = Int.MAX_VALUE,
        excludeHidden: Boolean = false
    ): Flow<List<Song>>

    /**
     * @return all records that have [Song.isLocal] equal to true
     */
    @Query("""
        SELECT DISTINCT * 
        FROM Song 
        WHERE is_local = 1
        LIMIT :limit
    """)
    fun allOnDevice( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @return all songs that were liked by user
     */
    @Query("""
        SELECT DISTINCT * 
        FROM Song 
        WHERE likedAt IS NOT NULL AND likedAt > 0
        ORDER BY ROWID
        LIMIT :limit
    """)
    fun allFavorites( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @return all songs that were liked by user in randomized order
     */
    @Query("""
        SELECT DISTINCT * 
        FROM Song 
        WHERE likedAt IS NOT NULL AND likedAt > 0
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    fun allFavoritesRandomized( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    @Query("""
        SELECT DISTINCT * 
        FROM Song 
        WHERE likedAt IS NOT NULL AND likedAt < 0
        ORDER BY ROWID
        LIMIT :limit
    """)
    fun allDisliked( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * Delete all songs with [Song.totalPlayTimeMs] equal to `0`
     *
     * @return number of rows affected by this operation
     */
    @Query("DELETE FROM Song WHERE totalPlayTimeMs = 0")
    fun clearHiddenSongs(): Int

    /**
     * @param songId of album to look for
     *
     * @return [Song] that has [Song.id] matches [songId]
     */
    @Query("SELECT DISTINCT * FROM Song WHERE id = :songId")
    fun findById( songId: String ): Flow<Song?>

    /**
     * [searchTerm] appears in [Song.title] or [Song.artistsText].
     * Additionally, it's **case-insensitive**
     *
     * I.E.: `name` matches `1name_to` and `1_NaMe_to`
     *
     * @param searchTerm what to look for
     * @return all [Song]s that have [Song.title] or [Song.artistsText] contain [searchTerm]
     */
    @Query("""
        SELECT DISTINCT * 
        FROM Song 
        WHERE title LIKE '%' || :searchTerm || '%' COLLATE NOCASE
        OR artistsText LIKE '%' || :searchTerm || '%' COLLATE NOCASE
    """)
    fun findAllTitleArtistContains( searchTerm: String ): Flow<List<Song>>

    /**
     * Require [artistName] to match [Song.artistsText], except for case-sensitive.
     *
     * I.E.: `Michael` matches both `michael` and `MICHAEL`
     *
     * Additionally, [MODIFIED_PREFIX] is removed before comparision.
     *
     * @param artistName [Song.artistsText] to look for
     * @return all [Song]s that have [Song.artistsText] match [artistName]
     */
    @Query("""
        SELECT DISTINCT * 
        FROM Song 
        WHERE trim(
            CASE 
                WHEN artistsText LIKE '$MODIFIED_PREFIX%' THEN SUBSTR(artistsText, LENGTH('$MODIFIED_PREFIX') + 1)
                ELSE artistsText
            END
        ) COLLATE NOCASE = trim(:artistName) COLLATE NOCASE
    """)
    fun findAllByArtist( artistName: String ): Flow<List<Song>>

    /**
     * @return whether any record in [Song] table has id [songId]
     */
    @Query("SELECT COUNT(*) > 0 FROM Song WHERE id = :songId")
    fun exists( songId: String ): Flow<Boolean>

    /**
     * Should not be called when you have a hold of [Song].
     *
     * The return value is **null safe**. When [Song.id] is
     * not found in database, it returns `false`
     *
     * @param songId of song to query
     * @return whether [Song.likedAt] is set
     */
    @Query("""
        SELECT COALESCE(
            (
                SELECT likedAt IS NOT NULL AND likedAt > 0
                FROM Song 
                WHERE id = :songId
            )
            , 0
        ) 
    """)
    fun isLiked( songId: String ): Flow<Boolean>

    /**
     * A tri-state represents 3 different states of like.
     *
     * - `true` - when song is **liked**
     * - `false` - when song is **disliked**
     * - `null` - if value is **unset** (neutral)
     *
     * @param songId of song to query
     * @return value represent [Song.likedAt] state
     */
    @Query("""
        SELECT 
            CASE 
                WHEN likedAt > 0 THEN 1 
                WHEN likedAt < 0 THEN 0 
                ELSE NULL 
            END 
        FROM Song 
        WHERE id = :songId
    """)
    fun likeState( songId: String ): Flow<Boolean?>

    /**
     * This query updates the [Song.likedAt] column to
     * cycle through three values in a fixed rotation:
     *
     * - `-1` to `null`
     * - `null` to [System.currentTimeMillis]
     * - `1` to `-1`
     *
     * @param songId of song to be updated
     * @return number of rows affected by this operation
     */
    @Query("""
        UPDATE Song  
        SET likedAt = 
            CASE  
                WHEN likedAt = -1 THEN NULL
                WHEN likedAt IS NULL THEN strftime('%s','now') * 1000
                ELSE -1
            END  
        WHERE id = :songId
    """)
    fun rotateLikeState( songId: String ): Int

    /**
     * ### If song **IS NOT** liked
     *
     * Set [Song.likedAt] to current time
     *
     * ### If song **IS** liked
     *
     * Set [Song.likedAt] to `null`
     *
     * @return number of rows affected
     */
    @Query("""
        UPDATE Song
        SET likedAt = 
            CASE 
                WHEN likedAt IS NOT NULL THEN NULL
                ELSE strftime('%s', 'now') * 1000
            END
        WHERE id = :songId
    """)
    fun toggleLike( songId: String ): Int

    /**
     * Set [Song.likedAt] according to provided [likeState]
     *
     * - `false` is dislike
     * - `null` is neutral
     * - `true` is like
     *
     * @param songId  of song to be updated
     * @return number of rows affected
     */
    @Query("""
        UPDATE Song
        SET likedAt = 
            CASE
                WHEN :likeState = 0 THEN -1
                WHEN :likeState = 1 THEN strftime('%s', 'now') * 1000 
                ELSE :likeState 
            END
        WHERE id = :songId
    """)
    fun likeState( songId: String, likeState: Boolean? ): Int

    /**
     * @param songId identifier of [Song]
     * @param title new name of this song
     *
     * @return number of albums affected by this operation
     */
    @Query("UPDATE Song SET title = :title WHERE id = :songId")
    fun updateTitle( songId: String, title: String ): Int

    /**
     * @param songId identifier of [Song]
     * @param artistsText artists to display
     *
     * @return number of albums affected by this operation
     */
    @Query("UPDATE Song SET artistsText = :artistsText WHERE id = :songId")
    fun updateArtists( songId: String, artistsText: String ): Int

    /**
     * Set [Song.totalPlayTimeMs] to:
     * - [value] if [isIncrement] is `false`
     * - Sum of [Song.totalPlayTimeMs] and [value] if [isIncrement] is `true`
     *
     * @param songId identifier of song to update
     * @param value value to add/set
     * @param isIncrement whether to hard set or add to existing value
     *
     * @return number of rows affected by this operation
     */
    @Query(
        """
        UPDATE Song 
        SET totalPlayTimeMs = 
            CASE
                WHEN :isIncrement = 0 THEN :value
                ELSE totalPlayTimeMs + :value
            END
        WHERE id = :songId
    """
    )
    fun updateTotalPlayTime( songId: String, value: Long, isIncrement: Boolean = false ): Int

    //<editor-fold defaultstate="collapsed" desc="Sort all">
    fun sortAllByPlayTime( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<Song>> =
        all( limit, excludeHidden ).map { list ->
            list.sortedBy( Song::totalPlayTimeMs )
        }

    fun sortAllByRelativePlayTime( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<Song>> =
        all( limit, excludeHidden ).map { list ->
            list.sortedBy( Song::relativePlayTime )
        }

    fun sortAllByTitle( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<Song>> =
        all( limit, excludeHidden ).map { list ->
            list.sortedBy( Song::cleanTitle )
        }

    @Query("""
        SELECT DISTINCT S.* 
        FROM Song S 
        LEFT JOIN Event E ON E.songId = S.id 
        WHERE totalPlayTimeMs >= :excludeHidden
        ORDER BY E.timestamp
        LIMIT :limit
    """)
    fun sortAllByDatePlayed( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<Song>>

    fun sortAllByLikedAt( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<Song>> =
        all( limit, excludeHidden ).map { list ->
            list.sortedBy( Song::likedAt )
        }

    fun sortAllByArtist( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<Song>> =
        all( limit, excludeHidden ).map { list ->
            list.sortedBy( Song::cleanArtistsText )
        }

    fun sortAllByDuration( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<Song>> =
        all( limit, excludeHidden ).map { list ->
            list.sortedBy { it.durationText.toDuration() }
        }

    @Query("""
        SELECT DISTINCT S.*
        FROM Song S
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
    fun sortAllByAlbumName( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<Song>>

    /**
     * Fetch all songs from the database and sort them
     * according to [sortBy] and [sortOrder]. It also
     * excludes songs if condition of [excludeHidden] is met.
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
    fun sortAll(
        sortBy: SongSortBy,
        sortOrder: SortOrder,
        limit: Int = Int.MAX_VALUE,
        excludeHidden: Boolean = false
    ): Flow<List<Song>> = when( sortBy ){
        SongSortBy.TOTAL_PLAY_TIME      -> sortAllByPlayTime( limit, excludeHidden )
        SongSortBy.RELATIVE_PLAY_TIME   -> sortAllByRelativePlayTime( limit, excludeHidden )
        SongSortBy.TITLE                -> sortAllByTitle( limit, excludeHidden )
        SongSortBy.DATE_ADDED           -> all( limit, excludeHidden )      // Already sorted by ROWID
        SongSortBy.DATE_PLAYED          -> sortAllByDatePlayed( limit, excludeHidden )
        SongSortBy.DATE_LIKED           -> sortAllByLikedAt( limit, excludeHidden )
        SongSortBy.ARTIST               -> sortAllByArtist( limit, excludeHidden )
        SongSortBy.DURATION             -> sortAllByDuration( limit, excludeHidden )
        SongSortBy.ALBUM                -> sortAllByAlbumName( limit, excludeHidden )
        SongSortBy.RANDOM               -> allRandomized( limit, excludeHidden )
    }.map( sortOrder::applyTo )
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Sort favorites">
    fun sortFavoritesByArtist( limit: Int = Int.MAX_VALUE ): Flow<List<Song>> =
        allFavorites( limit ).map { list ->
            list.sortedBy( Song::cleanArtistsText )
        }

    fun sortFavoritesByPlayTime( limit: Int = Int.MAX_VALUE ): Flow<List<Song>> =
        allFavorites( limit ).map { list ->
            list.sortedBy( Song::totalPlayTimeMs )
        }

    fun sortFavoritesByRelativePlayTime( limit: Int = Int.MAX_VALUE ): Flow<List<Song>> =
        allFavorites( limit ).map { list ->
            list.sortedBy( Song::relativePlayTime )
        }

    fun sortFavoritesByTitle( limit: Int = Int.MAX_VALUE ): Flow<List<Song>> =
        allFavorites( limit ).map { list ->
            list.sortedBy( Song::cleanTitle )
        }

    fun sortFavoritesByLikedAt( limit: Int = Int.MAX_VALUE ): Flow<List<Song>> =
        allFavorites( limit ).map { list ->
            list.sortedBy( Song::likedAt )
        }

    @Query("""
        SELECT DISTINCT S.* 
        FROM Song S 
        LEFT JOIN Event E ON E.songId = S.id 
        WHERE likedAt IS NOT NULL AND likedAt > 0
        ORDER BY E.timestamp
        LIMIT :limit
    """)
    fun sortFavoritesByDatePlayed( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    fun sortFavoritesByDuration( limit: Int = Int.MAX_VALUE ): Flow<List<Song>> =
        allFavorites( limit ).map { list ->
            list.sortedBy { it.durationText.toDuration() }
        }

    @Query("""
        SELECT DISTINCT S.*
        FROM Song S
        LEFT JOIN SongAlbumMap sam ON sam.songId = S.id
        LEFT JOIN Album A ON A.id = sam.albumId
        WHERE likedAt IS NOT NULL AND likedAt > 0
        ORDER BY 
            CASE 
                WHEN A.title LIKE '$MODIFIED_PREFIX%' THEN SUBSTR(A.title, LENGTH('$MODIFIED_PREFIX') + 1)
                ELSE A.title
            END
        LIMIT :limit
    """)
    fun sortFavoritesByAlbumName( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * Fetch all favorite songs and sort them according to [sortBy] and [sortOrder].
     *
     * [sortBy] sorts all based on each song's property
     * such as [SongSortBy.Title], [SongSortBy.PlayTime], etc.
     * While [sortOrder] arranges order of sorted songs
     * to follow alphabetical order A to Z, or numerical order 0 to 9, etc.
     *
     * @param sortBy which song's property is used to sort
     * @param sortOrder what order should results be in
     * @param limit stop query once number of results reaches this number
     *
     * @return a **SORTED** list of [Song]s that are continuously
     * updated to reflect changes within the database - wrapped by [Flow]
     *
     * @see SongSortBy
     * @see SortOrder
     */
    fun sortFavorites(
        sortBy: SongSortBy,
        sortOrder: SortOrder,
        limit: Int = Int.MAX_VALUE
    ): Flow<List<Song>> = when( sortBy ) {
        SongSortBy.TOTAL_PLAY_TIME      -> sortFavoritesByPlayTime()
        SongSortBy.RELATIVE_PLAY_TIME   -> sortFavoritesByRelativePlayTime()
        SongSortBy.TITLE                -> sortFavoritesByTitle()
        SongSortBy.DATE_ADDED           -> allFavorites()      // Already sorted by ROWID
        SongSortBy.DATE_PLAYED          -> sortFavoritesByDatePlayed()
        SongSortBy.DATE_LIKED           -> sortFavoritesByLikedAt()
        SongSortBy.ARTIST               -> sortFavoritesByArtist()
        SongSortBy.DURATION             -> sortFavoritesByDuration()
        SongSortBy.ALBUM                -> sortFavoritesByAlbumName()
        SongSortBy.RANDOM               -> allFavoritesRandomized()
    }.map( sortOrder::applyTo ).take( limit )
    //</editor-fold>
}