package app.kreate.internal.database.repositories

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.kreate.constant.ArtistSortBy
import app.kreate.constant.SortOrder
import app.kreate.database.models.Artist
import app.kreate.database.models.Song
import app.kreate.database.repositories.ArtistTable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take

@Dao
@RewriteQueriesToDropUnusedColumns
abstract class AbstractArtistTable: ArtistTable {

    override val tableName: String
        get() = "artists"

    @Query("""
        SELECT DISTINCT * 
        FROM artists
        WHERE bookmarked_at IS NOT NULL
        ORDER BY ROWID 
        LIMIT :limit
    """)
    abstract override fun allFollowing( limit: Int ): Flow<List<Artist>>

    @Query("""
        SELECT DISTINCT * 
        FROM artists
        WHERE bookmarked_at IS NOT NULL
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    abstract override fun allFollowingRandomized( limit: Int ): Flow<List<Artist>>

    @Query("""
        SELECT DISTINCT A.*
        FROM artists A
        JOIN song_artist_map sam ON sam.artist_id = A.id
        JOIN song_playlist_map spm ON spm.song_id = sam.song_id
        ORDER BY A.ROWID
        LIMIT :limit
    """)
    abstract override fun allInLibrary( limit: Int ): Flow<List<Artist>>

    @Query("""
        SELECT DISTINCT A.*
        FROM artists A
        JOIN song_artist_map sam ON sam.artist_id = A.id
        JOIN song_playlist_map spm ON spm.song_id = sam.song_id
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    abstract override fun allInLibraryRandomized( limit: Int ): Flow<List<Artist>>

    @Query("""
        SELECT DISTINCT S.*
        FROM song_artist_map sam
        JOIN artists A ON A.id = sam.artist_id
        JOIN songs S ON S.id = sam.song_id
        WHERE A.bookmarked_at IS NOT NULL
        ORDER BY S.ROWID
        LIMIT :limit
    """)
    abstract override fun allSongsInFollowing( limit: Int ): Flow<List<Song>>

    @Query("SELECT DISTINCT * FROM artists WHERE id = :artistId")
    abstract override fun findById( artistId: String ): Flow<Artist?>

    @Query("""
        SELECT DISTINCT artists.*
        FROM song_artist_map
        JOIN artists ON id = artist_id
        WHERE song_id = :songId
    """)
    abstract override fun findBySongId( songId: String ): Flow<List<Artist>>

    @Query("""
        SELECT COALESCE(
            (
                SELECT 1 
                FROM artists 
                WHERE id = :artistId 
                AND bookmarked_at IS NOT NULL 
            ),
            0
        )
    """)
    abstract override fun isFollowing( artistId: String ): Flow<Boolean>

    @Query("""
        UPDATE artists
        SET bookmarked_at = CASE
            WHEN bookmarked_at IS NULL THEN strftime('%s', 'now') * 1000
            ELSE NULL
        END
        WHERE id = :artistId
    """)
    abstract override fun toggleFollow( artistId: String ): Int

    //<editor-fold defaultstate="collapsed" desc="Sort all">
    fun sortFollowingByName( limit: Int = Int.MAX_VALUE ): Flow<List<Artist>> =
        allFollowing( limit ).map { list ->
            list.sortedBy( Artist::cleanName )
        }

    override fun sortFollowing(
        sortBy: ArtistSortBy,
        sortOrder: SortOrder,
        limit: Int
    ): Flow<List<Artist>> = when( sortBy ) {
        ArtistSortBy.TITLE      -> sortFollowingByName()
        ArtistSortBy.DATE_ADDED -> allFollowing()
        ArtistSortBy.RANDOM     -> allFollowingRandomized()
    }.map( sortOrder::applyTo ).take( limit )
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Sort artists in library">
    fun sortInLibraryByName( limit: Int = Int.MAX_VALUE ): Flow<List<Artist>> =
        allInLibrary( limit ).map { list ->
            list.sortedBy( Artist::cleanName )
        }

    override fun sortInLibrary(
        sortBy: ArtistSortBy,
        sortOrder: SortOrder,
        limit: Int
    ): Flow<List<Artist>> = when( sortBy ) {
        ArtistSortBy.TITLE      -> sortInLibraryByName()
        ArtistSortBy.DATE_ADDED -> allInLibrary()     // Already sorted by ROWID
        ArtistSortBy.RANDOM     -> allInLibraryRandomized()
    }.map( sortOrder::applyTo ).take( limit )
    //</editor-fold>
}