package me.knighthat.database

import android.database.SQLException
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Update
import androidx.room.Upsert
import app.kreate.database.models.Album
import app.kreate.database.models.Song
import it.fast4x.rimusic.enums.AlbumSortBy
import it.fast4x.rimusic.enums.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

@Dao
@RewriteQueriesToDropUnusedColumns
interface AlbumTable {

    /**
     * @return all records from this table
     */
    @Query("SELECT DISTINCT * FROM Album LIMIT :limit")
    fun all( limit: Int = Int.MAX_VALUE ): Flow<List<Album>>

    /**
     * @return all albums from this table that are bookmarked by user
     */
    @Query("""
        SELECT DISTINCT *
        FROM Album
        WHERE bookmarkedAt IS NOT NULL
        ORDER BY ROWID
        LIMIT :limit
    """)
    fun allBookmarked( limit: Int = Int.MAX_VALUE ): Flow<List<Album>>

    /**
     * @return albums that have their songs mapped to at least 1 playlist
     */
    @Query("""
        SELECT DISTINCT A.*
        FROM Album A
        JOIN SongAlbumMap sam ON sam.albumId = A.id
        JOIN SongPlaylistMap spm ON spm.songId = sam.songId 
        ORDER BY A.ROWID
        LIMIT :limit
    """)
    fun allInLibrary( limit: Int = Int.MAX_VALUE ): Flow<List<Album>>

