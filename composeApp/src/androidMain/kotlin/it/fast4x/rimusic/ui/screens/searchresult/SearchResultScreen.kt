package it.fast4x.rimusic.ui.screens.searchresult
// test
import android.annotation.SuppressLint
import android.net.Uri
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.R
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.bodies.ContinuationBody
import it.fast4x.innertube.models.bodies.SearchBody
import it.fast4x.innertube.requests.searchPage
import it.fast4x.innertube.utils.from
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.Skeleton
import it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import it.fast4x.rimusic.ui.components.themed.NonQueuedMediaItemMenu
import it.fast4x.rimusic.ui.components.themed.Title
import it.fast4x.rimusic.ui.items.AlbumItem
import it.fast4x.rimusic.ui.items.AlbumItemPlaceholder
import it.fast4x.rimusic.ui.items.ArtistItem
import it.fast4x.rimusic.ui.items.ArtistItemPlaceholder
import it.fast4x.rimusic.ui.items.PlaylistItem
import it.fast4x.rimusic.ui.items.PlaylistItemPlaceholder
import it.fast4x.rimusic.ui.items.SongItemPlaceholder
import it.fast4x.rimusic.ui.items.VideoItem
import it.fast4x.rimusic.ui.items.VideoItemPlaceholder
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.px
import it.fast4x.rimusic.utils.addNext
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.asSong
import it.fast4x.rimusic.utils.disableScrollingTextKey
import it.fast4x.rimusic.utils.enqueue
import it.fast4x.rimusic.utils.forcePlay
import it.fast4x.rimusic.utils.isDownloadedSong
import it.fast4x.rimusic.utils.manageDownload
import it.fast4x.rimusic.utils.parentalControlEnabledKey
import it.fast4x.rimusic.utils.playVideo
import it.fast4x.rimusic.utils.preferences
import it.fast4x.rimusic.utils.rememberPreference
import it.fast4x.rimusic.utils.searchResultScreenTabIndexKey
import it.fast4x.rimusic.utils.showButtonPlayerVideoKey
import me.knighthat.component.SongItem
import me.knighthat.utils.Toaster
import androidx.compose.foundation.text.BasicText
import it.fast4x.rimusic.typography
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import it.fast4x.rimusic.colorPalette

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
    val binder = LocalPlayerServiceBinder.current
    val saveableStateHolder = rememberSaveableStateHolder()
    val (tabIndex, onTabIndexChanges) = rememberPreference(searchResultScreenTabIndexKey, 0)

    val hapticFeedback = LocalHapticFeedback.current

    val isVideoEnabled = LocalContext.current.preferences.getBoolean(showButtonPlayerVideoKey, false)
    val parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)

    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    val headerContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit = {
        Title(
            title = stringResource(R.string.search_results_for),
            verticalPadding = 4.dp
        )
        Title(
            title = query,
            icon = R.drawable.pencil,
            onClick = {
                navController.navigate("${NavRoutes.search.name}?text=${Uri.encode(query)}")
            },
            verticalPadding = 4.dp
        )
        Spacer(modifier = Modifier.height(24.dp))
    }

    val emptyItemsText = stringResource(R.string.no_results_found)

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
                                SongItem(
                                    song = song.asSong,
                                    navController = navController,
                                    onClick = {
                                        binder?.startRadio( song.asMediaItem, false, song.info?.endpoint )
                                    }
                                )
                            }
                        },
                        itemPlaceholderContent = { SongItemPlaceholder() }
                    )
                }

                1 -> {
                    val thumbnailSizeDp = Dimensions.thumbnails.album + 8.dp
                    val thumbnailSizePx = thumbnailSizeDp.px
                    var useGrid by rememberSaveable { mutableStateOf(true) }

                    val albumItemContentGrid: @Composable androidx.compose.foundation.lazy.grid.LazyGridItemScope.(Innertube.AlbumItem) -> Unit = { album ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            AlbumItem(
                                yearCentered = false,
                                album = album,
                                thumbnailSizePx = thumbnailSizePx,
                                thumbnailSizeDp = thumbnailSizeDp,
                                modifier = Modifier
                                    .combinedClickable(
                                        onClick = {
                                            navController.navigate("${NavRoutes.album.name}/${album.key}")
                                        },
                                        onLongClick = {}
                                    ),
                                disableScrollingText = disableScrollingText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            BasicText(
                                text = album.title ?: "",
                                style = typography().s.copy(textAlign = TextAlign.Center),
                                maxLines = 2
                            )
                            album.year?.let { year ->
                                if (year.isNotBlank()) {
                                    BasicText(
                                        text = year,
                                        style = typography().xs.copy(color = Color.Gray, textAlign = TextAlign.Center)
                                    )
                                }
                            }
                        }
                    }

                    val albumItemContentList: @Composable androidx.compose.foundation.lazy.LazyItemScope.(Innertube.AlbumItem) -> Unit = { album ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            AlbumItem(
                                yearCentered = false,
                                album = album,
                                thumbnailSizePx = thumbnailSizePx,
                                thumbnailSizeDp = thumbnailSizeDp,
                                modifier = Modifier
                                    .combinedClickable(
                                        onClick = {
                                            navController.navigate("${NavRoutes.album.name}/${album.key}")
                                        },
                                        onLongClick = {}
                                    ),
                                disableScrollingText = disableScrollingText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            BasicText(
                                text = album.title ?: "",
                                style = typography().s.copy(textAlign = TextAlign.Center),
                                maxLines = 2
                            )
                            album.year?.let { year ->
                                if (year.isNotBlank()) {
                                    BasicText(
                                        text = year,
                                        style = typography().xs.copy(color = Color.Gray, textAlign = TextAlign.Center)
                                    )
                                }
                            }
                        }
                    }

                    val albumItemPlaceholder: @Composable () -> Unit = { AlbumItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp) }

                    Column {
                        Title(
                            title = stringResource(R.string.search_results_for),
                            verticalPadding = 4.dp
                        )
                        Title(
                            title = query,
                            icon = R.drawable.pencil,
                            onClick = {
                                navController.navigate("${NavRoutes.search.name}?text=${Uri.encode(query)}")
                            },
                            verticalPadding = 4.dp,
                            trailingIcon = {
                                IconButton(onClick = { useGrid = !useGrid }) {
                                    Icon(
                                        painter = painterResource(id = if (useGrid) R.drawable.sort_vertical else R.drawable.sort_grid),
                                        contentDescription = "Switch Mode",
                                        tint = colorPalette().text
                                    )
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (useGrid) {
                            ItemsGridPage(
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
                                headerContent = {},
                                itemContent = albumItemContentGrid,
                                itemPlaceholderContent = albumItemPlaceholder,
                                thumbnailSizeDp = thumbnailSizeDp
                            )
                        } else {
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
                                headerContent = {},
                                itemContent = albumItemContentList,
                                itemPlaceholderContent = albumItemPlaceholder
                            )
                        }
                    }
                }

                2 -> {
                    val thumbnailSizeDp = 64.dp
                    val thumbnailSizePx = thumbnailSizeDp.px

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
                            ArtistItem(
                                artist = artist,
                                thumbnailSizePx = thumbnailSizePx,
                                thumbnailSizeDp = thumbnailSizeDp,
                                modifier = Modifier
                                    .clickable(onClick = {
                                        navController.navigate("${NavRoutes.artist.name}/${artist.key}")
                                    }),
                                disableScrollingText = disableScrollingText
                            )
                        },
                        itemPlaceholderContent = {
                            ArtistItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp)
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
                                VideoItem(
                                    video = video,
                                    thumbnailWidthDp = thumbnailWidthDp,
                                    thumbnailHeightDp = thumbnailHeightDp,
                                    modifier = Modifier
                                        .combinedClickable(
                                            onLongClick = {
                                                menuState.display {
                                                    NonQueuedMediaItemMenu(
                                                        navController = navController,
                                                        mediaItem = video.asMediaItem,
                                                        onDismiss = menuState::hide,
                                                        disableScrollingText = disableScrollingText
                                                    )
                                                }
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
                                        ),
                                    disableScrollingText = disableScrollingText
                                )
                            }
                        },
                        itemPlaceholderContent = {
                            VideoItemPlaceholder(
                                thumbnailHeightDp = thumbnailHeightDp,
                                thumbnailWidthDp = thumbnailWidthDp
                            )
                        }
                    )
                }

                4 -> {
                    val thumbnailSizeDp = Dimensions.thumbnails.playlist + 8.dp
                    val thumbnailSizePx = thumbnailSizeDp.px
                    var useGrid by rememberSaveable { mutableStateOf(true) }

                    val playlistItemContentGrid: @Composable androidx.compose.foundation.lazy.grid.LazyGridItemScope.(Innertube.PlaylistItem) -> Unit = { playlist ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            PlaylistItem(
                                playlist = playlist,
                                thumbnailSizePx = thumbnailSizePx,
                                thumbnailSizeDp = thumbnailSizeDp,
                                showSongsCount = false,
                                modifier = Modifier.clickable {
                                    navController.navigate("${NavRoutes.playlist.name}/${playlist.key}")
                                },
                                disableScrollingText = disableScrollingText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            BasicText(
                                text = playlist.title ?: "",
                                style = typography().s.copy(textAlign = TextAlign.Center),
                                maxLines = 2
                            )
                        }
                    }

                    val playlistItemContentList: @Composable androidx.compose.foundation.lazy.LazyItemScope.(Innertube.PlaylistItem) -> Unit = { playlist ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            PlaylistItem(
                                playlist = playlist,
                                thumbnailSizePx = thumbnailSizePx,
                                thumbnailSizeDp = thumbnailSizeDp,
                                showSongsCount = false,
                                modifier = Modifier.clickable {
                                    navController.navigate("${NavRoutes.playlist.name}/${playlist.key}")
                                },
                                disableScrollingText = disableScrollingText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            BasicText(
                                text = playlist.title ?: "",
                                style = typography().s.copy(textAlign = TextAlign.Center),
                                maxLines = 2
                            )
                        }
                    }

                    val playlistItemPlaceholder: @Composable () -> Unit = { PlaylistItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp) }

                    Column {
                        Title(
                            title = stringResource(R.string.search_results_for),
                            verticalPadding = 4.dp
                        )
                        Title(
                            title = query,
                            icon = R.drawable.pencil,
                            onClick = {
                                navController.navigate("${NavRoutes.search.name}?text=${Uri.encode(query)}")
                            },
                            verticalPadding = 4.dp,
                            trailingIcon = {
                                IconButton(onClick = { useGrid = !useGrid }) {
                                    Icon(
                                        painter = painterResource(id = if (useGrid) R.drawable.sort_vertical else R.drawable.sort_grid),
                                        contentDescription = "Switch Mode",
                                        tint = colorPalette().text
                                    )
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (useGrid) {
                            ItemsGridPage(
                                tag = "searchResults/$query/playlists",
                                itemsPageProvider = { continuation ->
                                    if (continuation == null) {
                                        Innertube.searchPage(
                                            body = SearchBody(query = query, params = Innertube.SearchFilter.CommunityPlaylist.value),
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
                                headerContent = {},
                                itemContent = playlistItemContentGrid,
                                itemPlaceholderContent = playlistItemPlaceholder,
                                thumbnailSizeDp = thumbnailSizeDp
                            )
                        } else {
                            ItemsPage(
                                tag = "searchResults/$query/playlists",
                                itemsPageProvider = { continuation ->
                                    if (continuation == null) {
                                        Innertube.searchPage(
                                            body = SearchBody(query = query, params = Innertube.SearchFilter.CommunityPlaylist.value),
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
                                headerContent = {},
                                itemContent = playlistItemContentList,
                                itemPlaceholderContent = playlistItemPlaceholder
                            )
                        }
                    }
                }

                5 -> {
                    val thumbnailSizeDp = Dimensions.thumbnails.playlist + 8.dp
                    val thumbnailSizePx = thumbnailSizeDp.px
                    var useGrid by rememberSaveable { mutableStateOf(true) }

                    val playlistItemContentGrid: @Composable androidx.compose.foundation.lazy.grid.LazyGridItemScope.(Innertube.PlaylistItem) -> Unit = { playlist ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            PlaylistItem(
                                playlist = playlist,
                                thumbnailSizePx = thumbnailSizePx,
                                thumbnailSizeDp = thumbnailSizeDp,
                                showSongsCount = false,
                                modifier = Modifier.clickable {
                                    navController.navigate("${NavRoutes.playlist.name}/${playlist.key}")
                                },
                                disableScrollingText = disableScrollingText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            BasicText(
                                text = playlist.title ?: "",
                                style = typography().s.copy(textAlign = TextAlign.Center),
                                maxLines = 2
                            )
                        }
                    }

                    val playlistItemContentList: @Composable androidx.compose.foundation.lazy.LazyItemScope.(Innertube.PlaylistItem) -> Unit = { playlist ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            PlaylistItem(
                                playlist = playlist,
                                thumbnailSizePx = thumbnailSizePx,
                                thumbnailSizeDp = thumbnailSizeDp,
                                showSongsCount = false,
                                modifier = Modifier.clickable {
                                    navController.navigate("${NavRoutes.playlist.name}/${playlist.key}")
                                },
                                disableScrollingText = disableScrollingText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            BasicText(
                                text = playlist.title ?: "",
                                style = typography().s.copy(textAlign = TextAlign.Center),
                                maxLines = 2
                            )
                        }
                    }

                    val playlistItemPlaceholder: @Composable () -> Unit = { PlaylistItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp) }

                    Column {
                        Title(
                            title = stringResource(R.string.search_results_for),
                            verticalPadding = 4.dp
                        )
                        Title(
                            title = query,
                            icon = R.drawable.pencil,
                            onClick = {
                                navController.navigate("${NavRoutes.search.name}?text=${Uri.encode(query)}")
                            },
                            verticalPadding = 4.dp,
                            trailingIcon = {
                                IconButton(onClick = { useGrid = !useGrid }) {
                                    Icon(
                                        painter = painterResource(id = if (useGrid) R.drawable.sort_vertical else R.drawable.sort_grid),
                                        contentDescription = "Switch Mode",
                                        tint = colorPalette().text
                                    )
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (useGrid) {
                            ItemsGridPage(
                                tag = "searchResults/$query/featured",
                                itemsPageProvider = { continuation ->
                                    if (continuation == null) {
                                        Innertube.searchPage(
                                            body = SearchBody(query = query, params = Innertube.SearchFilter.FeaturedPlaylist.value),
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
                                headerContent = {},
                                itemContent = playlistItemContentGrid,
                                itemPlaceholderContent = playlistItemPlaceholder,
                                thumbnailSizeDp = thumbnailSizeDp
                            )
                        } else {
                            ItemsPage(
                                tag = "searchResults/$query/featured",
                                itemsPageProvider = { continuation ->
                                    if (continuation == null) {
                                        Innertube.searchPage(
                                            body = SearchBody(query = query, params = Innertube.SearchFilter.FeaturedPlaylist.value),
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
                                headerContent = {},
                                itemContent = playlistItemContentList,
                                itemPlaceholderContent = playlistItemPlaceholder
                            )
                        }
                    }
                }

                6 -> {
                    val thumbnailSizeDp = Dimensions.thumbnails.playlist + 8.dp
                    val thumbnailSizePx = thumbnailSizeDp.px
                    var useGrid by rememberSaveable { mutableStateOf(true) }

                    val playlistItemContentGrid: @Composable androidx.compose.foundation.lazy.grid.LazyGridItemScope.(Innertube.PlaylistItem) -> Unit = { playlist ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            PlaylistItem(
                                playlist = playlist,
                                thumbnailSizePx = thumbnailSizePx,
                                thumbnailSizeDp = thumbnailSizeDp,
                                showSongsCount = false,
                                modifier = Modifier.clickable {
                                    navController.navigate("${NavRoutes.podcast.name}/${playlist.key}")
                                },
                                disableScrollingText = disableScrollingText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            BasicText(
                                text = playlist.title ?: "",
                                style = typography().s.copy(textAlign = TextAlign.Center),
                                maxLines = 2
                            )
                        }
                    }

                    val playlistItemContentList: @Composable androidx.compose.foundation.lazy.LazyItemScope.(Innertube.PlaylistItem) -> Unit = { playlist ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            PlaylistItem(
                                playlist = playlist,
                                thumbnailSizePx = thumbnailSizePx,
                                thumbnailSizeDp = thumbnailSizeDp,
                                showSongsCount = false,
                                modifier = Modifier.clickable {
                                    navController.navigate("${NavRoutes.podcast.name}/${playlist.key}")
                                },
                                disableScrollingText = disableScrollingText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            BasicText(
                                text = playlist.title ?: "",
                                style = typography().s.copy(textAlign = TextAlign.Center),
                                maxLines = 2
                            )
                        }
                    }

                    val playlistItemPlaceholder: @Composable () -> Unit = { PlaylistItemPlaceholder(thumbnailSizeDp = thumbnailSizeDp) }

                    Column {
                        Title(
                            title = stringResource(R.string.search_results_for),
                            verticalPadding = 4.dp
                        )
                        Title(
                            title = query,
                            icon = R.drawable.pencil,
                            onClick = {
                                navController.navigate("${NavRoutes.search.name}?text=${Uri.encode(query)}")
                            },
                            verticalPadding = 4.dp,
                            trailingIcon = {
                                IconButton(onClick = { useGrid = !useGrid }) {
                                    Icon(
                                        painter = painterResource(id = if (useGrid) R.drawable.sort_vertical else R.drawable.sort_grid),
                                        contentDescription = "Switch Mode",
                                        tint = colorPalette().text
                                    )
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        if (useGrid) {
                            ItemsGridPage(
                                tag = "searchResults/$query/podcasts",
                                itemsPageProvider = { continuation ->
                                    if (continuation == null) {
                                        Innertube.searchPage(
                                            body = SearchBody(query = query, params = Innertube.SearchFilter.Podcast.value),
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
                                headerContent = {},
                                itemContent = playlistItemContentGrid,
                                itemPlaceholderContent = playlistItemPlaceholder,
                                thumbnailSizeDp = thumbnailSizeDp
                            )
                        } else {
                            ItemsPage(
                                tag = "searchResults/$query/podcasts",
                                itemsPageProvider = { continuation ->
                                    if (continuation == null) {
                                        Innertube.searchPage(
                                            body = SearchBody(query = query, params = Innertube.SearchFilter.Podcast.value),
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
                                headerContent = {},
                                itemContent = playlistItemContentList,
                                itemPlaceholderContent = playlistItemPlaceholder
                            )
                        }
                    }
                }
            }
        }
    }
}
