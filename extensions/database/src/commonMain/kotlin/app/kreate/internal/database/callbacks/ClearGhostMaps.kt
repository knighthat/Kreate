package app.kreate.internal.database.callbacks

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import co.touchlab.kermit.Logger


internal class ClearGhostMaps : RoomDatabase.Callback() {

    override fun onOpen(connection: SQLiteConnection) =
        try {
            connection.execSQL("""
                DELETE FROM formats 
                WHERE song_id NOT IN (SELECT id FROM songs);
            """.trimIndent())
            connection.execSQL("""
                DELETE FROM lyrics 
                WHERE song_id NOT IN (SELECT id FROM songs);
            """.trimIndent())
            connection.execSQL("""
                DELETE FROM persistent_queue 
                WHERE song_id NOT IN (SELECT id FROM songs);
            """.trimIndent())
            connection.execSQL("""
                DELETE FROM playback_history 
                WHERE song_id NOT IN (SELECT id FROM songs);
            """.trimIndent())
            connection.execSQL("""
                DELETE FROM song_album_map 
                WHERE song_id NOT IN (SELECT id FROM songs)
                OR album_id NOT IN (SELECT id FROM albums);
            """.trimIndent())
            connection.execSQL("""
                DELETE FROM song_artist_map 
                WHERE song_id NOT IN (SELECT id FROM songs)
                OR artist_id NOT IN (SELECT id FROM artists);
            """.trimIndent())
            connection.execSQL("""
                DELETE FROM song_playlist_map 
                WHERE song_id NOT IN (SELECT id FROM songs)
                OR playlist_id NOT IN (SELECT id FROM playlists);
            """.trimIndent())
        } catch( err: Exception ) {
            Logger.e( "failed to clear ghost maps", err, "ClearGhostMaps" )
        }
}