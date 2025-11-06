package me.knighthat.database

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.kreate.database.models.Playlist
import app.kreate.database.models.Song
import app.kreate.database.models.SongPlaylistMap
import app.kreate.database.table.DatabaseTable
import app.kreate.util.MODIFIED_PREFIX
import it.fast4x.rimusic.enums.PlaylistSongSortBy
import it.fast4x.rimusic.enums.SortOrder
import it.fast4x.rimusic.utils.durationTextToMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

@Dao
@RewriteQueriesToDropUnusedColumns
interface SongPlaylistMapTable: DatabaseTable<SongPlaylistMap> {

    /**
     * Song with [songId] will be removed from all playlists
     *
     * @return number of rows affected by this operation
     */
    @Query("DELETE FROM SongPlaylistMap WHERE songId = :songId")
    fun deleteBySongId( songId: String ): Int

    /**
     * Remove song with [songId] from playlist with id [playlistId]
     *
     * @return number of rows affected by this operation
     */
    @Query("DELETE FROM SongPlaylistMap WHERE songId = :songId AND playlistId = :playlistId")
    fun deleteBySongId( songId: String, playlistId: Long ): Int

    /**
     * Remove all songs belong to playlist with id [playlistId]
     *
     * @param playlistId playlist to have its songs wiped
     *
     * @return number of rows affected by this operation
     */
    @Query("DELETE FROM SongPlaylistMap WHERE playlistId = :playlistId")
    fun clear( playlistId: Long ): Int

    /**
     * Delete all mappings where songs aren't exist in `Song` table
     *
     * @return number of rows affected by this operation
     */
    @Query("""
        DELETE FROM SongPlaylistMap 
        WHERE songId NOT IN (
            SELECT DISTINCT id
            FROM Song
        )
    """)
    fun clearGhostMaps(): Int

