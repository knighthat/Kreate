package app.kreate.database.repositories

import app.kreate.constant.PlaylistSongSortBy
import app.kreate.constant.SortOrder
import app.kreate.database.models.Playlist
import app.kreate.database.models.Song
import app.kreate.database.models.SongPlaylistMap
import app.kreate.database.table.DatabaseTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map


interface SongPlaylistMapTable: DatabaseTable<SongPlaylistMap> {

    /**
     * Song with [songId] will be removed from all playlists
     *
     * @return number of rows affected by this operation
     */
    fun deleteBySongId( songId: String ): Int

    /**
     * Remove song with [songId] from playlist with id [playlistId]
     *
     * @return number of rows affected by this operation
     */
    fun deleteBySongId( songId: String, playlistId: Long ): Int

    /**
     * Remove all songs belong to playlist with id [playlistId]
     *
     * @param playlistId playlist to have its songs wiped
     *
     * @return number of rows affected by this operation
     */
    fun clear( playlistId: Long ): Int

    /**
     * Delete all mappings where songs aren't exist in `Song` table
     *
     * @return number of rows affected by this operation
     */
    fun clearGhostMaps(): Int

    /**
     * @param playlistId of playlist to look for
     * @return all [Song]s that were mapped to playlist has [Playlist.id] matches [playlistId]
     */
    fun allSongsOf( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @param playlistId of playlist to look for
     * @return all [Song]s that were mapped to playlist has [Playlist.id] matches [playlistId] in randomized order
     */
    fun allSongsOfRandomized( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @param songId of playlist to look for
     * @param playlistId playlist to look into
     *
     * @return [SongPlaylistMap] that has [Song.id] matches [songId] and [Playlist.id] matches [playlistId]
     */
    fun findById( songId: String, playlistId: Long ): Flow<SongPlaylistMap?>

    /**
     * @return position of [songId] in [playlistId], `-1` otherwise
     */
    fun findPositionOf( songId: String, playlistId: Long ): Int

    /**
     * @return whether [songId] is mapped to any playlist
     */
    fun isMapped( songId: String ): Flow<Boolean>

    /**
     * @return whether [songId] is mapped to [playlistId]
     */
    fun isMapped( songId: String, playlistId: Long ): Flow<Boolean>

    /**
     * @return list of [Playlist.id] that [songId] is mapped to
     */
    fun mappedTo( songId: String ): Flow<List<Long>>

    /**
     * Randomly assign new [SongPlaylistMap.position] to
     * each song mapped to playlist with id [playlistId].
     *
     * @return number of rows affected by this operation
     */
    fun shufflePositions( playlistId: Long ): Int

    /**
     * Move song from [from] to [to].
     *
     * Other songs' positions are updated accordingly
     *
     * @return number of rows affected by this operation
     */
    fun move( playlistId: Long, from: Int, to: Int ): Int

    /**
     * Insert provided song into indicated playlist
     * at the next available position.
     *
     * If record exists in the database (determined by [songId] and [playlistId])
     * then this operation is skipped.
     *
     * @param songId     song to add
     * @param playlistId playlist to add song into
     */
    fun map( songId: String, playlistId: Long )

    /**
     * Compile a list of songs that are most listened to in **descending** order.
     *
     * @param playlistId playlist to query
     * @param limit number of items to include
     */
    fun findMostPlayedSongsOf(playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * Compile a list of thumbnail url from songs that are
     * most listened to in **descending** order.
     *
     * @param playlistId playlist to query
     * @param limit number of items to include
     */
    fun findThumbnailsOfMostPlayedSongIn(
        playlistId: Long,
        limit: Int = Int.MAX_VALUE
    ): Flow<List<String>> =
        findMostPlayedSongsOf( playlistId )
            .map { list ->
                list.asSequence()
                    .filter { it.thumbnailUrl != null }
                    .distinctBy( Song::thumbnailUrl )
                    .take( 4 )
                    .toList()
                    .mapNotNull( Song::cleanThumbnailUrl )
            }
            .flowOn( Dispatchers.Default )

    /**
     * Fetch all songs that were mapped to [playlistId] and sort
     * them according to [sortBy] and [sortOrder].
     *
     * [sortBy] sorts all based on each song's property
     * such as [PlaylistSongSortBy.ARTIST], [PlaylistSongSortBy.DURATION], etc.
     * While [sortOrder] arranges order of sorted songs
     * to follow alphabetical order A to Z, or numerical order 0 to 9, etc.
     *
     * @param sortBy which song's property is used to sort
     * @param sortOrder what order should results be in
     * @param limit stop query once number of results reaches this number
     *
     * @return a **SORTED** list of [Song]'s that are continuously
     * updated to reflect changes within the database - wrapped by [Flow]
     *
     * @see PlaylistSongSortBy
     * @see SortOrder
     */
    fun sortSongs(
        playlistId: Long,
        sortBy: PlaylistSongSortBy,
        sortOrder: SortOrder,
        limit: Int = Int.MAX_VALUE
    ): Flow<List<Song>>
}