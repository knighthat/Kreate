package app.kreate.player.download

import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import kotlinx.coroutines.flow.StateFlow


/**
 * The public entry point for the rest of the app: hand it a [MediaItem], get a download.
 *
 * Inject it via Koin: `val downloader: MediaDownloader by inject()`.
 */
interface MediaDownloader {

    @get:UnstableApi
    val downloads: StateFlow<Map<String, Download>>

    fun download( mediaItem: MediaItem )

    fun remove( mediaItem: MediaItem )
}