package app.kreate.database.repositories

import app.kreate.database.models.Lyrics
import app.kreate.database.table.DatabaseTable
import kotlinx.coroutines.flow.Flow


interface LyricsTable: DatabaseTable<Lyrics> {

    /**
     * @param songId of song to look for
     * @return [Lyrics] that has [Lyrics.songId] matches [songId]
     */
    fun findBySongId( songId: String ): Flow<Lyrics?>
}