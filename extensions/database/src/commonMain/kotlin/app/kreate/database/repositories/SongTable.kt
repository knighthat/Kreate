package app.kreate.database.repositories

import app.kreate.constant.SongSortBy
import app.kreate.constant.SortOrder
import app.kreate.database.models.Song
import app.kreate.database.table.DatabaseTable
import app.kreate.util.MODIFIED_PREFIX
import kotlinx.coroutines.flow.Flow


interface SongTable: DatabaseTable<Song> {

    /**
     * @return all records from this table
     */
    fun all(
        limit: Int = Int.MAX_VALUE,
        excludeHidden: Boolean = false
    ): Flow<List<Song>>

    /**
     * @return all records from this table in randomized order
     */
    fun allRandomized(
        limit: Int = Int.MAX_VALUE,
        excludeHidden: Boolean = false
    ): Flow<List<Song>>

    /**
     * @return all records with [Song.isLocal] being `true`
     */
    fun allOnDevice( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @return all songs that were liked by user
     */
    fun allFavorites( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @return all songs that were liked by user in randomized order
     */
    fun allFavoritesRandomized( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    fun allDisliked( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * Delete all songs with [Song.totalPlayTimeMs] equal to `0`
     *
     * @return number of rows affected by this operation
     */
    fun clearHiddenSongs(): Int

    /**
     * @param songId of album to look for
     *
     * @return [Song] that has [Song.id] matches [songId]
     */
    fun findById( songId: String ): Flow<Song?>

    fun findByIds( songIds: Collection<String> ): List<Song>

    /**
     * [searchTerm] appears in [Song.title] or [Song.artistsText].
     * Additionally, it's **case-insensitive**
     *
     * I.E.: `name` matches `1name_to` and `1_NaMe_to`
     *
     * @param searchTerm what to look for
     * @return all [Song]s that have [Song.title] or [Song.artistsText] contain [searchTerm]
     */
    fun findAllTitleArtistContains( searchTerm: String ): Flow<List<Song>>

    /**
     * Require [artistName] to match [Song.artistsText], except for case-sensitive.
     *
     * I.E.: `Michael` matches both `michael` and `MICHAEL`
     *
     * Additionally, [MODIFIED_PREFIX] is removed before comparison.
     *
     * @param artistName [Song.artistsText] to look for
     * @return all [Song]s that have [Song.artistsText] match [artistName]
     */
    fun findAllByArtist( artistName: String ): Flow<List<Song>>

    /**
     * Returns all [Song.id] and its [Song.likedAt] if set
     */
    fun observeLikeState(): Flow<Map<String, Long>>

    /**
     * @return whether any record in [Song] table has id [songId]
     */
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
    fun isLiked( songId: String ): Flow<Boolean>

    fun isLocal( songId: String ): Flow<Boolean>

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
    fun likeState( songId: String, likeState: Boolean? ): Int

    /**
     * @param songId identifier of [Song]
     * @param title new name of this song
     *
     * @return number of albums affected by this operation
     */
    fun updateTitle( songId: String, title: String ): Int

    /**
     * @param songId identifier of [Song]
     * @param artistsText artists to display
     *
     * @return number of albums affected by this operation
     */
    fun updateArtists( songId: String, artistsText: String ): Int

    /**
     * @param songId identifier of [Song]
     * @param thumbnailUrl new url to get image
     *
     * @return number of albums affected by this operation
     */
    fun updateThumbnail( songId: String, thumbnailUrl: String? ): Int

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
    fun updateTotalPlayTime( songId: String, value: Long, isIncrement: Boolean = false ): Int

    /**
     * Fetch all songs from the database and sort them
     * according to [sortBy] and [sortOrder]. It also
     * excludes songs if condition of [excludeHidden] is met.
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
    fun sortAll(
        sortBy: SongSortBy,
        sortOrder: SortOrder,
        limit: Int = Int.MAX_VALUE,
        excludeHidden: Boolean = false
    ): Flow<List<Song>>

    /**
     * Fetch all favorite songs and sort them according to [sortBy] and [sortOrder].
     *
     * [sortBy] sorts all based on each song's property
     * such as [SongSortBy.TITLE], [SongSortBy.TOTAL_PLAY_TIME], etc.
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
    ): Flow<List<Song>>
}