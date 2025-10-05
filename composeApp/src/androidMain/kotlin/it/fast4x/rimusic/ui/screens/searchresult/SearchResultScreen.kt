package it.fast4x.rimusic.ui.screens.searchresult

import android.annotation.SuppressLint
import android.net.Uri
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.rimusic.component.album.AlbumItem
import app.kreate.android.themed.rimusic.component.artist.ArtistItem
import app.kreate.android.themed.rimusic.component.playlist.PlaylistItem
import app.kreate.android.themed.rimusic.component.song.SongItem
import app.kreate.database.models.Album
import app.kreate.database.models.SongAlbumMap
import it.fast4x.compose.persist.persist
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.bodies.BrowseBody
import it.fast4x.innertube.models.bodies.ContinuationBody
import it.fast4x.innertube.models.bodies.SearchBody
import it.fast4x.innertube.requests.albumPage
import it.fast4x.innertube.requests.searchPage
import it.fast4x.innertube.utils.from
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.Skeleton
import it.fast4x.rimusic.ui.components.SwipeableAlbumItem
import it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import it.fast4x.rimusic.ui.components.themed.NonQueuedMediaItemMenu
import it.fast4x.rimusic.ui.components.themed.Title
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.ui.styling.px
import it.fast4x.rimusic.utils.DisposableListener
import it.fast4x.rimusic.utils.addNext
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.asSong
import it.fast4x.rimusic.utils.enqueue
import it.fast4x.rimusic.utils.forcePlay
import it.fast4x.rimusic.utils.isDownloadedSong
import it.fast4x.rimusic.utils.manageDownload
import it.fast4x.rimusic.utils.playVideo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.utils.Toaster

