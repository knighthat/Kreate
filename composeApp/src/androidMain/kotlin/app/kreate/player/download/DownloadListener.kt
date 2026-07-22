package app.kreate.player.download

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import app.kreate.database.Database
import app.kreate.database.models.Lyrics
import app.kreate.database.models.Song
import app.kreate.database.repositories.SongTable
import app.kreate.di.THUMBNAIL_SIZE
import app.kreate.preferences.Preferences
import app.kreate.util.thumbnail
import app.kreate.util.toDuration
import co.touchlab.kermit.Logger
import coil3.imageLoader
import coil3.request.ImageRequest
import it.fast4x.kugou.KuGou
import it.fast4x.lrclib.LrcLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.component.inject


@OptIn(UnstableApi::class)
class DownloadListener : DownloadManager.Listener, KoinComponent {

    private val logger = Logger.withTag( "DownloadListener" )
    private val scope: CoroutineScope by inject()
    private val context: Context by inject()
    private val songTable: SongTable get() = get()

    private suspend fun preCacheThumbnail( thumbnailUrl: String ) {
        // Cache thumbnail by sending out a request. If thumbnail is already cached,
        // it'll be ignored, no data wasted.
        val thumbnailUrl = thumbnailUrl.thumbnail( THUMBNAIL_SIZE )
        val request = ImageRequest.Builder(context).data( thumbnailUrl ).build()
        // Fetches and decodes the image into the memory/disk cache without rendering it
        context.imageLoader.execute( request )
    }

    private suspend fun downloadLyrics( song: Song ) {
        if( !Preferences.AUTO_DOWNLOAD.value || !Preferences.AUTO_DOWNLOAD_LYRICS_ON_SONG_DOWNLOAD.value )
            return

        val storedLyrics = Database.lyricsTable.findBySongId( song.id ).first()
        if( storedLyrics?.synced != null ) return

        var fetchedLyrics: Lyrics? = null
        LrcLib.lyrics(
            artist = song.cleanArtistsText(),
            title = song.cleanTitle(),
            duration = song.durationText.toDuration()
        )?.onSuccess {
            fetchedLyrics = Lyrics(
                songId = song.id,
                fixed = storedLyrics?.fixed,
                synced = it?.text.orEmpty()
            )
        }?.onFailure {
            // Try out different source for lyrics
            KuGou.lyrics(
                artist = song.cleanArtistsText(),
                title = song.cleanTitle(),
                duration = song.durationText.toDuration().inWholeSeconds
            )?.onSuccess {
                fetchedLyrics = Lyrics(
                    songId = song.id,
                    fixed = storedLyrics?.fixed,
                    synced = it?.value.orEmpty()
                )
            }
        }

        if( fetchedLyrics != null )
            Database.asyncTransaction {
                lyricsTable.upsert( fetchedLyrics!! )
            }
    }

    override fun onDownloadChanged(
        downloadManager: DownloadManager,
        download: Download,
        finalException: Exception?
    ) {
        if( download.state != Download.STATE_COMPLETED ) return
        logger.v { "Download ${download.request.id} is successfully downloaded" }

        scope.launch( Dispatchers.IO ) {
            val song = songTable.findById( download.request.id ).firstOrNull()
            if( song == null ) {
                logger.w { "Song is downloaded but not inserted into database" }
                return@launch
            }

            if( song.thumbnailUrl != null ) {
                preCacheThumbnail( song.thumbnailUrl!! )
            } else {
                logger.w { "Song doesn't have thumbnail url" }
            }

            downloadLyrics( song )
        }
    }
}