    /**
     * @return all songs of bookmarked albums
     */
    @Query("""
        SELECT DISTINCT S.*
        FROM SongAlbumMap sam
        JOIN Album A ON A.id = sam.albumId
        JOIN Song S ON S.id = sam.songId
        WHERE A.bookmarkedAt IS NOT NULL
        ORDER BY S.ROWID
        LIMIT :limit
    """)
    fun allSongsInBookmarked( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @param albumId of album to look for
     * @return [Album] that has [Album.id] matches [albumId]
     */
    @Query("SELECT DISTINCT * FROM Album WHERE id = :albumId")
    fun findById( albumId: String ): Flow<Album?>

    /**
     * @return [Album] that has song with id [songId]
     */
    @Query("""
        SELECT Album.*
        FROM SongAlbumMap 
        JOIN Album ON id = albumId
        WHERE songId = :songId
    """)
    fun findBySongId( songId: String ): Flow<Album?>

    /**
     * Attempt to write [Album] into database.
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
     * @param album intended to insert in to database
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertIgnore( album: Album )

    /**
     * Attempt to replace a record's data with provided [album].
     *
     * ### Standalone use
     *
     * When error occurs and [android.database.SQLException] is thrown,
     * data inside database will be replaced by provided [album].
     *
     * ### Transaction use
     *
     * When error occurs and [android.database.SQLException] is thrown,
     * data inside database will be replaced by provided [album]
     * and transaction continues.
     *
     * @param album intended to update
     */
    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateReplace( album: Album )

    /**
     * Attempt to replace each record's data with the one provided in records.
     *
     * ### Standalone use
     *
     * When an element fails to insert, it overrides existing
     * data with provided one.
     *
     * ### Transaction use
     *
     * When an element fails to insert, it overrides existing
     * data with provided one and transaction continues.
     *
     * @param albums list of [Album] to update
     */
    @Update(onConflict = OnConflictStrategy.REPLACE)
    fun updateReplace( albums: List<Album> )

    /**
     * Attempt to write [album] into database.
     *
     * If [album] exist (determined by its primary key),
     * existing record's columns will be replaced
     * by provided [album]' data.
     *
     * @param album data intended to insert in to database
     */
    @Upsert
    fun upsert( album: Album )

    /**
     * Attempt to write the list of [Album] to database.
     *
     * If record exist (determined by its primary key),
     * existing record's columns will be replaced
     * by provided data.
     *
     * @param albums list of [Album] to insert to database
     */
    @Upsert
    fun upsert( albums: List<Album> )

    /**
     * @return whether [Album] with id [albumId] is bookmarked,
     * if album doesn't exist, return default value - `false`
     */
    @Query("""
        SELECT 
            CASE
                WHEN bookmarkedAt IS NOT NULL THEN 1   
                ELSE 0
            END
        FROM Album
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
        UPDATE Album
        SET bookmarkedAt = 
            CASE 
                WHEN bookmarkedAt IS NULL THEN strftime('%s', 'now') * 1000
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
    @Query("UPDATE Album SET thumbnailUrl = :thumbnailUrl WHERE id = :albumId")
    fun updateCover( albumId: String, thumbnailUrl: String ): Int

    /**
     * @param albumId identifier of [Album]
     * @param authors name(s) of people who made this song
     *
     * @return number of albums affected by this operation
     */
    @Query("UPDATE Album SET authorsText = :authors WHERE id = :albumId")
    fun updateAuthors( albumId: String, authors: String ): Int

    /**
     * @param albumId identifier of [Album]
     * @param title new name of this album
     *
     * @return number of albums affected by this operation
     */
    @Query("UPDATE Album SET title = :title WHERE id = :albumId")
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
        FROM Album A
        JOIN SongAlbumMap sam ON sam.albumId = A.id 
        WHERE A.bookmarkedAt IS NOT NULL
        GROUP BY A.id
        ORDER BY COUNT(sam.songId)
        LIMIT :limit
    """)
    fun sortBookmarkedBySongsCount( limit: Int = Int.MAX_VALUE ): Flow<List<Album>>

    @Query("""
        SELECT DISTINCT A.*
        FROM Album A
        JOIN SongAlbumMap sam ON sam.albumId = A.id
        JOIN Song S ON S.id = sam.songId
        WHERE A.bookmarkedAt IS NOT NULL
        GROUP BY A.id
        ORDER BY SUM(
            CASE 
                WHEN S.durationText LIKE '%:%:%' THEN (
                    (SUBSTR(S.durationText, 1, INSTR(S.durationText, ':') - 1) * 3600) + 
                    (SUBSTR(S.durationText, INSTR(S.durationText, ':') + 1, INSTR(SUBSTR(S.durationText, INSTR(S.durationText, ':') + 1), ':') - 1) * 60) + 
                    SUBSTR(S.durationText, INSTR(S.durationText, ':') + INSTR(SUBSTR(S.durationText, INSTR(S.durationText, ':') + 1), ':') + 1)
                )
                ELSE (
                    (SUBSTR(S.durationText, 1, INSTR(S.durationText, ':') - 1) * 60) + 
                    (SUBSTR(S.durationText, INSTR(S.durationText, ':') + 1))
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
        FROM SongPlaylistMap spm
        JOIN SongAlbumMap sam ON sam.songId = spm.songId
        JOIN Album A ON A.id = sam.albumId
        GROUP BY A.id
        ORDER BY COUNT(sam.songId)
        LIMIT :limit
    """)
    fun sortInLibraryBySongsCount( limit: Int = Int.MAX_VALUE ): Flow<List<Album>>

    @Query("""
        SELECT DISTINCT A.*
        FROM SongPlaylistMap spm
        JOIN SongAlbumMap sam ON sam.songId = spm.songId
        JOIN Album A ON A.id = sam.albumId
        JOIN Song S ON S.id = sam.songId
        GROUP BY A.id
        ORDER BY SUM(
            CASE 
                WHEN S.durationText LIKE '%:%:%' THEN (
                    (SUBSTR(S.durationText, 1, INSTR(S.durationText, ':') - 1) * 3600) + 
                    (SUBSTR(S.durationText, INSTR(S.durationText, ':') + 1, INSTR(SUBSTR(S.durationText, INSTR(S.durationText, ':') + 1), ':') - 1) * 60) + 
                    SUBSTR(S.durationText, INSTR(S.durationText, ':') + INSTR(SUBSTR(S.durationText, INSTR(S.durationText, ':') + 1), ':') + 1)
                )
                ELSE (
                    (SUBSTR(S.durationText, 1, INSTR(S.durationText, ':') - 1) * 60) + 
                    (SUBSTR(S.durationText, INSTR(S.durationText, ':') + 1))
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
    }.map( sortOrder::applyTo ).take( 4 )
    //</editor-fold>
}