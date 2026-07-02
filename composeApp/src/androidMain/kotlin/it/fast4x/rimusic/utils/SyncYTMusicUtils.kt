package it.fast4x.rimusic.utils

import it.fast4x.innertube.YtMusic
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import kotlinx.coroutines.flow.first


suspend fun removeYTSongFromPlaylist(
    songId: String,
    playlistBrowseId: String,
    playlistId: Long,
): Boolean {
    println("removeYTSongFromPlaylist removeSongFromPlaylist params songId = $songId, playlistBrowseId = $playlistBrowseId, playlistId = $playlistId")

    if ( isYouTubeSyncEnabled() )  {
        val setVideoId: String = Database.songPlaylistMapTable
                                         .findById( songId, playlistId )
                                         .first()
                                         ?.setVideoId ?: return false

        println("removeYTSongFromPlaylist removeSongFromPlaylist songSetVideoId = $setVideoId")

        YtMusic.removeFromPlaylist( playlistBrowseId, songId, setVideoId )
    }

    return isYouTubeSyncEnabled()
}
