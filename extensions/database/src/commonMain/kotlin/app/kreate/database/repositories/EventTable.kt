package app.kreate.database.repositories

import app.kreate.database.ext.EventWithSong
import app.kreate.database.models.Album
import app.kreate.database.models.Artist
import app.kreate.database.models.Event
import app.kreate.database.models.PlaylistPreview
import app.kreate.database.models.Song
import app.kreate.database.table.DatabaseTable
import kotlinx.coroutines.flow.Flow


interface EventTable: DatabaseTable<Event> {

    fun countAll(): Flow<Long>

    fun allWithSong( limit: Int = Int.MAX_VALUE ): Flow<List<EventWithSong>>

    /**
     * Return a list of songs that were listened to by user.
     *
     * Songs must be listened at least once within [from] and [to]
     * be included in the results.
     *
     * Results are sorted from most listened to least listened to.
     *
     * By default, only [from] is required, [to] is set to current time.
     * Meaning fetch all from [from] to present.
     *
     * @param from beginning of period to query in epoch millis format
     * @param to the end of period to query in epoch millis format
     * @param limit trim result to have maximum size of this value
     *
     * @return [Song]s that were listened to at least once in period in descending order
     */
    fun findSongsMostPlayedBetween(
        from: Long,
        to: Long = System.currentTimeMillis(),
        limit: Int = Int.MAX_VALUE
    ): Flow<List<Song>>

    /**
     * Return a list of artists that have their songs listened to by user.
     *
     * Songs must be listened at least once within [from] and [to]
     * be included in the results.
     *
     * Results are sorted by total [Song.totalPlayTimeMs] in descending order.
     *
     * By default, only [from] is required, [to] is set to current time.
     * Meaning fetch all from [from] to present.
     *
     * @param from beginning of period to query in epoch millis format
     * @param to the end of period to query in epoch millis format
     * @param limit trim result to have maximum size of this value
     *
     * @return [Artist]s that have their songs listened to at least once in period in descending order
     */
    fun findArtistsMostPlayedBetween(
        from: Long,
        to: Long = System.currentTimeMillis(),
        limit: Int = Int.MAX_VALUE
    ): Flow<List<Artist>>

    /**
     * Return a list of albums that have their songs listened to by user.
     *
     * Songs must be listened at least once within [from] and [to]
     * be included in the results.
     *
     * Results are sorted by total [Song.totalPlayTimeMs] in descending order.
     *
     * By default, only [from] is required, [to] is set to current time.
     * Meaning fetch all from [from] to present.
     *
     * @param from beginning of period to query in epoch millis format
     * @param to the end of period to query in epoch millis format
     * @param limit trim result to have maximum size of this value
     *
     * @return [Album]s that have their songs listened to at least once in period in descending order
     */
    fun findAlbumsMostPlayedBetween(
        from: Long,
        to: Long = System.currentTimeMillis(),
        limit: Int = Int.MAX_VALUE
    ): Flow<List<Album>>

    /**
     * Return a list of playlists that have their songs were listened to by user.
     *
     * Songs must be listened at least once within [from] and [to]
     * be included in the results.
     *
     * Results are converted into [PlaylistPreview] and
     * sorted by total [Song.totalPlayTimeMs] in descending order.
     *
     * By default, only [from] is required, [to] is set to current time.
     * Meaning fetch all from [from] to present.
     *
     * @param from beginning of period to query in epoch millis format
     * @param to the end of period to query in epoch millis format
     * @param limit trim result to have maximum size of this value
     *
     * @return [PlaylistPreview] that their songs were listened to at least once in period in descending order
     */
    fun findPlaylistMostPlayedBetweenAsPreview(
        from: Long,
        to: Long = System.currentTimeMillis(),
        limit: Int = Int.MAX_VALUE
    ): Flow<List<PlaylistPreview>>

    fun deleteAll(): Int
}