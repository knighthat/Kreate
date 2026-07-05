package app.kreate.database.repositories

import app.kreate.constant.ArtistSortBy
import app.kreate.constant.SortOrder
import app.kreate.database.models.Artist
import app.kreate.database.models.Song
import app.kreate.database.table.DatabaseTable
import kotlinx.coroutines.flow.Flow


interface ArtistTable: DatabaseTable<Artist> {

    /**
     * @return all artists from this table that are followed by user
     */
    fun allFollowing( limit: Int = Int.MAX_VALUE ): Flow<List<Artist>>

    /**
     * @return all artists from this table that are followed by user in randomized order
     */
    fun allFollowingRandomized( limit: Int = Int.MAX_VALUE ): Flow<List<Artist>>

    /**
     * @return artists that have their songs mapped to at least 1 playlist
     */
    fun allInLibrary( limit: Int = Int.MAX_VALUE ): Flow<List<Artist>>

    /**
     * @return artists that have their songs mapped to at least 1 playlist in randomized order
     */
    fun allInLibraryRandomized( limit: Int = Int.MAX_VALUE ): Flow<List<Artist>>

    /**
     * @return all songs of following artists
     */
    fun allSongsInFollowing( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @param artistId of artist to look for
     * @return [Artist] that has [Artist.id] matches [artistId]
     */
    fun findById( artistId: String ): Flow<Artist?>

    fun findBySongId( songId: String ): Flow<List<Artist>>

    /**
     * @return whether [Artist] with id [artistId] is followed by user,
     * if artist doesn't exist, return default value - `false`
     */
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
    fun toggleFollow( artistId: String ): Int

    /**
     * Fetch all following artists and sort
     * them according to [sortBy] and [sortOrder].
     *
     * [sortBy] sorts all based on each artist's property
     * such as [ArtistSortBy.TITLE], [ArtistSortBy.DATE_ADDED], etc.
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
    ): Flow<List<Artist>>

    /**
     * Fetch all artists that have their songs mapped to
     * at least 1 playlist in library and sort
     * them according to [sortBy] and [sortOrder].
     *
     * [sortBy] sorts all based on each artist's property
     * such as [ArtistSortBy.TITLE], [ArtistSortBy.DATE_ADDED], etc.
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
    ): Flow<List<Artist>>
}