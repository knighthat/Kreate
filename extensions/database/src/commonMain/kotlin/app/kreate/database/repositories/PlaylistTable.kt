package app.kreate.database.repositories

import androidx.room.Query
import app.kreate.constant.PlaylistSortBy
import app.kreate.constant.SortOrder
import app.kreate.database.models.Artist
import app.kreate.database.models.Playlist
import app.kreate.database.models.PlaylistPreview
import app.kreate.database.models.Song
import app.kreate.database.table.DatabaseTable
import kotlinx.coroutines.flow.Flow


interface PlaylistTable: DatabaseTable<Playlist> {

    /**
     * @return list of songs that were mapped to at least 1 playlist
     */
    fun allSongs( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @return list of songs that were mapped to at least 1 **pinned** playlist
     */
    fun allPinnedSongs( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @return list of songs that belong YouTube private playlist
     */
    fun allYTPlaylistSongs( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @return list of songs that were mapped to at least 1 **monthly** playlist
     */
    fun allMonthlySongs( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @return all playlists from this table with number of songs they carry
     */
    fun allAsPreview( limit: Int = Int.MAX_VALUE ): Flow<List<PlaylistPreview>>

    /**
     * @return all playlists from this table with number of songs they carry in randomized order
     */
    fun allAsPreviewRandomized( limit: Int = Int.MAX_VALUE ): Flow<List<PlaylistPreview>>

    /**
     * @param browseId of playlist to look for
     * @return [Playlist] that has [Playlist.browseId] matches [browseId]
     */
    fun findByBrowseId( browseId: String ): Flow<Playlist?>

    /**
     * @return [Playlist] that has [Playlist.name] equals to [playlistName], case-insensitive
     */
    fun findByName( playlistName: String ): Flow<Playlist?>

    /**
     * @return playlist with id [playlistId]
     */
    fun findById( playlistId: Long ): Flow<Playlist?>

    /**
     * Attempt to write [playlist] into database.
     *
     * ### Standalone use
     *
     * When error occurs and [androidx.sqlite.SQLiteException] is thrown,
     * the process is cancel and passes exception to caller.
     *
     * ### Transaction use
     *
     * When error occurs and [androidx.sqlite.SQLiteException] is thrown,
     * **the entire transaction rolls back** and passes exception to caller.
     *
     * > Note: Use this if inserting record is crucial for
     * > the transaction to continue.
     *
     * @param playlist intended to insert in to database
     * @return ROWID of this new record, throws exception when fail
     * @throws androidx.sqlite.SQLiteException when there's a conflict
     */
    fun insert( playlist: Playlist ): Long

    /**
     * @return whether a playlist with name [playlistName] exists in the database
     */
    @Query("""
        SELECT COUNT(*) > 0
        FROM playlists
        WHERE name = :playlistName
    """)
    fun exists( playlistName: String ): Flow<Boolean>

    /**
     * ### If playlist **IS NOT** pinned
     *
     * Set [Playlist.isPinned] to `true`
     *
     * ### If playlist **IS** pinned
     *
     * Set [Playlist.isPinned] to `false`
     *
     * @return number of rows affected
     */
    fun togglePin( playlistId: Long ): Int

    /**
     * Fetch all playlists, sort them according to [sortBy] and [sortOrder],
     * and return [PlaylistPreview] as the result.
     *
     * [sortBy] sorts all based on each playlist's property
     * such as [PlaylistSortBy.TITLE], [PlaylistSortBy.DATE_ADDED], etc.
     * While [sortOrder] arranges order of sorted songs
     * to follow alphabetical order A to Z, or numerical order 0 to 9, etc.
     *
     * @param sortBy which playlist's property is used to sorts
     * @param sortOrder what order should results be in
     * @param limit stop query once number of results reaches this number
     *
     * @return a **SORTED** list of [Artist]'s that are continuously
     * updated to reflect changes within the database - wrapped by [Flow]
     *
     * @see PlaylistSortBy
     * @see SortOrder
     */
    fun sortPreviews(
        sortBy: PlaylistSortBy,
        sortOrder: SortOrder,
        limit: Int = Int.MAX_VALUE
    ): Flow<List<PlaylistPreview>>
}