    /**
     * @param playlistId of playlist to look for
     * @return all [Song]s that were mapped to playlist has [Playlist.id] matches [playlistId]
     */
    @Query("""
        SELECT DISTINCT S.*
        FROM SongPlaylistMap SPM
        JOIN Song S ON S.id = SPM.songId
        WHERE SPM.playlistId = :playlistId
        ORDER BY SPM.ROWID
        LIMIT :limit
    """)
    fun allSongsOf( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @param playlistId of playlist to look for
     * @return all [Song]s that were mapped to playlist has [Playlist.id] matches [playlistId] in randomized order
     */
    @Query("""
        SELECT DISTINCT S.*
        FROM SongPlaylistMap SPM
        JOIN Song S ON S.id = SPM.songId
        WHERE SPM.playlistId = :playlistId
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    fun allSongsOfRandomized( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @param songId of playlist to look for
     * @param playlistId playlist to look into
     *
     * @return [SongPlaylistMap] that has [Song.id] matches [songId] and [Playlist.id] matches [playlistId]
     */
    @Query("SELECT * FROM SongPlaylistMap WHERE playlistId = :playlistId AND songId = :songId")
    fun findById( songId: String, playlistId: Long ): Flow<SongPlaylistMap?>

    /**
     * @return position of [songId] in [playlistId], `-1` otherwise
     */
    @Query("""
        SELECT COALESCE(
            (
                SELECT 'position'
                FROM SongPlaylistMap 
                WHERE songId = :songId 
                AND playlistId = :playlistId
            ),
            -1
        ) 
        """)
    fun findPositionOf( songId: String, playlistId: Long ): Int

    /**
     * @return whether [songId] is mapped to any playlist
     */
    @Query("""
        SELECT COUNT(s.id) > 0
        FROM Song s
        JOIN SongPlaylistMap spm ON spm.songId = s.id 
        WHERE s.id = :songId
    """)
    fun isMapped( songId: String ): Flow<Boolean>

    /**
     * @return list of [Playlist.id] that [songId] is mapped to
     */
    @Query("""
        SELECT playlistId
        FROM SongPlaylistMap
        WHERE songId = :songId
    """)
    fun mappedTo( songId: String ): Flow<List<Long>>

    /**
     * Randomly assign new [SongPlaylistMap.position] to
     * each song mapped to playlist with id [playlistId].
     *
     * @return number of rows affected by this operation
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Query("""
        UPDATE SongPlaylistMap 
        SET position = shuffled.new_position
        FROM (
            SELECT spm1.songId, ROW_NUMBER() OVER (ORDER BY RANDOM()) - 1 AS new_position
            FROM SongPlaylistMap spm1
            WHERE spm1.playlistId = :playlistId
        ) AS shuffled
        WHERE playlistId = :playlistId
        AND shuffled.songId = SongPlaylistMap.songId 
    """)
    // You'll get complain from IDE that "shuffled" and "FROM"
    // aren't exist, that's OK - IGNORE it.
    fun shufflePositions( playlistId: Long ): Int

    /**
     * Move song from [from] to [to].
     *
     * Other songs' positions are updated accordingly
     *
     * @return number of rows affected by this operation
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Query("""
        UPDATE SongPlaylistMap
        SET position = updated.new_position
        FROM (
            SELECT 
                spm.songId, 
                spm.position,
                CASE 
                    WHEN spm.position = :from THEN :to 
                    WHEN spm.position > :from AND spm.position <= :to THEN position - 1 
                    WHEN spm.position < :from AND spm.position >= :to THEN position + 1 
                    ELSE spm.position
                END AS new_position
            FROM SongPlaylistMap spm
            WHERE spm.playlistId = :playlistId
        ) AS updated
        WHERE playlistId = :playlistId
        AND updated.songId = SongPlaylistMap.songId
    """)
    // You'll get complain from IDE that "updated" and "FROM"
    // aren't exist, that's OK - IGNORE it.
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
    @Query("""
        INSERT OR IGNORE INTO SongPlaylistMap ( songId, playlistId, position )
        VALUES( 
            :songId,
            :playlistId,
            COALESCE(
                (
                    SELECT MAX(position) + 1 
                    FROM SongPlaylistMap 
                    WHERE playlistId = :playlistId
                ), 
                0
            )
        )
    """)
    fun map( songId: String, playlistId: Long )

    /**
     * Compile a list of songs that are most listened to in **descending** order.
     *
     * @param playlistId playlist to query
     * @param limit number of items to include
     */
    @Query("""
        SELECT DISTINCT s.*
        FROM SongPlaylistMap spm
        LEFT JOIN Song s ON s.id = spm.songId
        WHERE spm.playlistId = :playlistId
        ORDER BY s.totalPlayTimeMs DESC
        LIMIT :limit
    """)
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
                val results = mutableListOf<String>()

                for( song in list ) {
                    if( results.size == limit )
                        break
                    else if( song.thumbnailUrl != null && song.thumbnailUrl !in results )
                        results.add( song.thumbnailUrl )
                }

                results
            }
            .flowOn( Dispatchers.Default )

    //<editor-fold defaultstate="collapsed" desc="Sort songs of playlist">
    @Query("""
        SELECT DISTINCT S.*
        FROM SongPlaylistMap spm
        LEFT JOIN Song S ON S.id = spm.songId
        LEFT JOIN SongAlbumMap sam ON sam.songId = S.id
        LEFT JOIN Album A ON A.id = sam.albumId
        WHERE spm.playlistId = :playlistId
        ORDER BY 
            A.title IS NULL, 
            CASE
                WHEN A.title LIKE "$MODIFIED_PREFIX%" THEN SUBSTR(A.title, LENGTH('$MODIFIED_PREFIX') + 1)
                ELSE A.title
            END
        LIMIT :limit
    """)
    fun sortSongsByAlbum( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    @Query("""
        SELECT DISTINCT S.*
        FROM SongPlaylistMap spm
        LEFT JOIN Song S ON S.id = spm.songId
        LEFT JOIN SongAlbumMap sam ON sam.songId = S.id
        LEFT JOIN Album A ON A.id = sam.albumId
        WHERE spm.playlistId = :playlistId
        ORDER BY A.year IS NULL, A.year
        LIMIT :limit
    """)
    fun sortSongsByAlbumYear( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    fun sortSongsByArtist( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>> =
        allSongsOf( playlistId, limit ).map { list ->
            list.sortedBy( Song::cleanArtistsText )
        }

    @Query("""
        SELECT DISTINCT S.*
        FROM SongPlaylistMap spm
        LEFT JOIN Song S ON S.id = spm.songId
        LEFT JOIN SongAlbumMap sam ON sam.songId = S.id
        LEFT JOIN Album A ON A.id = sam.albumId
        WHERE spm.playlistId = :playlistId
        ORDER BY 
            A.title IS NULL, 
            S.artistsText IS NULL,
            CASE
                WHEN A.title LIKE "$MODIFIED_PREFIX%" THEN SUBSTR(A.title, LENGTH('$MODIFIED_PREFIX') + 1)
                ELSE A.title
            END,
            CASE
                WHEN S.artistsText LIKE "$MODIFIED_PREFIX%" THEN SUBSTR(S.artistsText, LENGTH('$MODIFIED_PREFIX') + 1)
                ELSE S.artistsText
            END
        LIMIT :limit
    """)
    fun sortSongsByAlbumAndArtist( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    @Query("""
        SELECT S.*
        FROM SongPlaylistMap spm
        LEFT JOIN Song S ON S.id = spm.songId
        LEFT JOIN Event E ON E.songId = S.id
        WHERE spm.playlistId = :playlistId
        ORDER BY E.timestamp
        LIMIT :limit
    """)
    fun sortSongsByDatePlayed( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    fun sortSongsByPlayTime( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>> =
        allSongsOf( playlistId, limit ).map { list ->
            list.sortedBy( Song::totalPlayTimeMs )
        }

    fun sortSongsByRelativePlayTime( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>> =
        allSongsOf( playlistId, limit ).map { list ->
            list.sortedBy( Song::relativePlayTime )
        }

    @Query("""
        SELECT S.*
        FROM SongPlaylistMap spm
        LEFT JOIN Song S ON S.id = spm.songId
        WHERE spm.playlistId = :playlistId
        ORDER BY spm.position
        LIMIT :limit
    """)
    fun sortSongsByPosition( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    fun sortSongsByTitle( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>> =
        allSongsOf( playlistId, limit ).map { list ->
            list.sortedBy( Song::cleanTitle )
        }

    fun sortSongsByDuration( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>> =
        allSongsOf( playlistId, limit ).map { list ->
            list.sortedBy { durationTextToMillis( it.durationText ?: "0" ) }
        }

    fun sortSongsByLikedAt( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>> =
        allSongsOf( playlistId, limit ).map { list ->
            list.sortedBy( Song::likedAt )
        }

    /**
     * Fetch all songs that were mapped to [playlistId] and sort
     * them according to [sortBy] and [sortOrder].
     *
     * [sortBy] sorts all based on each song's property
     * such as [PlaylistSongSortBy.Artist], [PlaylistSongSortBy.Duration], etc.
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
    ): Flow<List<Song>> = when( sortBy ) {
        PlaylistSongSortBy.Album            -> sortSongsByAlbum( playlistId )
        PlaylistSongSortBy.AlbumYear        -> sortSongsByAlbumYear( playlistId )
        PlaylistSongSortBy.Artist           -> sortSongsByArtist( playlistId )
        PlaylistSongSortBy.ArtistAndAlbum   -> sortSongsByAlbumAndArtist( playlistId )
        PlaylistSongSortBy.DatePlayed       -> sortSongsByDatePlayed( playlistId )
        PlaylistSongSortBy.PlayTime         -> sortSongsByPlayTime( playlistId )
        PlaylistSongSortBy.RelativePlayTime -> sortSongsByRelativePlayTime( playlistId )
        PlaylistSongSortBy.Position         -> sortSongsByPosition( playlistId )
        PlaylistSongSortBy.Title            -> sortSongsByTitle( playlistId )
        PlaylistSongSortBy.Duration         -> sortSongsByDuration( playlistId )
        PlaylistSongSortBy.DateLiked        -> sortSongsByLikedAt( playlistId )
        PlaylistSongSortBy.DateAdded        -> allSongsOf( playlistId )     // Already sorted by ROWID
        PlaylistSongSortBy.RANDOM           -> allSongsOfRandomized( playlistId )
    }.map( sortOrder::applyTo ).take( limit )
    //</editor-fold>
}