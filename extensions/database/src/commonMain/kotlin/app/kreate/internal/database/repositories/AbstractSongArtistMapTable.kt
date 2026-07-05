package app.kreate.internal.database.repositories

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.kreate.database.models.Artist
import app.kreate.database.models.Song
import app.kreate.database.repositories.SongArtistMapTable
import kotlinx.coroutines.flow.Flow


@Dao
@RewriteQueriesToDropUnusedColumns
internal abstract class AbstractSongArtistMapTable: SongArtistMapTable {

    override val tableName: String
        get() = "song_artist_map"

    @Query("""
        SELECT DISTINCT songs.*
        FROM song_artist_map sam 
        JOIN songs ON songs.id = sam.song_id
        WHERE sam.artist_id = :artistId
        ORDER BY songs.ROWID
        LIMIT :limit
    """)
    abstract override fun allSongsBy( artistId: String, limit: Int ): Flow<List<Song>>

    @Query("""
        SELECT DISTINCT A.*
        FROM artists A
        JOIN song_artist_map SAM ON SAM.artist_id = A.id
        WHERE SAM.song_id = :songId
        ORDER BY A.ROWID
        LIMIT :limit
    """)
    abstract override fun findArtistsOf( songId: String, limit: Int ): Flow<List<Artist>>

    @Query("""
        SELECT DISTINCT S.*
        FROM songs S
        JOIN song_artist_map SAM ON SAM.song_id = S.id
        JOIN artists A ON A.id = SAM.artist_id
        WHERE A.id = :artistId
        ORDER BY S.total_playtime DESC
        LIMIT :limit
    """)
    abstract override fun findArtistMostPlayedSongs( artistId: String, limit: Int ): Flow<List<Song>>

    @Query("""
        DELETE FROM song_artist_map 
        WHERE song_id NOT IN (
            SELECT DISTINCT id
            FROM songs
        )
    """)
    abstract override fun clearGhostMaps(): Int
}