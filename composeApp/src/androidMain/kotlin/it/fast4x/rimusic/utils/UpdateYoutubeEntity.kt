package it.fast4x.rimusic.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.media3.common.util.UnstableApi
import app.kreate.android.Preferences
import app.kreate.database.models.Album
import app.kreate.database.models.Artist
import app.kreate.database.models.SongAlbumMap
import it.fast4x.compose.persist.persist
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.bodies.BrowseBody
import it.fast4x.innertube.requests.albumPage
import it.fast4x.innertube.requests.artistPage
import it.fast4x.rimusic.Database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Composable
fun UpdateYoutubeArtist(browseId: String) {

    var artistPage by persist<Innertube.ArtistInfoPage?>("artist/$browseId/artistPage")
    var artist by persist<Artist?>("artist/$browseId/artist")
    val tabIndex by Preferences.ARTIST_SCREEN_TAB_INDEX

    LaunchedEffect(browseId) {
        Database.artistTable
                .findById( browseId )
                .combine(snapshotFlow { tabIndex }.map { it != 4 }) { artist, mustFetch -> artist to mustFetch }
                .distinctUntilChanged()
                .collect { (currentArtist, mustFetch) ->
                    artist = currentArtist

                    if (artistPage == null && (currentArtist?.timestamp == null || mustFetch)) {
                        withContext(Dispatchers.IO) {
                            Innertube.artistPage(BrowseBody(browseId = browseId))
                                ?.onSuccess { currentArtistPage ->
                                    artistPage = currentArtistPage

                                    Database.artistTable.upsert(
                                        Artist(
                                            id = browseId,
                                            name = currentArtistPage.name,
                                            thumbnailUrl = currentArtistPage.thumbnail?.url,
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
    var albumPage by persist<Innertube.PlaylistOrAlbumPage?>("album/$browseId/albumPage")
    val tabIndex by rememberSaveable {mutableStateOf(0)}
    LaunchedEffect(browseId) {
        Database.albumTable
                .findById( browseId )
                .combine(snapshotFlow { tabIndex }) { album, tabIndex -> album to tabIndex }
                .collect { (currentAlbum, tabIndex) ->
                    album = currentAlbum

                    if (albumPage == null && (currentAlbum?.timestamp == null || tabIndex == 1)) {
                        withContext(Dispatchers.IO) {
                            Innertube.albumPage(BrowseBody(browseId = browseId))
                                ?.onSuccess { currentAlbumPage ->
                                    albumPage = currentAlbumPage

                                    Database.songAlbumMapTable.clear( browseId )

                                    Database.albumTable.upsert(
                                        Album(
                                            id = browseId,
                                            title = currentAlbumPage.title,
                                            thumbnailUrl = currentAlbumPage.thumbnail?.url,
                                            year = currentAlbumPage.year,
                                            authorsText = currentAlbumPage.authors
                                                ?.joinToString("") { it.name ?: "" },
                                            shareUrl = currentAlbumPage.url,
                                            timestamp = System.currentTimeMillis(),
                                            bookmarkedAt = album?.bookmarkedAt
                                        )
                                    )
                                    currentAlbumPage.songsPage
                                                    ?.items
                                                    ?.map(Innertube.SongItem::asMediaItem)
                                                    ?.onEach( Database::insertIgnore )
                                                    ?.mapIndexed { position, mediaItem ->
                                                        SongAlbumMap(
                                                            songId = mediaItem.mediaId,
                                                            albumId = browseId,
                                                            position = position
                                                        )
                                                    }
                                                   ?.also( Database.songAlbumMapTable::upsert )
                                }
                        }

                    }
                }
    }
}