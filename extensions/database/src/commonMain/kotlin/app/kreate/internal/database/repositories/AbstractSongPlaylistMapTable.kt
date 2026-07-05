package app.kreate.internal.database.repositories

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.kreate.constant.PlaylistSongSortBy
import app.kreate.constant.SortOrder
import app.kreate.database.models.Song
import app.kreate.database.models.SongPlaylistMap
import app.kreate.database.repositories.SongPlaylistMapTable
import app.kreate.util.MODIFIED_PREFIX
import app.kreate.util.toDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take


@Dao
@RewriteQueriesToDropUnusedColumns
internal abstract class AbstractSongPlaylistMapTable: SongPlaylistMapTable {

    override val tableName: String
        get() = "song_playlist_map"

    @Query("DELETE FROM  song_playlist_map WHERE song_id = :songId")
    abstract override fun deleteBySongId( songId: String ): Int

    @Query("DELETE FROM  song_playlist_map WHERE song_id = :songId AND playlist_id = :playlistId")
    abstract override fun deleteBySongId( songId: String, playlistId: Long ): Int

    @Query("DELETE FROM  song_playlist_map WHERE playlist_id = :playlistId")
    abstract override fun clear( playlistId: Long ): Int

    @Query("""
        DELETE FROM  song_playlist_map 
        WHERE song_id NOT IN (
            SELECT DISTINCT id
            FROM songs
        )
    """)
    abstract override fun clearGhostMaps(): Int

    @Query("""
        SELECT DISTINCT S.*
        FROM  song_playlist_map SPM
        JOIN songs S ON S.id = SPM.song_id
        WHERE SPM.playlist_id = :playlistId
        ORDER BY SPM.ROWID
        LIMIT :limit
    """)
    abstract override fun allSongsOf( playlistId: Long, limit: Int ): Flow<List<Song>>

