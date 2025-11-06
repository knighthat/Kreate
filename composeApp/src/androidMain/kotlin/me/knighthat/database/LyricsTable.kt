package me.knighthat.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import app.kreate.database.models.Lyrics
import app.kreate.database.table.DatabaseTable
import kotlinx.coroutines.flow.Flow

@Dao
@RewriteQueriesToDropUnusedColumns
interface LyricsTable: DatabaseTable<Lyrics> {

    override val tableName: String
        get() = Lyrics::class.simpleName!!

    /**
     * @param songId of song to look for
     * @return [Lyrics] that has [Lyrics.songId] matches [songId]
     */
    @Query("SELECT DISTINCT * FROM Lyrics WHERE songId = :songId")
    fun findBySongId( songId: String ): Flow<Lyrics?>
}