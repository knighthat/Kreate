package me.knighthat.sync

import android.content.Context
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import app.kreate.database.Database
import app.kreate.database.insertIgnore

/**
 * Handles YouTube syncing
 */
object YouTubeSync {

    /**
     * Handles toggling like state of a song both locally and remotely.
     *
     * ***Toggle*** only handles 2 out of 3 states of like state:
     * `like` and `neutral`.
     *
     * This function handles these:
     * - Toggle song like state inside database
     * - Download song when liked (if enabled in settings)
     * - Sync like state with YouTube (if applicable)
     *
     * This function must not be called on **main thread**
     */
    @UnstableApi
    suspend fun toggleSongLike( context: Context, mediaItem: MediaItem ) {
        assert( Looper.myLooper() != Looper.getMainLooper() ) {
            "Cannot run YouTubeSync.toggleSongLike on main thread"
        }

        // TODO: Encapsulate this block in a transaction
        // Always ensure song in database before proceed
        Database.insertIgnore( mediaItem )
        Database.songTable.toggleLike( mediaItem.mediaId )
    }
}