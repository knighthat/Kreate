package it.fast4x.rimusic.utils

import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.database.models.Album
import app.kreate.database.models.Artist
import app.kreate.database.models.Playlist
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic
import it.fast4x.innertube.utils.completed
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.isAutoSyncEnabled
import it.fast4x.rimusic.ui.components.tab.toolbar.Descriptive
import it.fast4x.rimusic.ui.components.tab.toolbar.DynamicColor
import it.fast4x.rimusic.ui.components.tab.toolbar.MenuIcon
import it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.knighthat.utils.Toaster

@OptIn(UnstableApi::class)
fun ytmPrivatePlaylistSync(playlist: Playlist, playlistId: Long) {
    playlist.let { plist ->
        Database.asyncTransaction {
            runBlocking(Dispatchers.IO) {
                withContext(Dispatchers.IO) {
                    plist.browseId?.let {
                        YtMusic.getPlaylist(
                            playlistId = it
                        ).completed()
                    }
                }
            }?.getOrNull()?.let { remotePlaylist ->
                CoroutineScope(Dispatchers.IO).launch {
                    withContext(Dispatchers.IO) {

                        println("ytmPrivatePlaylistSync Remote playlist editable: ${remotePlaylist.isEditable}")

                        // Update here playlist isEditable flag because library contain playlists but isEditable isn't always available
                        if (remotePlaylist.isEditable == true)
                            Database.playlistTable
                                    .update( playlist.copy(isEditable = true) )

                        remotePlaylist.songs
                                      .map( Innertube.SongItem::asMediaItem )
                                      .let {
                                          mapIgnore( playlist, *it.toTypedArray() )
                                      }
                    }
                }
            }
        }
    }
}

suspend fun importYTMSubscribedChannels(): Boolean {
    println("importYTMSubscribedChannels isYouTubeSyncEnabled() = ${isYouTubeSyncEnabled()} and isAutoSyncEnabled() = ${isAutoSyncEnabled()}")
    if (isYouTubeSyncEnabled()) {

        Toaster.n( R.string.syncing, Toast.LENGTH_LONG )

        Innertube.library("FEmusic_library_corpus_artists").completed().onSuccess { page ->

            val ytmArtists = page.items.filterIsInstance<Innertube.ArtistItem>()

            println("YTM artists: $ytmArtists")

            ytmArtists.forEach { remoteArtist ->
                var localArtist = Database.artistTable.findById( remoteArtist.key ).first()
                println("Local artist: $localArtist")
                println("Remote artist: $remoteArtist")

                if (localArtist == null) {
                    localArtist = Artist(
                        id = remoteArtist.key,
                        name = remoteArtist.title,
                        thumbnailUrl = remoteArtist.thumbnail?.url,
                        bookmarkedAt = System.currentTimeMillis(),
                        isYoutubeArtist = true
                    )
                    Database.artistTable.upsert( localArtist )
                } else {
                    localArtist.copy(
                        bookmarkedAt = localArtist.bookmarkedAt ?: System.currentTimeMillis(),
                        thumbnailUrl = remoteArtist.thumbnail?.url,
                        isYoutubeArtist = true
                    ).let( Database.artistTable::update )
                }
            }

            Database.artistTable
                    .allFollowing()
                    .first()
                    .filter { artist ->
                        artist.isYoutubeArtist && artist.id !in ytmArtists.map { it.key }
                    }
                    .map { it.copy( isYoutubeArtist = false, bookmarkedAt = null ) }
                    .forEach( Database.artistTable::update )
        }
            .onFailure {
                println("Error importing YTM subscribed artists channels: ${it.stackTraceToString()}")
                return false
            }
        return true
    } else
        return false
}

suspend fun importYTMLikedAlbums(): Boolean {
    println("importYTMLikedAlbums isYouTubeSyncEnabled() = ${isYouTubeSyncEnabled()} and isAutoSyncEnabled() = ${isAutoSyncEnabled()}")
    if (isYouTubeSyncEnabled()) {

        Toaster.n( R.string.syncing, Toast.LENGTH_LONG )

        Innertube.library("FEmusic_liked_albums").completed().onSuccess { page ->

            val ytmAlbums = page.items.filterIsInstance<Innertube.AlbumItem>()

            println("YTM albums: $ytmAlbums")

            ytmAlbums.forEach { remoteAlbum ->
                var localAlbum = Database.albumTable.findById( remoteAlbum.key ).first()
                println("Local album: $localAlbum")
                println("Remote album: $remoteAlbum")

                if (localAlbum == null) {
                    localAlbum = Album(
                        id = remoteAlbum.key,
                        title = remoteAlbum.title,
                        thumbnailUrl = remoteAlbum.thumbnail?.url,
                        bookmarkedAt = System.currentTimeMillis(),
                        year = remoteAlbum.year,
                        authorsText = remoteAlbum.authors?.getOrNull(1)?.name,
                        isYoutubeAlbum = true
                    )
                    Database.albumTable.upsert( localAlbum )
                } else {
                    localAlbum.copy(
                        isYoutubeAlbum = true,
                        bookmarkedAt = localAlbum.bookmarkedAt ?: System.currentTimeMillis(),
                        thumbnailUrl = remoteAlbum.thumbnail?.url)
                        .let( Database.albumTable::updateReplace )
                }
            }

            Database.albumTable
                    .all()
                    .first()
                    .filter { album ->
                        album.isYoutubeAlbum && album.id !in ytmAlbums.map { it.key }
                    }
                    .map { it.copy( isYoutubeAlbum = false, bookmarkedAt = null ) }
                    .also( Database.albumTable::updateReplace )
        }
            .onFailure {
                println("Error importing YTM liked albums: ${it.stackTraceToString()}")
                return false
            }
        return true
    } else
        return false
}

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


@Composable
fun autoSyncToolbutton(messageId: Int): MenuIcon = object : MenuIcon, DynamicColor, Descriptive {

    override var isFirstColor: Boolean by Preferences.AUTO_SYNC
    override val iconId: Int = R.drawable.sync
    override val messageId: Int = messageId
    override val menuIconTitle: String
        @Composable
        get() = stringResource(messageId)

    override fun onShortClick() {
        isFirstColor = !isFirstColor
    }
}

