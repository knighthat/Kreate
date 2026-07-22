package app.kreate.database.callbacks

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import app.kreate.database.models.Song
import app.kreate.database.repositories.SongTable
import app.kreate.player.download.MediaDownloader
import app.kreate.preferences.Preferences
import co.touchlab.kermit.Logger
import it.fast4x.rimusic.utils.asMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi


@OptIn(ExperimentalAtomicApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
class DownloadOnLikeCallback : RoomDatabase.Callback(), KoinComponent {

    companion object {

        private val EMPTY_SET = HashSet<String>(0)
    }

    private val logger = Logger.withTag( "DownloadOnLikeCallback" )
    private val scope: CoroutineScope by inject()
    private val table: SongTable by inject()
    private val mediaDownloader: MediaDownloader by inject()
    private val snapshot = AtomicReference<Set<String>>(EMPTY_SET)

    private suspend fun start() =
        table.observeLikeState()
             .collectLatest { newMap ->
                 logger.v { "Song like state changed" }

                 // Check if this is first emit. If it is, only assign snapshot
                 // and not download existing media items
                 if( snapshot.load() === EMPTY_SET ) {
                     logger.d { "Initial run, capturing first snapshot of ${newMap.size} songs" }

                     snapshot.store( newMap.keys )
                     return@collectLatest
                 }

                 val snapshotKeys = snapshot.exchange( newMap.keys )
                 val newKeys = newMap.keys
                 // Get all keys that only exist in 1 of the maps
                 val diff = (snapshotKeys - newKeys) + (newKeys - snapshotKeys)

                 logger.d { "New map has ${diff.size} different keys compared to previous snapshot" }

                 diff.filter { newMap[it] == -1L }
                     .forEach { id ->
                         logger.i { "Song $id state has been changed to disliked" }

                         val mediaItem = MediaItem.Builder().setMediaId( id ).build()
                         mediaDownloader.remove( mediaItem )
                     }

                 val downloads = mediaDownloader.downloads.value
                 // Get all keys that are not present in indexed downloads
                 // or if it's but the state isn't completed (successful)
                 diff.filter { it !in downloads.keys || downloads[it]?.state != Download.STATE_COMPLETED }
                     .also {
                         logger.d { "There are ${it.size} songs to be downloaded" }
                     }
                     // Convert Ids back to Song instances
                     .let( table::findByIds )
                     .map( Song::asMediaItem )
                     // Submit download requests
                     .forEach( mediaDownloader::download )
             }

    override fun onOpen( connection: SQLiteConnection ) {
        scope.launch {
            combine(Preferences.AUTO_DOWNLOAD, Preferences.AUTO_DOWNLOAD_ON_LIKE) { a, b -> a && b }
                .collectLatest {
                    if( !it ) {
                        logger.i { "Auto download on liked is set to off" }
                        return@collectLatest
                    } else {
                        logger.i { "Auto download is enabled, starting observation" }
                    }

                    // Start observation when enabled, else cancel it
                    start()
                }
        }
    }
}