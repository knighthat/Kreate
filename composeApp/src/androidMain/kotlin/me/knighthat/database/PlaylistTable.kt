package me.knighthat.database

import android.database.SQLException
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.kreate.database.models.Artist
import app.kreate.database.models.Playlist
import app.kreate.database.models.PlaylistPreview
import app.kreate.database.models.Song
import app.kreate.database.table.DatabaseTable
import it.fast4x.rimusic.enums.PlaylistSortBy
import it.fast4x.rimusic.enums.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

@Dao
@RewriteQueriesToDropUnusedColumns
interface PlaylistTable: DatabaseTable<Playlist> {

    override val tableName: String
        get() = Playlist::class.simpleName!!

    /**
     * @return list of songs that were mapped to at least 1 playlist
     */
    @Query("""
        SELECT DISTINCT S.*
        FROM SongPlaylistMap spm
        JOIN Song S ON S.id = spm.songId
        ORDER BY S.ROWID
        LIMIT :limit
    """)
    fun allSongs( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @return list of songs that were mapped to at least 1 **pinned** playlist
     */
    @Query("""
        SELECT DISTINCT S.*
        FROM SongPlaylistMap spm
        JOIN Song S ON S.id = spm.songId
        JOIN Playlist P ON P.id = spm.playlistId
        WHERE P.is_pinned COLLATE NOCASE
        ORDER BY S.ROWID
        LIMIT :limit
    """)
    fun allPinnedSongs( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @return list of songs that belong YouTube private playlist
     */
    @Query("""
        SELECT DISTINCT S.*
        FROM SongPlaylistMap spm
        JOIN Song S ON S.id = spm.songId
        JOIN Playlist P ON P.id = spm.playlistId
        WHERE P.isYoutubePlaylist
        ORDER BY S.ROWID
        LIMIT :limit
    """)
    fun allYTPlaylistSongs( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @return list of songs that were mapped to at least 1 **monthly** playlist
     */
    @Query("""
        SELECT DISTINCT S.*
        FROM SongPlaylistMap spm
        JOIN Song S ON S.id = spm.songId
        JOIN Playlist P ON P.id = spm.playlistId
        WHERE P.is_monthly COLLATE NOCASE
        ORDER BY S.ROWID
        LIMIT :limit
    """)
    fun allMonthlySongs( limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @return all playlists from this table with number of songs they carry
     */
    @Query("""
        SELECT DISTINCT 
            *,
            (
                SELECT COUNT(songId)
                FROM SongPlaylistMap
                WHERE playlistId = id
            ) as songCount
        FROM Playlist
        ORDER BY ROWID
        LIMIT :limit
    """)
    fun allAsPreview( limit: Int = Int.MAX_VALUE ): Flow<List<PlaylistPreview>>

    /**
     * @return all playlists from this table with number of songs they carry in randomized order
     */
    @Query("""
        SELECT DISTINCT 
            *,
            (
                SELECT COUNT(songId)
                FROM SongPlaylistMap
                WHERE playlistId = id
            ) as songCount
        FROM Playlist
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    fun allAsPreviewRandomized( limit: Int = Int.MAX_VALUE ): Flow<List<PlaylistPreview>>

    /**
     * @param browseId of playlist to look for
     * @return [Playlist] that has [Playlist.browseId] matches [browseId]
     */
    @Query("SELECT DISTINCT * FROM Playlist WHERE browseId = :browseId")
    fun findByBrowseId( browseId: String ): Flow<Playlist?>

    /**
     * @return [Playlist] that has [Playlist.name] equals to [playlistName], case-insensitive
     */
    @Query("""
        SELECT DISTINCT * 
        FROM Playlist 
        WHERE trim(name) COLLATE NOCASE = trim(:playlistName) COLLATE NOCASE
        LIMIT 1
    """)
    fun findByName( playlistName: String ): Flow<Playlist?>

    @Query("""
        SELECT *
        FROM Playlist
        WHERE trim(name) COLLATE NOCASE = trim(:playlistName) COLLATE NOCASE
        AND is_monthly = 1
        LIMIT 1
    """)
    fun findMonthlyPlaylistByName( playlistName: String ): Flow<Playlist?>

    /**
     * @return playlist with id [playlistId]
     */
    @Query("SELECT * FROM Playlist WHERE id = :playlistId")
    fun findById( playlistId: Long ): Flow<Playlist?>

    /**
     * Attempt to write [playlist] into database.
     *
     * ### Standalone use
     *
     * When error occurs and [SQLException] is thrown,
     * the process is cancel and passes exception to caller.
     *
     * ### Transaction use
     *
     * When error occurs and [SQLException] is thrown,
     * **the entire transaction rolls back** and passes exception to caller.
     *
     * > Note: Use this if inserting record is crucial for
     * > the transaction to continue.
     *
     * @param playlist intended to insert in to database
     * @return ROWID of this new record, throws exception when fail
     * @throws SQLException when there's a conflict
     */
    @Insert
    @Throws(SQLException::class)
    fun insert( playlist: Playlist ): Long

    /**
     * Toggle [Playlist.isPinned]
     *
     * @return number of rows affected
     */
    @Query("""
        UPDATE Playlist
        SET is_pinned = 
            CASE 
                WHEN is_pinned = 0 THEN 1
                ELSE 0
            END
        WHERE id = :playlistId
    """)
    fun togglePin( playlistId: Long ): Int

    //<editor-fold defaultstate="collapsed" desc="Sort as preview">
    @Query("""
        SELECT DISTINCT P.*, COUNT(spm.songId) as songCount
        FROM SongPlaylistMap spm
        JOIN Playlist P ON P.id = spm.playlistId
        JOIN Song S ON S.id = spm.songId
        GROUP BY P.id
        ORDER BY SUM(S.totalPlayTimeMs)
        LIMIT :limit
    """)
    fun sortPreviewsByMostPlayed( limit: Int = Int.MAX_VALUE ): Flow<List<PlaylistPreview>>

    fun sortPreviewsByName( limit: Int = Int.MAX_VALUE ): Flow<List<PlaylistPreview>> =
        allAsPreview( limit ).map { list ->
            list.sortedBy { it.playlist.cleanName() }
        }

    fun sortPreviewsBySongCount( limit: Int = Int.MAX_VALUE ): Flow<List<PlaylistPreview>> =
        allAsPreview( limit ).map { list ->
            list.sortedBy( PlaylistPreview::songCount )
        }

    /**
     * Fetch all playlists, sort them according to [sortBy] and [sortOrder],
     * and return [PlaylistPreview] as the result.
     *
     * [sortBy] sorts all based on each playlist's property
     * such as [PlaylistSortBy.Name], [PlaylistSortBy.DateAdded], etc.
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
    ): Flow<List<PlaylistPreview>> = when( sortBy ) {
        PlaylistSortBy.MostPlayed   -> sortPreviewsByMostPlayed()
        PlaylistSortBy.Name         -> sortPreviewsByName()
        PlaylistSortBy.DateAdded    -> allAsPreview()       // Already sorted by ROWID
        PlaylistSortBy.SongCount    -> sortPreviewsBySongCount()
        PlaylistSortBy.RANDOM       -> allAsPreviewRandomized()
    }.map( sortOrder::applyTo ).take( limit )
    //</editor-fold>
}