package me.knighthat.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.kreate.database.models.Artist
import app.kreate.database.models.Song
import app.kreate.database.table.DatabaseTable
import it.fast4x.rimusic.enums.ArtistSortBy
import it.fast4x.rimusic.enums.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

@Dao
@RewriteQueriesToDropUnusedColumns
interface ArtistTable: DatabaseTable<Artist> {

    override val tableName: String
        get() = Artist::class.simpleName!!

    /**
     * @return all artists from this table that are followed by user
     */
    @Query("""
        SELECT DISTINCT * 
        FROM Artist
        WHERE bookmarkedAt IS NOT NULL
        ORDER BY ROWID 
        LIMIT :limit
    """)
    fun allFollowing( limit: Int = Int.MAX_VALUE ): Flow<List<Artist>>

    /**
     * @return all artists from this table that are followed by user in randomized order
     */
    @Query("""
        SELECT DISTINCT * 
        FROM Artist
        WHERE bookmarkedAt IS NOT NULL
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    fun allFollowingRandomized( limit: Int = Int.MAX_VALUE ): Flow<List<Artist>>

    /**
     * @return artists that have their songs mapped to at least 1 playlist
     */
    @Query("""
        SELECT DISTINCT A.*
        FROM Artist A
        JOIN SongArtistMap sam ON sam.artistId = A.id
        JOIN SongPlaylistMap spm ON spm.songId = sam.songId
        ORDER BY A.ROWID
        LIMIT :limit
    """)
    fun allInLibrary( limit: Int = Int.MAX_VALUE ): Flow<List<Artist>>

    /**
     * @return artists that have their songs mapped to at least 1 playlist in randomized order
     */
    @Query("""
        SELECT DISTINCT A.*
        FROM Artist A
        JOIN SongArtistMap sam ON sam.artistId = A.id
        JOIN SongPlaylistMap spm ON spm.songId = sam.songId
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    fun allInLibraryRandomized( limit: Int = Int.MAX_VALUE ): Flow<List<Artist>>

    /**
     * @return all songs of following artists
     */
    @Query("""
        SELECT DISTINCT S.*
        FROM SongArtistMap sam
        JOIN Artist A ON A.id = sam.artistId
        JOIN Song S ON S.id = sam.songId
        WHERE A.bookmarkedAt IS NOT NULL
        ORDER BY S.ROWID
        LIMIT :limit
    """)
    fun allSongsInFollowing( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @param artistId of artist to look for
     * @return [Artist] that has [Artist.id] matches [artistId]
     */
    @Query("SELECT DISTINCT * FROM Artist WHERE id = :artistId")
    fun findById( artistId: String ): Flow<Artist?>

    @Query("""
        SELECT DISTINCT Artist.*
        FROM SongArtistMap
        JOIN Artist ON id = artistId
        WHERE songId = :songId
    """)
    fun findBySongId( songId: String ): Flow<List<Artist>>

    /**
     * @return whether [Artist] with id [artistId] is followed by user,
     * if artist doesn't exist, return default value - `false`
     */
    @Query("""
        SELECT COALESCE(
            (
                SELECT 1 
                FROM Artist 
                WHERE id = :artistId 
                AND bookmarkedAt IS NOT NULL 
            ),
            0
        )
    """)
    fun isFollowing( artistId: String ): Flow<Boolean>

    /**
     * There are 2 possible actions.
     *
     * ### If artist IS followed
     *
     * This will remove [Artist.bookmarkedAt] timestamp (replace with NULL)
     *
     * ## If artist IS NOT followed
     *
     * It will assign [Artist.bookmarkedAt] with current time in millis
     *
     * @param artistId artist identifier to update its [Artist.bookmarkedAt]
     *
     * @return number of artists updated by this operation
     */
    @Query("""
        UPDATE Artist
        SET bookmarkedAt = CASE
            WHEN bookmarkedAt IS NULL THEN strftime('%s', 'now') * 1000
            ELSE NULL
        END
        WHERE id = :artistId
    """)
    fun toggleFollow( artistId: String ): Int

    //<editor-fold defaultstate="collapsed" desc="Sort all">
    fun sortFollowingByName( limit: Int = Int.MAX_VALUE ): Flow<List<Artist>> =
        allFollowing( limit ).map { list ->
            list.sortedBy( Artist::cleanName )
        }

    /**
     * Fetch all following artists and sort
     * them according to [sortBy] and [sortOrder].
     *
     * [sortBy] sorts all based on each artist's property
     * such as [ArtistSortBy.Name], [ArtistSortBy.DateAdded], etc.
     * While [sortOrder] arranges order of sorted songs
     * to follow alphabetical order A to Z, or numerical order 0 to 9, etc.
     *
     * @param sortBy which artist's property is used to sort
     * @param sortOrder what order should results be in
     * @param limit stop query once number of results reaches this number
     *
     * @return a **SORTED** list of [Artist]'s that are continuously
     * updated to reflect changes within the database - wrapped by [Flow]
     *
     * @see ArtistSortBy
     * @see SortOrder
     */
    fun sortFollowing(
        sortBy: ArtistSortBy,
        sortOrder: SortOrder,
        limit: Int = Int.MAX_VALUE
    ): Flow<List<Artist>> = when( sortBy ) {
        ArtistSortBy.Name       -> sortFollowingByName()
        ArtistSortBy.DateAdded  -> allFollowing()
        ArtistSortBy.RANDOM     -> allFollowingRandomized()
    }.map( sortOrder::applyTo ).take( limit )
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Sort artists in library">
    fun sortInLibraryByName( limit: Int = Int.MAX_VALUE ): Flow<List<Artist>> =
        allInLibrary( limit ).map { list ->
            list.sortedBy( Artist::cleanName )
        }

    /**
     * Fetch all artists that have their songs mapped to
     * at least 1 playlist in library and sort
     * them according to [sortBy] and [sortOrder].
     *
     * [sortBy] sorts all based on each artist's property
     * such as [ArtistSortBy.Name], [ArtistSortBy.DateAdded], etc.
     * While [sortOrder] arranges order of sorted songs
     * to follow alphabetical order A to Z, or numerical order 0 to 9, etc.
     *
     * @param sortBy which artist's property is used to sort
     * @param sortOrder what order should results be in
     * @param limit stop query once number of results reaches this number
     *
     * @return a **SORTED** list of [Artist]'s that are continuously
     * updated to reflect changes within the database - wrapped by [Flow]
     *
     * @see ArtistSortBy
     * @see SortOrder
     */
    fun sortInLibrary(
        sortBy: ArtistSortBy,
        sortOrder: SortOrder,
        limit: Int = Int.MAX_VALUE
    ): Flow<List<Artist>> = when( sortBy ) {
        ArtistSortBy.Name       -> sortInLibraryByName()
        ArtistSortBy.DateAdded  -> allInLibrary()     // Already sorted by ROWID
        ArtistSortBy.RANDOM     -> allInLibraryRandomized()
    }.map( sortOrder::applyTo ).take( limit )
    //</editor-fold>
}