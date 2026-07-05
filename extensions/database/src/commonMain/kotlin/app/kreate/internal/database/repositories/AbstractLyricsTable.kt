package app.kreate.internal.database.repositories

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.kreate.database.models.Lyrics
import app.kreate.database.repositories.LyricsTable
import kotlinx.coroutines.flow.Flow


@Dao
@RewriteQueriesToDropUnusedColumns
abstract class AbstractLyricsTable: LyricsTable {

    override val tableName: String
        get() = "lyrics"

    @Query("SELECT DISTINCT * FROM lyrics WHERE song_id = :songId")
    abstract override fun findBySongId( songId: String ): Flow<Lyrics?>
}