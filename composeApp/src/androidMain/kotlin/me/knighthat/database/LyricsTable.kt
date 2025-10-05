package me.knighthat.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Upsert
import app.kreate.database.models.Lyrics
import kotlinx.coroutines.flow.Flow

@Dao
@RewriteQueriesToDropUnusedColumns
interface LyricsTable {

    /**
     * @param songId of song to look for
     * @return [Lyrics] that has [Lyrics.songId] matches [songId]
     */
    @Query("SELECT DISTINCT * FROM Lyrics WHERE songId = :songId")
    fun findBySongId( songId: String ): Flow<Lyrics?>

    /**
     * Attempt to write [lyrics] into database.
     *
     * If [lyrics] exist (determined by its primary key),
     * existing record's columns will be replaced
     * by provided [lyrics]' data.
     *
     * @param lyrics data intended to insert in to database
     * @return ROWID of successfully modified record
     */
    @Upsert
    fun upsert( lyrics: Lyrics ): Long
}