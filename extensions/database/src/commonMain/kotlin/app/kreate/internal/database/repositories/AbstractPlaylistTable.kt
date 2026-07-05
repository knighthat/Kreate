package app.kreate.internal.database.repositories

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.kreate.constant.PlaylistSortBy
import app.kreate.constant.SortOrder
import app.kreate.database.models.Playlist
import app.kreate.database.models.PlaylistPreview
import app.kreate.database.models.Song
import app.kreate.database.repositories.PlaylistTable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take


@Dao
@RewriteQueriesToDropUnusedColumns
abstract class AbstractPlaylistTable: PlaylistTable {

    override val tableName: String
        get() = "playlists"

    @Query("""
        SELECT DISTINCT S.*
        FROM song_playlist_map spm
        JOIN songs S ON S.id = spm.song_id
        ORDER BY S.ROWID
        LIMIT :limit
    """)
    abstract override fun allSongs( limit: Int ): Flow<List<Song>>

    @Query("""
        SELECT DISTINCT S.*
        FROM song_playlist_map spm
        JOIN songs S ON S.id = spm.song_id
        JOIN playlists P ON P.id = spm.playlist_id
        WHERE P.is_pinned
        ORDER BY S.ROWID
        LIMIT :limit
    """)
    abstract override fun allPinnedSongs( limit: Int ): Flow<List<Song>>

    @Query("""
        SELECT DISTINCT S.*
        FROM song_playlist_map spm
        JOIN songs S ON S.id = spm.song_id
        JOIN playlists P ON P.id = spm.playlist_id
        WHERE P.youtube
        ORDER BY S.ROWID
        LIMIT :limit
    """)
    abstract override fun allYTPlaylistSongs( limit: Int ): Flow<List<Song>>

    @Query("""
        SELECT DISTINCT S.*
        FROM song_playlist_map spm
        JOIN songs S ON S.id = spm.song_id
        JOIN playlists P ON P.id = spm.playlist_id
        WHERE P.is_monthly
        ORDER BY S.ROWID
        LIMIT :limit
    """)
    abstract override fun allMonthlySongs( limit: Int ): Flow<List<Song>>

    @Query("""
        SELECT DISTINCT 
            *,
            (
                SELECT COUNT(song_id)
                FROM song_playlist_map
                WHERE playlist_id = id
            ) as songCount
        FROM playlists
        ORDER BY ROWID
        LIMIT :limit
    """)
    abstract override fun allAsPreview( limit: Int ): Flow<List<PlaylistPreview>>

    @Query("""
        SELECT DISTINCT 
            *,
            (
                SELECT COUNT(song_id)
                FROM song_playlist_map
                WHERE playlist_id = id
            ) as songCount
        FROM playlists
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    abstract override fun allAsPreviewRandomized( limit: Int ): Flow<List<PlaylistPreview>>

    @Query("SELECT DISTINCT * FROM playlists WHERE browse_id = :browseId")
    abstract override fun findByBrowseId( browseId: String ): Flow<Playlist?>

    @Query("""
        SELECT DISTINCT * 
        FROM playlists 
        WHERE trim(name) COLLATE NOCASE = trim(:playlistName) COLLATE NOCASE
        LIMIT 1
    """)
    abstract override fun findByName( playlistName: String ): Flow<Playlist?>

    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    abstract override fun findById( playlistId: Long ): Flow<Playlist?>

    @Insert
    abstract override fun insert( playlist: Playlist ): Long

    @Query("""
        SELECT COUNT(*) > 0
        FROM playlists
        WHERE name = :playlistName
    """)
    abstract override fun exists( playlistName: String ): Flow<Boolean>

    @Query("""
        UPDATE playlists
        SET is_pinned = 
            CASE
                WHEN is_pinned = 1 THEN 0
                ELSE 1
            END
        WHERE id = :playlistId
    """)
    abstract override fun togglePin( playlistId: Long ): Int

    //<editor-fold defaultstate="collapsed" desc="Sort as preview">
    @Query("""
        SELECT DISTINCT P.*, COUNT(spm.song_id) as songCount
        FROM song_playlist_map spm
        JOIN playlists P ON P.id = spm.playlist_id
        JOIN songs S ON S.id = spm.song_id
        GROUP BY P.id
        ORDER BY SUM(S.total_playtime)
        LIMIT :limit
    """)
    abstract fun sortPreviewsByMostPlayed( limit: Int = Int.MAX_VALUE ): Flow<List<PlaylistPreview>>

    fun sortPreviewsByName( limit: Int = Int.MAX_VALUE ): Flow<List<PlaylistPreview>> =
        allAsPreview( limit ).map { list ->
            list.sortedBy { it.playlist.cleanName() }
        }

    fun sortPreviewsBySongCount( limit: Int = Int.MAX_VALUE ): Flow<List<PlaylistPreview>> =
        allAsPreview( limit ).map { list ->
            list.sortedBy( PlaylistPreview::songCount )
        }

    override fun sortPreviews(
        sortBy: PlaylistSortBy,
        sortOrder: SortOrder,
        limit: Int
    ): Flow<List<PlaylistPreview>> = when( sortBy ) {
        PlaylistSortBy.TOTAL_PLAY_TIME  -> sortPreviewsByMostPlayed()
        PlaylistSortBy.TITLE            -> sortPreviewsByName()
        PlaylistSortBy.DATE_ADDED       -> allAsPreview()       // Already sorted by ROWID
        PlaylistSortBy.SONG_COUNT       -> sortPreviewsBySongCount()
        PlaylistSortBy.RANDOM           -> allAsPreviewRandomized()
    }.map( sortOrder::applyTo ).take( limit )
    //</editor-fold>
}