    @Query("""
        SELECT DISTINCT S.*
        FROM  song_playlist_map SPM
        JOIN songs S ON S.id = SPM.song_id
        WHERE SPM.playlist_id = :playlistId
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    abstract override fun allSongsOfRandomized( playlistId: Long, limit: Int ): Flow<List<Song>>

    @Query("SELECT * FROM  song_playlist_map WHERE playlist_id = :playlistId AND song_id = :songId")
    abstract override fun findById( songId: String, playlistId: Long ): Flow<SongPlaylistMap?>

    @Query("""
        SELECT COALESCE(
            (
                SELECT 'position'
                FROM  song_playlist_map 
                WHERE song_id = :songId 
                AND playlist_id = :playlistId
            ),
            -1
        ) 
    """)
    abstract override fun findPositionOf( songId: String, playlistId: Long ): Int

    @Query("""
        SELECT COUNT(s.id) > 0
        FROM songs s
        JOIN  song_playlist_map spm ON spm.song_id = s.id 
        WHERE s.id = :songId
    """)
    abstract override fun isMapped( songId: String ): Flow<Boolean>

    @Query("""
        SELECT EXISTS(
            SELECT 1 
            FROM song_playlist_map 
            WHERE song_id = :songId
                AND playlist_id = :playlistId
        );
    """)
    abstract override fun isMapped( songId: String, playlistId: Long ): Flow<Boolean>

    @Query("""
        SELECT playlist_id
        FROM song_playlist_map
        WHERE song_id = :songId
    """)
    abstract override fun mappedTo( songId: String ): Flow<List<Long>>

    @Query("""
        UPDATE  song_playlist_map 
        SET position = shuffled.new_position
        FROM (
            SELECT spm1.song_id, ROW_NUMBER() OVER (ORDER BY RANDOM()) - 1 AS new_position
            FROM  song_playlist_map spm1
            WHERE spm1.playlist_id = :playlistId
        ) AS shuffled
        WHERE playlist_id = :playlistId
        AND shuffled.song_id = song_playlist_map.song_id 
    """)
    // You'll get complain from IDE that "shuffled" and "FROM"
    // aren't exist, that's OK - IGNORE it.
    abstract override fun shufflePositions( playlistId: Long ): Int

    @Query("""
        UPDATE song_playlist_map
        SET position = updated.new_position
        FROM (
            SELECT 
                spm.song_id, 
                spm.position,
                CASE 
                    WHEN spm.position = :from THEN :to 
                    WHEN spm.position > :from AND spm.position <= :to THEN position - 1 
                    WHEN spm.position < :from AND spm.position >= :to THEN position + 1 
                    ELSE spm.position
                END AS new_position
            FROM  song_playlist_map spm
            WHERE spm.playlist_id = :playlistId
        ) AS updated
        WHERE playlist_id = :playlistId
        AND updated.song_id = song_playlist_map.song_id
    """)
    // You'll get complain from IDE that "updated" and "FROM"
    // aren't exist, that's OK - IGNORE it.
    abstract override fun move( playlistId: Long, from: Int, to: Int ): Int

    @Query("""
        INSERT OR IGNORE INTO  song_playlist_map ( song_id, playlist_id, position )
        VALUES( 
            :songId,
            :playlistId,
            COALESCE(
                (
                    SELECT MAX(position) + 1 
                    FROM  song_playlist_map 
                    WHERE playlist_id = :playlistId
                ), 
                0
            )
        )
    """)
    abstract override fun map( songId: String, playlistId: Long )

    @Query("""
        SELECT DISTINCT s.*
        FROM  song_playlist_map spm
        LEFT JOIN songs s ON s.id = spm.song_id
        WHERE spm.playlist_id = :playlistId
        ORDER BY s.total_playtime DESC
        LIMIT :limit
    """)
    abstract override fun findMostPlayedSongsOf(playlistId: Long, limit: Int ): Flow<List<Song>>

    override fun findThumbnailsOfMostPlayedSongIn( playlistId: Long, limit: Int ): Flow<List<String>> =
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

    //<editor-fold defaultstate="collapsed" desc="Sort songs of playlist">
    @Query("""
        SELECT DISTINCT S.*
        FROM  song_playlist_map spm
        LEFT JOIN songs S ON S.id = spm.song_id
        LEFT JOIN song_album_map sam ON sam.song_id = S.id
        LEFT JOIN albums A ON A.id = sam.album_id
        WHERE spm.playlist_id = :playlistId
        ORDER BY 
            A.title IS NULL, 
            CASE
                WHEN A.title LIKE "$MODIFIED_PREFIX%" THEN SUBSTR(A.title, LENGTH('$MODIFIED_PREFIX') + 1)
                ELSE A.title
            END
        LIMIT :limit
    """)
    abstract fun sortSongsByAlbum( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    @Query("""
        SELECT DISTINCT S.*
        FROM  song_playlist_map spm
        LEFT JOIN songs S ON S.id = spm.song_id
        LEFT JOIN song_album_map sam ON sam.song_id = S.id
        LEFT JOIN albums A ON A.id = sam.album_id
        WHERE spm.playlist_id = :playlistId
        ORDER BY A.year IS NULL, A.year
        LIMIT :limit
    """)
    abstract fun sortSongsByAlbumYear( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    fun sortSongsByArtist( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>> =
        allSongsOf( playlistId, limit ).map { list ->
            list.sortedBy( Song::cleanArtistsText )
        }

    @Query("""
        SELECT DISTINCT S.*
        FROM  song_playlist_map spm
        LEFT JOIN songs S ON S.id = spm.song_id
        LEFT JOIN song_album_map sam ON sam.song_id = S.id
        LEFT JOIN albums A ON A.id = sam.album_id
        WHERE spm.playlist_id = :playlistId
        ORDER BY 
            A.title IS NULL, 
            S.artists IS NULL,
            CASE
                WHEN A.title LIKE "$MODIFIED_PREFIX%" THEN SUBSTR(A.title, LENGTH('$MODIFIED_PREFIX') + 1)
                ELSE A.title
            END,
            CASE
                WHEN S.artists LIKE "$MODIFIED_PREFIX%" THEN SUBSTR(S.artists, LENGTH('$MODIFIED_PREFIX') + 1)
                ELSE S.artists
            END
        LIMIT :limit
    """)
    abstract fun sortSongsByAlbumAndArtist( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    @Query("""
        SELECT S.*
        FROM  song_playlist_map spm
        LEFT JOIN songs S ON S.id = spm.song_id
        LEFT JOIN playback_history E ON E.song_id = S.id
        WHERE spm.playlist_id = :playlistId
        ORDER BY E.created_at
        LIMIT :limit
    """)
    abstract fun sortSongsByDatePlayed( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

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
        FROM  song_playlist_map spm
        LEFT JOIN songs S ON S.id = spm.song_id
        WHERE spm.playlist_id = :playlistId
        ORDER BY spm.position
        LIMIT :limit
    """)
    abstract fun sortSongsByPosition( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    fun sortSongsByTitle( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>> =
        allSongsOf( playlistId, limit ).map { list ->
            list.sortedBy( Song::cleanTitle )
        }

    fun sortSongsByDuration( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>> =
        allSongsOf( playlistId, limit ).map { list ->
            list.sortedBy { it.durationText.toDuration() }
        }

    fun sortSongsByLikedAt( playlistId: Long, limit: Int = Int.MAX_VALUE ): Flow<List<Song>> =
        allSongsOf( playlistId, limit ).map { list ->
            list.sortedBy( Song::likedAt )
        }

    override fun sortSongs(
        playlistId: Long,
        sortBy: PlaylistSongSortBy,
        sortOrder: SortOrder,
        limit: Int
    ): Flow<List<Song>> = when( sortBy ) {
        PlaylistSongSortBy.ALBUM                -> sortSongsByAlbum( playlistId )
        PlaylistSongSortBy.ALBUM_YEAR           -> sortSongsByAlbumYear( playlistId )
        PlaylistSongSortBy.ARTIST               -> sortSongsByArtist( playlistId )
        PlaylistSongSortBy.ARTIST_AND_ALBUM     -> sortSongsByAlbumAndArtist( playlistId )
        PlaylistSongSortBy.DATE_PLAYED          -> sortSongsByDatePlayed( playlistId )
        PlaylistSongSortBy.TOTAL_PLAY_TIME      -> sortSongsByPlayTime( playlistId )
        PlaylistSongSortBy.RELATIVE_PLAY_TIME   -> sortSongsByRelativePlayTime( playlistId )
        PlaylistSongSortBy.POSITION             -> sortSongsByPosition( playlistId )
        PlaylistSongSortBy.TITLE                -> sortSongsByTitle( playlistId )
        PlaylistSongSortBy.DURATION             -> sortSongsByDuration( playlistId )
        PlaylistSongSortBy.DATE_LIKED           -> sortSongsByLikedAt( playlistId )
        PlaylistSongSortBy.DATE_ADDED           -> allSongsOf( playlistId )     // Already sorted by ROWID
        PlaylistSongSortBy.RANDOM               -> allSongsOfRandomized( playlistId )
    }.map( sortOrder::applyTo ).take( limit )
    //</editor-fold>
}