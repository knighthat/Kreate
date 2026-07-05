package app.kreate.internal.database.repositories

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.kreate.constant.AlbumSortBy
import app.kreate.constant.SortOrder
import app.kreate.database.models.Album
import app.kreate.database.models.Song
import app.kreate.database.repositories.AlbumTable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take


@Dao
@RewriteQueriesToDropUnusedColumns
abstract class AbstractAlbumTable: AlbumTable {

    override val tableName: String
        get() = "albums"

    @Query("""
        SELECT DISTINCT *
        FROM albums
        WHERE bookmarked_at IS NOT NULL
        ORDER BY ROWID
        LIMIT :limit
    """)
    abstract override fun allBookmarked( limit: Int ): Flow<List<Album>>

    @Query("""
        SELECT DISTINCT *
        FROM albums
        WHERE bookmarked_at IS NOT NULL
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    abstract override fun allBookmarkedRandomized( limit: Int ): Flow<List<Album>>

    @Query("""
        SELECT DISTINCT A.*
        FROM albums A
        JOIN song_album_map sam ON sam.album_id = A.id
        JOIN song_playlist_map spm ON spm.song_id = sam.song_id 
        ORDER BY A.ROWID
        LIMIT :limit
    """)
    abstract override fun allInLibrary( limit: Int ): Flow<List<Album>>

    @Query("""
        SELECT DISTINCT A.*
        FROM albums A
        JOIN song_album_map sam ON sam.album_id = A.id
        JOIN song_playlist_map spm ON spm.song_id = sam.song_id 
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    abstract override fun allInLibraryRandomized( limit: Int ): Flow<List<Album>>

    @Query("""
        SELECT DISTINCT S.*
        FROM song_album_map sam
        JOIN albums A ON A.id = sam.album_id
        JOIN songs S ON S.id = sam.song_id
        WHERE A.bookmarked_at IS NOT NULL
        ORDER BY S.ROWID
        LIMIT :limit
    """)
    abstract override fun allSongsInBookmarked( limit: Int ): Flow<List<Song>>

    @Query("SELECT DISTINCT * FROM albums WHERE id = :albumId")
    abstract override fun findById( albumId: String ): Flow<Album?>

    @Query("""
        SELECT albums.*
        FROM song_album_map 
        JOIN albums ON id = album_id
        WHERE song_id = :songId
    """)
    abstract override fun findBySongId( songId: String ): Flow<Album?>

    @Query("""
        SELECT 
            CASE
                WHEN bookmarked_at IS NOT NULL THEN 1   
                ELSE 0
            END
        FROM albums
        WHERE id = :albumId 
    """)
    abstract override fun isBookmarked( albumId: String ): Flow<Boolean>

    @Query("""
        UPDATE albums
        SET bookmarked_at = 
            CASE 
                WHEN bookmarked_at IS NULL THEN strftime('%s', 'now') * 1000
                ELSE NULL
            END
        WHERE id = :albumId
    """)
    abstract override fun toggleBookmark( albumId: String ): Int

    @Query("UPDATE albums SET thumbnail_url = :thumbnailUrl WHERE id = :albumId")
    abstract override fun updateCover( albumId: String, thumbnailUrl: String ): Int

    @Query("UPDATE albums SET artists = :authors WHERE id = :albumId")
    abstract override fun updateAuthors( albumId: String, authors: String ): Int

    @Query("UPDATE albums SET title = :title WHERE id = :albumId")
    abstract override fun updateTitle( albumId: String, title: String ): Int

    //<editor-fold defaultstate="collapsed" desc="Sort bookmarked">
    fun sortBookmarkedByTitle( limit: Int = Int.MAX_VALUE ): Flow<List<Album>> =
        allBookmarked( limit ).map { list ->
            list.sortedBy( Album::cleanTitle )
        }

    fun sortBookmarkedByYear( limit: Int = Int.MAX_VALUE ): Flow<List<Album>> =
        allBookmarked( limit ).map { list ->
            list.sortedBy( Album::year )
        }

    fun sortBookmarkedByArtist( limit: Int = Int.MAX_VALUE ): Flow<List<Album>> =
        allBookmarked( limit ).map { list ->
            list.sortedBy( Album::cleanAuthorsText )
        }

    @Query("""
        SELECT DISTINCT A.* 
        FROM albums A
        JOIN song_album_map sam ON sam.album_id = A.id 
        WHERE A.bookmarked_at IS NOT NULL
        GROUP BY A.id
        ORDER BY COUNT(sam.song_id)
        LIMIT :limit
    """)
    abstract fun sortBookmarkedBySongsCount( limit: Int = Int.MAX_VALUE ): Flow<List<Album>>

    @Query("""
        SELECT DISTINCT A.*
        FROM albums A
        JOIN song_album_map sam ON sam.album_id = A.id
        JOIN songs S ON S.id = sam.song_id
        WHERE A.bookmarked_at IS NOT NULL
        GROUP BY A.id
        ORDER BY SUM(
            CASE 
                WHEN S.duration LIKE '%:%:%' THEN (
                    (SUBSTR(S.duration, 1, INSTR(S.duration, ':') - 1) * 3600) + 
                    (SUBSTR(S.duration, INSTR(S.duration, ':') + 1, INSTR(SUBSTR(S.duration, INSTR(S.duration, ':') + 1), ':') - 1) * 60) + 
                    SUBSTR(S.duration, INSTR(S.duration, ':') + INSTR(SUBSTR(S.duration, INSTR(S.duration, ':') + 1), ':') + 1)
                )
                ELSE (
                    (SUBSTR(S.duration, 1, INSTR(S.duration, ':') - 1) * 60) + 
                    (SUBSTR(S.duration, INSTR(S.duration, ':') + 1))
                )
            END 
        )
        LIMIT :limit
    """)
    // Duration conversion is baked into SQL syntax to reduce code complexity
    // at the cost of unfriendly syntax, potentially makes it harder to maintain or reuse.
    abstract fun sortBookmarkedByDuration( limit: Int = Int.MAX_VALUE ): Flow<List<Album>>

    override fun sortBookmarked(
        sortBy: AlbumSortBy,
        sortOrder: SortOrder,
        limit: Int
    ): Flow<List<Album>> = when( sortBy ) {
        AlbumSortBy.TITLE           -> sortBookmarkedByTitle()
        AlbumSortBy.YEAR            -> sortBookmarkedByYear()
        AlbumSortBy.DATE_ADDED      -> allBookmarked()       // Already sorted by ROWID
        AlbumSortBy.ARTIST          -> sortBookmarkedByArtist()
        AlbumSortBy.SONGS_COUNT     -> sortBookmarkedBySongsCount()
        AlbumSortBy.TOTAL_DURATION  -> sortBookmarkedByDuration()
        AlbumSortBy.RANDOM          -> allBookmarkedRandomized()
    }.map( sortOrder::applyTo ).take( limit )
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Sort albums in library">
    fun sortInLibraryByTitle( limit: Int = Int.MAX_VALUE ): Flow<List<Album>> =
        allInLibrary( limit ).map { list ->
            list.sortedBy( Album::cleanTitle )
        }

    fun sortInLibraryByYear( limit: Int = Int.MAX_VALUE ): Flow<List<Album>> =
        allInLibrary( limit ).map { list ->
            list.sortedBy( Album::year )
        }

    fun sortInLibraryByArtist( limit: Int = Int.MAX_VALUE ): Flow<List<Album>> =
        allInLibrary( limit ).map { list ->
            list.sortedBy( Album::cleanAuthorsText )
        }

    @Query("""
        SELECT DISTINCT A.*
        FROM song_playlist_map spm
        JOIN song_album_map sam ON sam.song_id = spm.song_id
        JOIN albums A ON A.id = sam.album_id
        GROUP BY A.id
        ORDER BY COUNT(sam.song_id)
        LIMIT :limit
    """)
    abstract fun sortInLibraryBySongsCount( limit: Int = Int.MAX_VALUE ): Flow<List<Album>>

    @Query("""
        SELECT DISTINCT A.*
        FROM song_playlist_map spm
        JOIN song_album_map sam ON sam.song_id = spm.song_id
        JOIN albums A ON A.id = sam.album_id
        JOIN songs S ON S.id = sam.song_id
        GROUP BY A.id
        ORDER BY SUM(
            CASE 
                WHEN S.duration LIKE '%:%:%' THEN (
                    (SUBSTR(S.duration, 1, INSTR(S.duration, ':') - 1) * 3600) + 
                    (SUBSTR(S.duration, INSTR(S.duration, ':') + 1, INSTR(SUBSTR(S.duration, INSTR(S.duration, ':') + 1), ':') - 1) * 60) + 
                    SUBSTR(S.duration, INSTR(S.duration, ':') + INSTR(SUBSTR(S.duration, INSTR(S.duration, ':') + 1), ':') + 1)
                )
                ELSE (
                    (SUBSTR(S.duration, 1, INSTR(S.duration, ':') - 1) * 60) + 
                    (SUBSTR(S.duration, INSTR(S.duration, ':') + 1))
                )
            END 
        )
        LIMIT :limit
    """)
    abstract fun sortInLibraryByDuration( limit: Int = Int.MAX_VALUE ): Flow<List<Album>>

    override fun sortInLibrary(
        sortBy: AlbumSortBy,
        sortOrder: SortOrder,
        limit: Int
    ): Flow<List<Album>> = when( sortBy ) {
        AlbumSortBy.TITLE           -> sortInLibraryByTitle()
        AlbumSortBy.YEAR            -> sortInLibraryByYear()
        AlbumSortBy.DATE_ADDED      -> allInLibrary()        // Already sorted by ROWID
        AlbumSortBy.ARTIST          -> sortInLibraryByArtist()
        AlbumSortBy.SONGS_COUNT     -> sortInLibraryBySongsCount()
        AlbumSortBy.TOTAL_DURATION  -> sortInLibraryByDuration()
        AlbumSortBy.RANDOM          -> allInLibraryRandomized()
    }.map( sortOrder::applyTo ).take( 4 )
    //</editor-fold>
}