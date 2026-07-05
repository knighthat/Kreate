package app.kreate.database.repositories

import app.kreate.database.models.Artist
import app.kreate.database.models.Song
import app.kreate.database.models.SongArtistMap
import app.kreate.database.table.DatabaseTable
import kotlinx.coroutines.flow.Flow


interface SongArtistMapTable: DatabaseTable<SongArtistMap> {

    /**
     * @param artistId of artist to look for
     * @param limit number of results cannot go over this value
     *
     * @return all [Song]s that were mapped to artist has [Artist.id] matches [artistId]
     */
    fun allSongsBy( artistId: String, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @return all [Artist]s featured in this song
     */
    fun findArtistsOf( songId: String, limit: Int = Int.MAX_VALUE ): Flow<List<Artist>>

    fun findArtistMostPlayedSongs( artistId: String, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * Delete all mappings where songs aren't exist in `Song` table
     *
     * @return number of rows affected by this operation
     */
    fun clearGhostMaps(): Int
}