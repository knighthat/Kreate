package app.kreate.internal.database.repositories

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.kreate.constant.SongSortBy
import app.kreate.constant.SortOrder
import app.kreate.database.ext.FormatWithSong
import app.kreate.database.models.Format
import app.kreate.database.repositories.FormatTable
import app.kreate.util.MODIFIED_PREFIX
import app.kreate.util.toDuration
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


@Dao
@RewriteQueriesToDropUnusedColumns
internal abstract class AbstractFormatTable: FormatTable {

    override val tableName: String
        get() = "formats"

    @Query("""
        SELECT DISTINCT F.*, S.* 
        FROM formats F
        JOIN songs S ON S.id = F.song_id
        WHERE total_playtime >= :excludeHidden
        ORDER BY S.ROWID 
        LIMIT :limit
    """)
    abstract override fun allWithSongs( limit: Int, excludeHidden: Boolean ): Flow<List<FormatWithSong>>

    @Query("""
        SELECT DISTINCT F.*, S.* 
        FROM formats F
        JOIN songs S ON S.id = F.song_id
        WHERE total_playtime >= :excludeHidden
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    abstract override fun allWithSongsRandomized( limit: Int, excludeHidden: Boolean ): Flow<List<FormatWithSong>>

    @Query("DELETE FROM formats WHERE song_id IN (:songIds)")
    abstract override fun deleteBySongId( songIds: List<String> ): Int

    override fun deleteBySongId( vararg songIds: String ): Int = deleteBySongId( songIds.toList() )

    @Query("SELECT DISTINCT * FROM formats WHERE song_id = :songId")
    abstract override fun findBySongId( songId: String ): Flow<Format?>

    @Query("""
        SELECT COALESCE(
            (
                SELECT length
                FROM formats
                WHERE song_id = :songId
            ),
            0
        )
    """)
    abstract override fun findContentLengthOf( songId: String ): Flow<Long>

    @Query("UPDATE formats SET length = :contentLength WHERE song_id = :songId")
    abstract override fun updateContentLengthOf( songId: String, contentLength: Long ): Int

    //<editor-fold defaultstate="collapsed" desc="Sort all with songs">
    fun sortAllWithSongsByPlayTime( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<FormatWithSong>> =
        allWithSongs( limit, excludeHidden ).map { list ->
            list.sortedBy { it.song.totalPlayTimeMs }
        }

    fun sortAllWithSongsByRelativePlayTime( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<FormatWithSong>> =
        allWithSongs( limit, excludeHidden ).map { list ->
            list.sortedBy { it.song.relativePlayTime() }
        }

    fun sortAllWithSongsByTitle( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<FormatWithSong>> =
        allWithSongs( limit, excludeHidden ).map { list ->
            list.sortedBy { it.song.cleanTitle() }
        }

    @Query("""
        SELECT DISTINCT F.*, S.*
        FROM formats F
        JOIN songs S ON S.id = F.song_id
        LEFT JOIN playback_history E ON E.song_id = F.song_id 
        WHERE total_playtime >= :excludeHidden
        ORDER BY E.created_at
        LIMIT :limit
    """)
    abstract fun sortAllWithSongsByDatePlayed( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<FormatWithSong>>

    fun sortAllWithSongsByLikedAt( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<FormatWithSong>> =
        allWithSongs( limit, excludeHidden ).map { list ->
            list.sortedBy { it.song.likedAt }
        }

    fun sortAllWithSongsByArtist( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<FormatWithSong>> =
        allWithSongs( limit, excludeHidden ).map { list ->
            list.sortedBy { it.song.cleanArtistsText() }
        }

    fun sortAllWithSongsByDuration( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<FormatWithSong>> =
        allWithSongs( limit, excludeHidden ).map { list ->
            list.sortedBy { it.song.durationText.toDuration() }
        }

    @Query("""
        SELECT DISTINCT F.*, S.*
        FROM formats F
        JOIN songs S ON S.id = F.song_id
        LEFT JOIN song_album_map sam ON sam.song_id = S.id
        LEFT JOIN albums A ON A.id = sam.album_id
        WHERE total_playtime >= :excludeHidden
        ORDER BY 
            CASE 
                WHEN A.title LIKE '$MODIFIED_PREFIX%' THEN SUBSTR(A.title, LENGTH('$MODIFIED_PREFIX') + 1)
                ELSE A.title
            END
        LIMIT :limit
    """)
    abstract fun sortAllWithSongsByAlbumName( limit: Int = Int.MAX_VALUE, excludeHidden: Boolean = false ): Flow<List<FormatWithSong>>

    override fun sortAllWithSongs(
        sortBy: SongSortBy,
        sortOrder: SortOrder,
        limit: Int,
        excludeHidden: Boolean
    ): Flow<List<FormatWithSong>> = when( sortBy ){
        SongSortBy.TOTAL_PLAY_TIME      -> sortAllWithSongsByPlayTime( limit, excludeHidden )
        SongSortBy.RELATIVE_PLAY_TIME   -> sortAllWithSongsByRelativePlayTime( limit, excludeHidden )
        SongSortBy.TITLE                -> sortAllWithSongsByTitle( limit, excludeHidden )
        SongSortBy.DATE_ADDED           -> allWithSongs( limit, excludeHidden )      // Already sorted by ROWID
        SongSortBy.DATE_PLAYED          -> sortAllWithSongsByDatePlayed( limit, excludeHidden )
        SongSortBy.DATE_LIKED           -> sortAllWithSongsByLikedAt( limit, excludeHidden )
        SongSortBy.ARTIST               -> sortAllWithSongsByArtist( limit, excludeHidden )
        SongSortBy.DURATION             -> sortAllWithSongsByDuration( limit, excludeHidden )
        SongSortBy.ALBUM                -> sortAllWithSongsByAlbumName( limit, excludeHidden )
        SongSortBy.RANDOM               -> allWithSongsRandomized()
    }.map( sortOrder::applyTo )
    //</editor-fold>
}