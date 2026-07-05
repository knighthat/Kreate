package app.kreate.database

import androidx.room.RoomDatabase
import androidx.room.useReaderConnection
import androidx.room.useWriterConnection
import app.kreate.database.Database.asyncQuery
import app.kreate.database.Database.asyncTransaction
import app.kreate.database.repositories.AlbumTable
import app.kreate.database.repositories.ArtistTable
import app.kreate.database.repositories.EventTable
import app.kreate.database.repositories.FormatTable
import app.kreate.database.repositories.LyricsTable
import app.kreate.database.repositories.PlaylistTable
import app.kreate.database.repositories.QueuedMediaItemTable
import app.kreate.database.repositories.SearchQueryTable
import app.kreate.database.repositories.SongAlbumMapTable
import app.kreate.database.repositories.SongArtistMapTable
import app.kreate.database.repositories.SongPlaylistMapTable
import app.kreate.database.repositories.SongTable
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

object Database : KoinComponent {

    val songTable: SongTable get() = get()
    val albumTable: AlbumTable get() = get()
    val artistTable: ArtistTable get() = get()
    val eventTable: EventTable get() = get()
    val formatTable: FormatTable get() = get()
    val lyricsTable: LyricsTable get() = get()
    val playlistTable: PlaylistTable get() = get()
    val queueTable: QueuedMediaItemTable get() = get()
    val searchTable: SearchQueryTable get() = get()
    val songAlbumMapTable: SongAlbumMapTable get() = get()
    val songArtistMapTable: SongArtistMapTable get() = get()
    val songPlaylistMapTable: SongPlaylistMapTable get() = get()

    /**
     * Commit statements in BULK. If anything goes wrong during the transaction,
     * other statements will be canceled and reversed to preserve database's integrity.
     * [Read more](https://sqlite.org/lang_transaction.html)
     *
     * [asyncTransaction] runs all statements on non-blocking
     * thread to prevent UI from going unresponsive.
     *
     * ## Best use cases:
     * - Commit multiple write statements that require data integrity
     * - Processes that take longer time to complete
     *
     * > Do NOT use this to retrieve data from the database.
     * > Use [asyncQuery] to retrieve records.
     *
     * @param block of statements to write to database
     */
    fun asyncTransaction( block: Database.() -> Unit ) {
        get<CoroutineScope>().launch( Dispatchers.IO) {
            get<RoomDatabase>().useWriterConnection { this@Database.block() }
        }
    }

    /**
     * Access and retrieve records from database.
     *
     * [asyncQuery] runs all statements asynchronously to
     * prevent blocking UI thread from going unresponsive.
     *
     * ## Best use cases:
     * - Background data retrieval
     * - Non-immediate UI component update (i.e. count number of songs)
     *
     * > Do NOT use this method to write data to database
     * > because it offers no fail-safe during write.
     * > Use [asyncTransaction] to modify database.
     *
     * @param block of statements to retrieve data from database
     */
    fun asyncQuery( block: Database.() -> Unit ) {
        get<CoroutineScope>().launch( Dispatchers.IO ) {
            get<RoomDatabase>().useReaderConnection { this@Database.block() }
        }
    }

    /**
     * Forces SQLite to flush all pending changes from the Write-Ahead Log (.wal file)
     * back into the primary database file, optimizing disk space.
     */
    suspend fun checkpoint() = get<RoomDatabase>().useWriterConnection { connection ->
        connection.usePrepared( "PRAGMA wal_checkpoint(FULL)" ) { statement ->
            if( statement.step() ) {
                val isBusy = statement.getLong(0) // 0 if not busy, 1 if busy
                val logFrames = statement.getLong(1)
                val checkpointedFrames = statement.getLong(2)

                Logger.d( tag = "Database" ) {
                    "Checkpoint performed! (is busy: $isBusy, logged frames: $logFrames, checkpointed frames: $checkpointedFrames)"
                }
            }
        }
    }

    /**
     * Closes the database.
     *
     * Once a [RoomDatabase] is closed it should no longer be used.
     */
    fun close() = get<RoomDatabase>().close()
}