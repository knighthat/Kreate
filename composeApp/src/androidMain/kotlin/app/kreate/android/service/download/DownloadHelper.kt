package app.kreate.android.service.download

import androidx.media3.common.MediaItem
import app.kreate.database.models.Song


interface DownloadHelper {

    /**
     * Attempt to download provided [mediaItem] by its ID.
     *
     * If media exists cache storage, then it'll be moved into
     * download storage to save bandwidth.
     */
    fun downloadMediaItem( mediaItem: MediaItem )

    /**
     * Like song then download (if enabled)
     */
    fun likeAndDownload( mediaItem: MediaItem )

    /**
     * Attempt to queue [mediaItems] for download. Number
     * of parallel downloads is defined in [android.app.DownloadManager],
     * which is controlled by [app.kreate.android.Preferences.DOWNLOAD_MAX_PARALLEL].
     *
     * This is a non-blocking operation, heavy and/or time-consuming
     * steps are handled by [kotlinx.coroutines.CoroutineScope] to
     * reduce stutter or freezing UI.
     */
    fun downloadMediaItems( mediaItems: List<MediaItem> )

    /**
     * Attempt to download provided [song] by its ID.
     *
     * If song exists cache storage, then it'll be moved into
     * download storage to save bandwidth.
     */
    fun downloadSong( song: Song )

    /**
     * Attempt to queue [songs] for download. Number
     * of parallel downloads is defined in [android.app.DownloadManager],
     * which is controlled by [app.kreate.android.Preferences.DOWNLOAD_MAX_PARALLEL].
     *
     * This is a non-blocking operation, heavy and/or time-consuming
     * steps are handled by worker thread(s) to reduce stutter or freezing UI.
     */
    fun downloadSongs( songs: List<Song> )

    /**
     * Remove songs by their IDs
     */
    fun remove( vararg ids: String )

    /**
     * Attempt to remove media by its ID.
     */
    fun removeMediaItem( mediaItem: MediaItem )

    /**
     * Attempt to remove list of medias.
     *
     * This is a non-blocking operation, heavy and/or time-consuming
     * steps are handled by worker thread(s) to reduce stutter or freezing UI.
     */
    fun removeMediaItems( mediaItems: List<MediaItem> )

    /**
     * Attempt to remove song by its ID.
     */
    fun removeSong( song: Song )

    /**
     * Attempt to remove list of songs.
     *
     * This is a non-blocking operation, heavy and/or time-consuming
     * steps are handled by worker thread(s) to reduce stutter or freezing UI.
     */
    fun removeSongs( songs: List<Song> )
}