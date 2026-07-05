package app.kreate.internal.database.repositories

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.RoomRawQuery
import app.kreate.constant.SongSortBy
import app.kreate.constant.SortOrder
import app.kreate.database.models.Song
import app.kreate.database.repositories.SongTable
import app.kreate.util.MODIFIED_PREFIX
import app.kreate.util.toDuration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take


@Dao
@RewriteQueriesToDropUnusedColumns
abstract class AbstractSongTable: SongTable {

    override val tableName: String
        get() = "songs"

    @Query("""
        SELECT DISTINCT * 
        FROM songs 
        WHERE total_playtime >= :excludeHidden
        ORDER BY ROWID 
        LIMIT :limit
    """)
    abstract override fun all( limit: Int, excludeHidden: Boolean ): Flow<List<Song>>

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

    @Query("""
        SELECT DISTINCT * 
        FROM songs 
        WHERE total_playtime >= :excludeHidden
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    abstract override fun allRandomized( limit: Int, excludeHidden: Boolean ): Flow<List<Song>>

    @Query("""
        SELECT DISTINCT * 
        FROM songs 
        WHERE is_local = 1
        LIMIT :limit
    """)
    abstract override fun allOnDevice( limit: Int ): Flow<List<Song>>

    @Query("""
        SELECT DISTINCT * 
        FROM songs 
        WHERE liked_at IS NOT NULL AND liked_at > 0
        ORDER BY ROWID
        LIMIT :limit
    """)
    abstract override fun allFavorites( limit: Int ): Flow<List<Song>>

    @Query("""
        SELECT DISTINCT * 
        FROM songs 
        WHERE liked_at IS NOT NULL AND liked_at > 0
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    abstract override fun allFavoritesRandomized( limit: Int ): Flow<List<Song>>

    @Query("""
        SELECT DISTINCT * 
        FROM songs 
        WHERE liked_at IS NOT NULL AND liked_at < 0
        ORDER BY ROWID
        LIMIT :limit
    """)
    abstract override fun allDisliked( limit: Int ): Flow<List<Song>>

    @Query("DELETE FROM songs WHERE total_playtime = 0")
    abstract override fun clearHiddenSongs(): Int

    @Query("SELECT DISTINCT * FROM songs WHERE id = :songId")
    abstract override fun findById( songId: String ): Flow<Song?>

    @Query("""
        SELECT DISTINCT * 
        FROM songs 
        WHERE title LIKE '%' || :searchTerm || '%' COLLATE NOCASE
        OR artists LIKE '%' || :searchTerm || '%' COLLATE NOCASE
    """)
    abstract override fun findAllTitleArtistContains( searchTerm: String ): Flow<List<Song>>

    @Query("""
        SELECT DISTINCT * 
        FROM songs 
        WHERE trim(
            CASE 
                WHEN artists LIKE '$MODIFIED_PREFIX%' THEN SUBSTR(artists, LENGTH('$MODIFIED_PREFIX') + 1)
                ELSE artists
            END
        ) COLLATE NOCASE = trim(:artistName) COLLATE NOCASE
    """)
    abstract override fun findAllByArtist( artistName: String ): Flow<List<Song>>

    @Query("SELECT COUNT(*) > 0 FROM songs WHERE id = :songId")
    abstract override fun exists( songId: String ): Flow<Boolean>

    @Query("""
        SELECT COALESCE(
            (
                SELECT liked_at IS NOT NULL AND liked_at > 0
                FROM songs 
                WHERE id = :songId
            )
            , 0
        ) 
    """)
    abstract override fun isLiked( songId: String ): Flow<Boolean>

    @Query("SELECT is_local FROM songs WHERE id = :songId")
    abstract override fun isLocal( songId: String ): Flow<Boolean>

    @Query("""
        SELECT 
            CASE 
                WHEN liked_at > 0 THEN 1 
                WHEN liked_at < 0 THEN 0 
                ELSE NULL 
            END 
        FROM songs 
        WHERE id = :songId
    """)
    abstract override fun likeState( songId: String ): Flow<Boolean?>

    @Query("""
        UPDATE songs  
        SET liked_at = 
            CASE  
                WHEN liked_at = -1 THEN NULL
                WHEN liked_at IS NULL THEN strftime('%s','now') * 1000
                ELSE -1
            END  
        WHERE id = :songId
    """)
    abstract override fun rotateLikeState( songId: String ): Int

    @Query("""
        UPDATE songs
        SET liked_at = 
            CASE 
                WHEN liked_at IS NOT NULL THEN NULL
                ELSE strftime('%s', 'now') * 1000
            END
        WHERE id = :songId
    """)
    abstract override fun toggleLike( songId: String ): Int

    @Query("""
        UPDATE songs
        SET liked_at = 
            CASE
                WHEN :likeState = 0 THEN -1
                WHEN :likeState = 1 THEN strftime('%s', 'now') * 1000 
                ELSE :likeState 
            END
        WHERE id = :songId
    """)
    abstract override fun likeState( songId: String, likeState: Boolean? ): Int

    @Query("UPDATE songs SET title = :title WHERE id = :songId")
    abstract override fun updateTitle( songId: String, title: String ): Int

    @Query("UPDATE songs SET artists = :artistsText WHERE id = :songId")
    abstract override fun updateArtists( songId: String, artistsText: String ): Int

    @Query("UPDATE songs SET thumbnail_url = :thumbnailUrl WHERE id = :songId")
    abstract override fun updateThumbnail( songId: String, thumbnailUrl: String? ): Int

    @Query(
        """
        UPDATE songs 
        SET total_playtime = 
            CASE
                WHEN :isIncrement = 0 THEN :value
                ELSE total_playtime + :value
            END
        WHERE id = :songId
    """)
    abstract override fun updateTotalPlayTime( songId: String, value: Long, isIncrement: Boolean ): Int

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
        FROM songs S 
        LEFT JOIN playback_history E ON E.song_id = S.id 
        WHERE total_playtime >= :excludeHidden
        ORDER BY E.created_at
        LIMIT :limit
    """)
    abstract fun sortAllByDatePlayed( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<Song>>

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
        FROM songs S
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
    abstract fun sortAllByAlbumName( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<Song>>

    override fun sortAll(
        sortBy: SongSortBy,
        sortOrder: SortOrder,
        limit: Int,
        excludeHidden: Boolean
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
        FROM songs S 
        LEFT JOIN playback_history E ON E.song_id = S.id 
        WHERE liked_at IS NOT NULL AND liked_at > 0
        ORDER BY E.created_at
        LIMIT :limit
    """)
    abstract fun sortFavoritesByDatePlayed( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    fun sortFavoritesByDuration( limit: Int = Int.MAX_VALUE ): Flow<List<Song>> =
        allFavorites( limit ).map { list ->
            list.sortedBy { it.durationText.toDuration() }
        }

    @Query("""
        SELECT DISTINCT S.*
        FROM songs S
        LEFT JOIN song_album_map sam ON sam.song_id = S.id
        LEFT JOIN albums A ON A.id = sam.album_id
        WHERE liked_at IS NOT NULL AND liked_at > 0
        ORDER BY 
            CASE 
                WHEN A.title LIKE '$MODIFIED_PREFIX%' THEN SUBSTR(A.title, LENGTH('$MODIFIED_PREFIX') + 1)
                ELSE A.title
            END
        LIMIT :limit
    """)
    abstract fun sortFavoritesByAlbumName( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    override fun sortFavorites(
        sortBy: SongSortBy,
        sortOrder: SortOrder,
        limit: Int
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