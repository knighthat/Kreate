package app.kreate.database.repositories

import app.kreate.constant.SongSortBy
import app.kreate.constant.SortOrder
import app.kreate.database.ext.FormatWithSong
import app.kreate.database.models.Format
import app.kreate.database.models.Song
import app.kreate.database.table.DatabaseTable
import kotlinx.coroutines.flow.Flow


interface FormatTable: DatabaseTable<Format> {

    /**
     * @return formats & songs of this table
     */
    fun allWithSongs(
        limit: Int = Int.MAX_VALUE,
        excludeHidden: Boolean = false
    ): Flow<List<FormatWithSong>>

    /**
     * @return formats & songs of this table in randomized order
     */
    fun allWithSongsRandomized(
        limit: Int = Int.MAX_VALUE,
        excludeHidden: Boolean = false
    ): Flow<List<FormatWithSong>>

    /**
     * [Format] with [Format.songId] inside [songIds] will be removed.
     *
     * @return number of rows affected by this operation
     */
    fun deleteBySongId( songIds: List<String> ): Int

    fun deleteBySongId( vararg songIds: String ): Int

    /**
     * @param songId of song to look for
     * @return [Format] that has [Format.songId] matches [songId]
     */
    fun findBySongId( songId: String ): Flow<Format?>

    /**
     * @return stored [Format.contentLength] of song with id [songId], `0` otherwise
     */
    fun findContentLengthOf( songId: String ): Flow<Long>

    /**
     * Set [Format.contentLength] of song with id [songId] to [contentLength]
     *
     * @return number of rows affected by this operation
     */
    fun updateContentLengthOf( songId: String, contentLength: Long = 0L ): Int

    /**
     * Fetch all formats & songs from the database and sort them
     * according to [sortBy] and [sortOrder] based on Song's properties.
     * It also excludes songs if condition of [excludeHidden] is met.
     *
     * [sortBy] sorts all based on each song's property
     * such as [SongSortBy.TITLE], [SongSortBy.TOTAL_PLAY_TIME], etc.
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
    ): Flow<List<FormatWithSong>>
}