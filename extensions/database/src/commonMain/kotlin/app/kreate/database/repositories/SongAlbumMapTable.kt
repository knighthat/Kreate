package app.kreate.database.repositories

import app.kreate.database.models.Album
import app.kreate.database.models.Song
import app.kreate.database.models.SongAlbumMap
import app.kreate.database.table.DatabaseTable
import kotlinx.coroutines.flow.Flow


interface SongAlbumMapTable: DatabaseTable<SongAlbumMap> {

    /**
     * Remove all songs belong to album with id [albumId]
     *
     * @param albumId album to have its songs wiped
     *
     * @return number of rows affected by this operation
     */
    fun clear( albumId: String ): Int

    /**
     * Delete all mappings where songs aren't exist in `Song` table
     *
     * @return number of rows affected by this operation
     */
    fun clearGhostMaps(): Int

    /**
     * Results are sorted by [SongAlbumMap.position].
     *
     * @param albumId of artist to look for
     * @param limit number of results cannot go over this value
     *
     * @return all [Song]s that were mapped to album has [Album.id] matches [albumId],
     * sorted by song's position in album
     */
    fun allSongsOf( albumId: String, limit: Int = Int.MAX_VALUE ): Flow<List<Song>>

    /**
     * @return [Album] that the song belongs to
     */
    fun findAlbumOf( songId: String, limit: Int = Int.MAX_VALUE ): Flow<Album?>

    fun map( songId: String, albumId: String, position: Int = -1 )
}