@OptIn(ExperimentalCoroutinesApi::class)
@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun SearchResultScreen(
    navController: NavController,
    miniPlayer: @Composable () -> Unit = {},
    query: String,
    onSearchAgain: () -> Unit
) {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current ?: return
    val (colorPalette, typography) = LocalAppearance.current
    val saveableStateHolder = rememberSaveableStateHolder()
    val (tabIndex, onTabIndexChanges) = Preferences.SEARCH_RESULTS_TAB_INDEX

    val hapticFeedback = LocalHapticFeedback.current

    val isVideoEnabled by Preferences.PLAYER_ACTION_TOGGLE_VIDEO
    val parentalControlEnabled by Preferences.PARENTAL_CONTROL

    val headerContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit = {
        Title(
            title = stringResource(R.string.search_results_for),
            verticalPadding = 4.dp
        )
        Title(
            title = query,
            icon = R.drawable.pencil,
            onClick = {
                navController.navigate( "${NavRoutes.search}?text=${Uri.encode( query )}")
            },
            verticalPadding = 4.dp
        )
        Spacer(modifier = Modifier.height(12.dp))
    }

    val emptyItemsText = stringResource(R.string.no_results_found)

    var currentlyPlaying by remember { mutableStateOf(binder.player.currentMediaItem?.mediaId) }
    binder.player.DisposableListener {
        object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int ) {
                currentlyPlaying = mediaItem?.mediaId
            }
        }
    }
    val songItemValues = remember( colorPalette, typography ) {
        SongItem.Values.from( colorPalette, typography )
    }

    Skeleton(
        navController,
        tabIndex,
        onTabIndexChanges,
        miniPlayer,
        navBarContent = { item ->
            item(0, stringResource(R.string.songs), R.drawable.musical_notes)
            item(1, stringResource(R.string.albums), R.drawable.album)
            item(2, stringResource(R.string.artists), R.drawable.artist)
            item(3, stringResource(R.string.videos), R.drawable.video)
            item(4, stringResource(R.string.playlists), R.drawable.playlist)
            item(5, stringResource(R.string.featured), R.drawable.featured_playlist)
            item(6, stringResource(R.string.podcasts), R.drawable.podcast)
        }
    ) { currentTabIndex ->
        saveableStateHolder.SaveableStateProvider(currentTabIndex) {
            when ( currentTabIndex ) {
                0 -> {
                    val localBinder = LocalPlayerServiceBinder.current

                    ItemsPage(
                        tag = "searchResults/$query/songs",
                        itemsPageProvider = { continuation ->
                            if (continuation == null) {
                                Innertube.searchPage(
                                    body = SearchBody(
                                        query = query,
                                        params = Innertube.SearchFilter.Song.value
                                    ),
                                    fromMusicShelfRendererContent = Innertube.SongItem.Companion::from
                                )
                            } else {
                                Innertube.searchPage(
                                    body = ContinuationBody(continuation = continuation),
                                    fromMusicShelfRendererContent = Innertube.SongItem.Companion::from
                                )
                            }
                        },
                        emptyItemsText = emptyItemsText,
                        headerContent = headerContent,
                        itemContent = { song ->
                            if (parentalControlEnabled && song.explicit)
                                return@ItemsPage

                            val isDownloaded =
                                isDownloadedSong(song.asMediaItem.mediaId)

                            SwipeablePlaylistItem(
                                mediaItem = song.asMediaItem,
                                onPlayNext = {
                                    localBinder?.player?.addNext(song.asMediaItem)
                                },
                                onDownload = {
                                    localBinder?.cache?.removeResource(song.asMediaItem.mediaId)
                                    Database.asyncTransaction {
                                        formatTable.updateContentLengthOf( song.key )
                                    }
                                    manageDownload(
                                        context = context,
                                        mediaItem = song.asMediaItem,
                                        downloadState = isDownloaded
                                    )
                                },
                                onEnqueue = {
                                    localBinder?.player?.enqueue(song.asMediaItem)
                                }
                            ) {
                                SongItem.Render(
                                    song = song.asSong,
                                    context = context,
                                    binder = binder,
                                    hapticFeedback = hapticFeedback,
                                    isPlaying = currentlyPlaying == song.key,
                                    values = songItemValues,
                                    navController = navController,
                                    onClick = {
                                        binder?.startRadio( song.asMediaItem, false, song.info?.endpoint )
                                    }
                                )
                            }
                        },
                        itemPlaceholderContent = { SongItem.Placeholder() }
                    )
                }

                1 -> {
                    val thumbnailSizeDp = 108.dp
                    val thumbnailSizePx = thumbnailSizeDp.px

                    ItemsPage(
                        tag = "searchResults/$query/albums",
                        itemsPageProvider = { continuation ->
                            if (continuation == null) {
                                Innertube.searchPage(
                                    body = SearchBody(
                                        query = query,
                                        params = Innertube.SearchFilter.Album.value
                                    ),
                                    fromMusicShelfRendererContent = Innertube.AlbumItem::from
                                )
                            } else {
                                Innertube.searchPage(
                                    body = ContinuationBody(continuation = continuation),
                                    fromMusicShelfRendererContent = Innertube.AlbumItem::from
                                )
                            }
                        },
                        emptyItemsText = emptyItemsText,
                        headerContent = headerContent,
                        itemContent = { album ->
                            var albumPage by persist<Innertube.PlaylistOrAlbumPage?>("album/${album.key}/albumPage")
                            SwipeableAlbumItem(
                                albumItem = album,
                                onPlayNext = {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        Database.albumTable
                                                .findById( album.key )
                                                .combine(snapshotFlow { currentTabIndex }) { album, tabIndex -> album to tabIndex }
                                                .collect {
                                                    if (albumPage == null)
                                                        withContext(Dispatchers.IO) {
                                                            Innertube.albumPage(
                                                                BrowseBody(
                                                                    browseId = album.key
                                                                )
                                                            )
                                                                ?.onSuccess { currentAlbumPage ->
                                                                    albumPage =
                                                                        currentAlbumPage

                                                                    println("mediaItem success home album songsPage ${currentAlbumPage.songsPage} description ${currentAlbumPage.description} year ${currentAlbumPage.year}")

                                                                    albumPage
                                                                        ?.songsPage
                                                                        ?.items
                                                                        ?.map(
                                                                            Innertube.SongItem::asMediaItem
                                                                        )
                                                                        ?.let { it1 ->
                                                                            withContext(Dispatchers.Main) {
                                                                                binder?.player?.addNext(
                                                                                    it1,
                                                                                    context
                                                                                )
                                                                            }
                                                                        }
                                                                    println("mediaItem success add in queue album songsPage ${albumPage
                                                                        ?.songsPage
                                                                        ?.items?.size}")

                                                                }
                                                                ?.onFailure {
                                                                    println("mediaItem error searchResultScreen album ${it.stackTraceToString()}")
                                                                }
                                                        }
                                                }
                                    }

                                },
                                onEnqueue = {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        Database.albumTable
                                                .findById( album.key )
                                                .combine(snapshotFlow { currentTabIndex }) { album, tabIndex -> album to tabIndex }
                                                .collect {
                                                    if (albumPage == null)
                                                        withContext(Dispatchers.IO) {
                                                            Innertube.albumPage(
                                                                BrowseBody(
                                                                    browseId = album.key
                                                                )
                                                            )
                                                                ?.onSuccess { currentAlbumPage ->
                                                                    albumPage =
                                                                        currentAlbumPage

                                                                    println("mediaItem success home album songsPage ${currentAlbumPage.songsPage} description ${currentAlbumPage.description} year ${currentAlbumPage.year}")

                                                                    albumPage
                                                                        ?.songsPage
                                                                        ?.items
                                                                        ?.map(
                                                                            Innertube.SongItem::asMediaItem
                                                                        )
                                                                        ?.let { it1 ->
                                                                            withContext(Dispatchers.Main) {
                                                                                binder?.player?.enqueue(
                                                                                    it1,
                                                                                    context
                                                                                )
                                                                            }
                                                                        }
                                                                    println("mediaItem success add in queue album songsPage ${albumPage
                                                                        ?.songsPage
                                                                        ?.items?.size}")

                                                                }
                                                                ?.onFailure {
                                                                    println("mediaItem error searchResultScreen album ${it.stackTraceToString()}")
                                                                }

                                                        }

                                                    //}
                                                }

                                    }

                                },
                                onBookmark = {
                                    CoroutineScope(Dispatchers.IO).launch {
                                        Database.albumTable
                                                .findById( album.key )
                                                .combine(snapshotFlow { currentTabIndex }) { album, tabIndex -> album to tabIndex }
                                                .collect {
                                                    if (albumPage == null)
                                                        withContext(Dispatchers.IO) {
                                                            Innertube.albumPage(
                                                                BrowseBody(
                                                                    browseId = album.key
                                                                )
                                                            )
                                                                ?.onSuccess { currentAlbumPage ->
                                                                    albumPage =
                                                                        currentAlbumPage

                                                                    println("mediaItem success home album songsPage ${currentAlbumPage.songsPage} description ${currentAlbumPage.description} year ${currentAlbumPage.year}")

                                                                    Database.albumTable.upsert(
                                                                        Album(
                                                                            id = album.key,
                                                                            title = currentAlbumPage.title,
                                                                            thumbnailUrl = currentAlbumPage.thumbnail?.url,
                                                                            year = currentAlbumPage.year,
                                                                            authorsText = currentAlbumPage.authors?.joinToString( "" ) { it.name ?: "" },
                                                                            shareUrl = currentAlbumPage.url,
                                                                            timestamp = System.currentTimeMillis(),
                                                                            bookmarkedAt = System.currentTimeMillis()
                                                                        )
                                                                    )

                                                                    currentAlbumPage.songsPage
                                                                                    ?.items
                                                                                    ?.map( Innertube.SongItem::asMediaItem )
                                                                                    ?.onEach( Database::insertIgnore )
                                                                                    ?.mapIndexed { position, mediaItem ->
                                                                                        SongAlbumMap(
                                                                                            songId = mediaItem.mediaId,
                                                                                            albumId = album.key,
                                                                                            position = position
                                                                                        )
                                                                                    }
                                                                                    ?.also( Database.songAlbumMapTable::upsert )
                                                                }
                                                                ?.onFailure {
                                                                    println("mediaItem error searchResultScreen album ${it.stackTraceToString()}")
                                                                }

                                                        }
                                                }
                                    }
                                }
                            ) {
                                val appearance = LocalAppearance.current
                                val albumItemValues = remember( appearance ) {
                                    AlbumItem.Values.from( appearance )
                                }

                                AlbumItem.Horizontal( album, thumbnailSizeDp, albumItemValues, navController )
                            }
                        },
                        itemPlaceholderContent = {
                            AlbumItem.VerticalPlaceholder( thumbnailSizeDp )
                        }
                    )
                }

                2 -> {
                    val thumbnailSizeDp = 64.dp
                    val thumbnailSizePx = thumbnailSizeDp.px

                    val artistItemValues = remember( colorPalette, typography ) {
                        ArtistItem.Values.from( colorPalette, typography )
                    }

                    ItemsPage(
                        tag = "searchResults/$query/artists",
                        itemsPageProvider = { continuation ->
                            if (continuation == null) {
                                Innertube.searchPage(
                                    body = SearchBody(
                                        query = query,
                                        params = Innertube.SearchFilter.Artist.value
                                    ),
                                    fromMusicShelfRendererContent = Innertube.ArtistItem::from
                                )
                            } else {
                                Innertube.searchPage(
                                    body = ContinuationBody(continuation = continuation),
                                    fromMusicShelfRendererContent = Innertube.ArtistItem::from
                                )
                            }
                        },
                        emptyItemsText = emptyItemsText,
                        headerContent = headerContent,
                        itemContent = { artist ->
                            ArtistItem.Render( artist, thumbnailSizeDp, artistItemValues, navController )
                        },
                        itemPlaceholderContent = {
                            ArtistItem.Placeholder( thumbnailSizeDp )
                        }
                    )
                }

                3 -> {
                    val localBinder = LocalPlayerServiceBinder.current
                    val menuState = LocalMenuState.current
                    val thumbnailHeightDp = 72.dp
                    val thumbnailWidthDp = 128.dp

                    ItemsPage(
                        tag = "searchResults/$query/videos",
                        itemsPageProvider = { continuation ->
                            if (continuation == null) {
                                Innertube.searchPage(
                                    body = SearchBody(
                                        query = query,
                                        params = Innertube.SearchFilter.Video.value
                                    ),
                                    fromMusicShelfRendererContent = Innertube.VideoItem::from
                                )
                            } else {
                                Innertube.searchPage(
                                    body = ContinuationBody(continuation = continuation),
                                    fromMusicShelfRendererContent = Innertube.VideoItem::from
                                )
                            }
                        },
                        emptyItemsText = emptyItemsText,
                        headerContent = headerContent,
                        itemContent = { video ->
                            SwipeablePlaylistItem(
                                mediaItem = video.asMediaItem,
                                onPlayNext = {
                                    localBinder?.player?.addNext(video.asMediaItem)
                                },
                                onDownload = {
                                    Toaster.w( R.string.downloading_videos_not_supported )
                                },
                                onEnqueue = {
                                    localBinder?.player?.enqueue(video.asMediaItem)
                                }
                            ) {
                                SongItem.Render(
                                    innertubeVideo = video,
                                    hapticFeedback = hapticFeedback,
                                    isPlaying = currentlyPlaying == video.key,
                                    values = songItemValues,
                                    thumbnailSizeDp = DpSize(thumbnailWidthDp, thumbnailHeightDp),
                                    onLongClick = {
                                        menuState.display {
                                            NonQueuedMediaItemMenu(
                                                navController = navController,
                                                mediaItem = video.asMediaItem,
                                                onDismiss = menuState::hide
                                            )
                                        };
                                        hapticFeedback.performHapticFeedback(
                                            HapticFeedbackType.LongPress
                                        )
                                    },
                                    onClick = {
                                        localBinder?.stopRadio()
                                        if (isVideoEnabled)
                                            localBinder?.player?.playVideo(video.asMediaItem)
                                        else
                                            localBinder?.player?.forcePlay(video.asMediaItem)
                                    }
                                )
                            }
                        },
                        itemPlaceholderContent = {
                            SongItem.Placeholder(
                                DpSize(thumbnailWidthDp, thumbnailHeightDp)
                            )
                        }
                    )
                }

                4, 5 -> {
                    val thumbnailSizeDp = Dimensions.thumbnails.playlist
                    val thumbnailSizePx = thumbnailSizeDp.px

                    val playlistItemValues = remember( colorPalette, typography ) {
                        PlaylistItem.Values.from( colorPalette, typography )
                    }

                    ItemsPage(
                        tag = "searchResults/$query/${
                            when (currentTabIndex) {
                                4 -> "playlists"
                                else -> "featured"
                            }
                        }",
                        itemsPageProvider = { continuation ->
                            if (continuation == null) {
                                val filter = when (currentTabIndex) {
                                    4 -> Innertube.SearchFilter.CommunityPlaylist
                                    else -> Innertube.SearchFilter.FeaturedPlaylist
                                }

                                Innertube.searchPage(
                                    body = SearchBody(query = query, params = filter.value),
                                    fromMusicShelfRendererContent = Innertube.PlaylistItem::from
                                )
                            } else {
                                Innertube.searchPage(
                                    body = ContinuationBody(continuation = continuation),
                                    fromMusicShelfRendererContent = Innertube.PlaylistItem::from
                                )
                            }
                        },
                        emptyItemsText = emptyItemsText,
                        headerContent = headerContent,
                        itemContent = { playlist ->
                            PlaylistItem.Horizontal(
                                innertubePlaylist = playlist,
                                heightDp = thumbnailSizeDp,
                                values = playlistItemValues,
                                navController = navController
                            )
                        },
                        itemPlaceholderContent = {
                            PlaylistItem.VerticalPlaceholder( thumbnailSizeDp )
                        }
                    )
                }

                6 -> {
                    val thumbnailSizeDp = Dimensions.thumbnails.playlist
                    val thumbnailSizePx = thumbnailSizeDp.px

                    val playlistItemValues = remember( colorPalette, typography ) {
                        PlaylistItem.Values.from( colorPalette, typography )
                    }

                    ItemsPage(
                        tag = "searchResults/$query/podcasts",
                        itemsPageProvider = { continuation ->
                            if (continuation == null) {
                                val filter = Innertube.SearchFilter.Podcast

                                Innertube.searchPage(
                                    body = SearchBody(query = query, params = filter.value),
                                    fromMusicShelfRendererContent = Innertube.PlaylistItem::from
                                )
                            } else {
                                Innertube.searchPage(
                                    body = ContinuationBody(continuation = continuation),
                                    fromMusicShelfRendererContent = Innertube.PlaylistItem::from
                                )
                            }
                        },
                        emptyItemsText = emptyItemsText,
                        headerContent = headerContent,
                        itemContent = { playlist ->
                            PlaylistItem.Vertical(
                                innertubePlaylist = playlist,
                                widthDp = thumbnailSizeDp,
                                values = playlistItemValues,
                                navController = null,
                                modifier = Modifier.clickable {
                                    NavRoutes.podcast.navigateHere( navController, playlist.key )
                                }
                            )
                        },
                        itemPlaceholderContent = {
                            PlaylistItem.VerticalPlaceholder( thumbnailSizeDp )
                        }
                    )
                }
            }
        }
    }
}
