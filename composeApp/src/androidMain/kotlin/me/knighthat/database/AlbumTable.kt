package me.knighthat.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.kreate.constant.SortOrder
import app.kreate.database.models.Album
import app.kreate.database.models.Song
import app.kreate.database.table.DatabaseTable
import it.fast4x.rimusic.enums.AlbumSortBy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

@Dao
@RewriteQueriesToDropUnusedColumns
interface AlbumTable: DatabaseTable<Album> {

    override val tableName: String
        get() = "albums"

    /**
     * @return all albums from this table that are bookmarked by user
     */
    @Query("""
        SELECT DISTINCT *
        FROM albums
        WHERE bookmarked_at IS NOT NULL
        ORDER BY ROWID
        LIMIT :limit
    """)
    fun allBookmarked( limit: Int = Int.MAX_VALUE ): Flow<List<Album>>

    /**
     * @return all albums from this table that are bookmarked by user in randomized order
     */
    @Query("""
        SELECT DISTINCT *
        FROM albums
        WHERE bookmarked_at IS NOT NULL
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    fun allBookmarkedRandomized( limit: Int = Int.MAX_VALUE ): Flow<List<Album>>

    /**
     * @return albums that have their songs mapped to at least 1 playlist
     */
    @Query("""
        SELECT DISTINCT A.*
        FROM albums A
        JOIN song_album_map sam ON sam.album_id = A.id
        JOIN song_playlist_map spm ON spm.song_id = sam.song_id 
        ORDER BY A.ROWID
        LIMIT :limit
    """)
    fun allInLibrary( limit: Int = Int.MAX_VALUE ): Flow<List<Album>>

    /**
     * @return albums that have their songs mapped to at least 1 playlist in randomized order
     */
    @Query("""
        SELECT DISTINCT A.*
        FROM albums A
        JOIN song_album_map sam ON sam.album_id = A.id
        JOIN song_playlist_map spm ON spm.song_id = sam.song_id 
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    fun allInLibraryRandomized( limit: Int = Int.MAX_VALUE ): Flow<List<Album>>

    /**
     * @return all songs of bookmarked albums
     */
    @Query("""
        SELECT DISTINCT S.*
        FROM song_album_map sam
        JOIN albums A ON A.id = sam.album_id
        JOIN songs S ON S.id = sam.song_id
        WHERE A.bookmarked_at IS NOT NULL
        ORDER BY S.ROWID
        LIMIT :limit
    """)
    fun allSongsInBookmarked( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @param albumId of album to look for
     * @return [Album] that has [Album.id] matches [albumId]
     */
    @Query("SELECT DISTINCT * FROM albums WHERE id = :albumId")
    fun findById( albumId: String ): Flow<Album?>

    /**
     * @return [Album] that has song with id [songId]
     */
    @Query("""
        SELECT albums.*
        FROM song_album_map 
        JOIN albums ON id = album_id
        WHERE song_id = :songId
    """)
    fun findBySongId( songId: String ): Flow<Album?>

    /**
     * @return whether [Album] with id [albumId] is bookmarked,
     * if album doesn't exist, return default value - `false`
     */
    @Query("""
        SELECT 
            CASE
                WHEN bookmarked_at IS NOT NULL THEN 1   
                ELSE 0
            END
        FROM albums
        WHERE id = :albumId 
    """)
    fun isBookmarked( albumId: String ): Flow<Boolean>

    /**
     * There are 2 possible actions.
     *
     * ### If album IS bookmarked
     *
     * This will remove [Album.bookmarkedAt] timestamp (replace with NULL)
     *
     * ## If album IS NOT bookmarked
     *
     * It will assign [Album.bookmarkedAt] with current time in millis
     *
     * @param albumId album identifier to update its [Album.bookmarkedAt]
     *
     * @return number of albums updated by this operation
     */
    @Query("""
        UPDATE albums
        SET bookmarked_at = 
            CASE 
                WHEN bookmarked_at IS NULL THEN strftime('%s', 'now') * 1000
                ELSE NULL
            END
        WHERE id = :albumId
    """)
    fun toggleBookmark( albumId: String ): Int

    /**
     * @param albumId identifier of [Album]
     * @param thumbnailUrl new url to thumbnail
     *
     * @return number of albums affected by this operation
     */
    @Query("UPDATE albums SET thumbnail_url = :thumbnailUrl WHERE id = :albumId")
    fun updateCover( albumId: String, thumbnailUrl: String ): Int

    /**
     * @param albumId identifier of [Album]
     * @param authors name(s) of people who made this song
     *
     * @return number of albums affected by this operation
     */
    @Query("UPDATE albums SET artists = :authors WHERE id = :albumId")
    fun updateAuthors( albumId: String, authors: String ): Int

    /**
     * @param albumId identifier of [Album]
     * @param title new name of this album
     *
     * @return number of albums affected by this operation
     */
    @Query("UPDATE albums SET title = :title WHERE id = :albumId")
    fun updateTitle( albumId: String, title: String ): Int

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
    fun sortBookmarkedBySongsCount( limit: Int = Int.MAX_VALUE ): Flow<List<Album>>

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
    fun sortBookmarkedByDuration( limit: Int = Int.MAX_VALUE ): Flow<List<Album>>

    /**
     * Fetch all bookmarked albums and sort
     * them according to [sortBy] and [sortOrder].
     *
     * [sortBy] sorts all based on each album's property
     * such as [AlbumSortBy.Title], [AlbumSortBy.Year], etc.
     * While [sortOrder] arranges order of sorted songs
     * to follow alphabetical order A to Z, or numerical order 0 to 9, etc.
     *
     * @param sortBy which album's property is used to sort
     * @param sortOrder what order should results be in
     * @param limit stop query once number of results reaches this number
     *
     * @return a **SORTED** list of [Album]'s that are continuously
     * updated to reflect changes within the database - wrapped by [Flow]
     *
     * @see AlbumSortBy
     * @see SortOrder
     */
    fun sortBookmarked(
        sortBy: AlbumSortBy,
        sortOrder: SortOrder,
        limit: Int = Int.MAX_VALUE
    ): Flow<List<Album>> = when( sortBy ) {
        AlbumSortBy.Title       -> sortBookmarkedByTitle()
        AlbumSortBy.Year        -> sortBookmarkedByYear()
        AlbumSortBy.DateAdded   -> allBookmarked()       // Already sorted by ROWID
        AlbumSortBy.Artist      -> sortBookmarkedByArtist()
        AlbumSortBy.Songs       -> sortBookmarkedBySongsCount()
        AlbumSortBy.Duration    -> sortBookmarkedByDuration()
        AlbumSortBy.RANDOM      -> allBookmarkedRandomized()
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
    fun sortInLibraryBySongsCount( limit: Int = Int.MAX_VALUE ): Flow<List<Album>>

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
    fun sortInLibraryByDuration( limit: Int = Int.MAX_VALUE ): Flow<List<Album>>

    /**
     * Fetch all albums that have their songs mapped to
     * at least 1 playlist in library and sort
     * them according to [sortBy] and [sortOrder].
     *
     * [sortBy] sorts all based on each album's property
     * such as [AlbumSortBy.Title], [AlbumSortBy.Year], etc.
     * While [sortOrder] arranges order of sorted songs
     * to follow alphabetical order A to Z, or numerical order 0 to 9, etc.
     *
     * @param sortBy which album's property is used to sort
     * @param sortOrder what order should results be in
     * @param limit stop query once number of results reaches this number
     *
     * @return a **SORTED** list of [Album]'s that are continuously
     * updated to reflect changes within the database - wrapped by [Flow]
     *
     * @see AlbumSortBy
     * @see SortOrder
     */
    fun sortInLibrary(
        sortBy: AlbumSortBy,
        sortOrder: SortOrder,
        limit: Int = Int.MAX_VALUE
    ): Flow<List<Album>> = when( sortBy ) {
        AlbumSortBy.Title       -> sortInLibraryByTitle()
        AlbumSortBy.Year        -> sortInLibraryByYear()
        AlbumSortBy.DateAdded   -> allInLibrary()        // Already sorted by ROWID
        AlbumSortBy.Artist      -> sortInLibraryByArtist()
        AlbumSortBy.Songs       -> sortInLibraryBySongsCount()
        AlbumSortBy.Duration    -> sortInLibraryByDuration()
        AlbumSortBy.RANDOM      -> allInLibraryRandomized()
    }.map( sortOrder::applyTo ).take( 4 )
    //</editor-fold>
}