package it.fast4x.rimusic.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.media3.common.util.UnstableApi
import app.kreate.android.utils.innertube.toMediaItem
import app.kreate.database.Database
import app.kreate.database.insertIgnore
import app.kreate.database.models.Album
import app.kreate.database.models.Artist
import app.kreate.database.models.SongAlbumMap
import app.kreate.gateway.innertube.YouTube
import app.kreate.gateway.innertube.models.InnertubeAlbum
import app.kreate.gateway.innertube.models.InnertubeArtist
import app.kreate.gateway.innertube.models.InnertubeSong
import app.kreate.preferences.Preferences
import co.touchlab.kermit.Logger
import it.fast4x.compose.persist.persist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.get

@Composable
fun UpdateYoutubeArtist(browseId: String) {
    var artistPage by persist<InnertubeArtist?>("artist/$browseId/artistPage")

    LaunchedEffect(browseId) {
        Database.artistTable
                .findById( browseId )
                .combine(Preferences.ARTIST_SCREEN_TAB_INDEX.map { it != 4 }) { artist, mustFetch -> artist to mustFetch }
                .distinctUntilChanged()
                .collect { (currentArtist, mustFetch) ->
                    if (artistPage == null && (currentArtist?.timestamp == null || mustFetch)) {
                        withContext(Dispatchers.IO) {
                            get<YouTube>(YouTube::class.java)
                                .getArtist( browseId, null )
                                .onFailure { err ->
                                    Logger.e( "Failed to update YouTube artist", err, "UpdateYoutubeArtist" )
                                }
                                .onSuccess { currentArtistPage ->
                                    artistPage = currentArtistPage

                                    Database.artistTable.upsert(
                                        Artist(
                                            id = browseId,
                                            name = currentArtistPage.name,
                                            thumbnailUrl = currentArtistPage.thumbnails.lastOrNull()?.url,
                                            timestamp = System.currentTimeMillis(),
                                            bookmarkedAt = currentArtist?.bookmarkedAt
                                        )
                                    )
                                }
                        }
                    }
                }
    }
}

@UnstableApi
@Composable
fun UpdateYoutubeAlbum (browseId: String) {
    var album by persist<Album?>("album/$browseId/album")
    var albumPage by persist<InnertubeAlbum?>("album/$browseId/albumPage")
    val tabIndex by rememberSaveable {mutableStateOf(0)}
    LaunchedEffect(browseId) {
        Database.albumTable
                .findById( browseId )
                .combine(snapshotFlow { tabIndex }) { album, tabIndex -> album to tabIndex }
                .collect { (currentAlbum, tabIndex) ->
                    album = currentAlbum

                    if (albumPage == null && (currentAlbum?.timestamp == null || tabIndex == 1)) {
                        withContext(Dispatchers.IO) {
                            get<YouTube>(YouTube::class.java)
                                .getAlbum( browseId, null )
                                .onFailure { err ->
                                    Logger.e( "Failed to update YouTube album", err, "UpdateYoutubeAlbum" )
                                }
                                .onSuccess { currentAlbumPage ->
                                    albumPage = currentAlbumPage

                                    Database.songAlbumMapTable.clear( browseId )

                                    Database.albumTable.upsert( currentAlbumPage.toAlbum )
                                    currentAlbumPage.songs
                                                    .map( InnertubeSong::toMediaItem )
                                                    .onEach( Database::insertIgnore )
                                                    .mapIndexed { position, mediaItem ->
                                                        SongAlbumMap(
                                                            songId = mediaItem.mediaId,
                                                            albumId = browseId,
                                                            position = position
                                                        )
                                                    }
                                                   .also( Database.songAlbumMapTable::upsert )
                                }
                        }

                    }
                }
    }
}