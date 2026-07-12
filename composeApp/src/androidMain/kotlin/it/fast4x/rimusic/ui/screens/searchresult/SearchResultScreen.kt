package it.fast4x.rimusic.ui.screens.searchresult

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.kreate.android.LocalBottomMenu
import app.kreate.android.R
import app.kreate.android.constant.MenuPage
import app.kreate.android.service.player.StatefulPlayer
import app.kreate.android.themed.rimusic.component.album.AlbumItem
import app.kreate.android.themed.rimusic.component.artist.ArtistItem
import app.kreate.android.themed.rimusic.component.playlist.PlaylistItem
import app.kreate.android.themed.rimusic.component.song.SongItem
import app.kreate.android.utils.innertube.toMediaItem
import app.kreate.android.viewmodel.SearchResultViewModel
import app.kreate.gateway.innertube.SearchFilter
import app.kreate.gateway.innertube.models.InnertubeAlbum
import app.kreate.gateway.innertube.models.InnertubeArtist
import app.kreate.gateway.innertube.models.InnertubeItem
import app.kreate.gateway.innertube.models.InnertubePlaylist
import app.kreate.gateway.innertube.models.InnertubeSong
import app.kreate.preferences.Preferences
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.ui.components.Skeleton
import it.fast4x.rimusic.ui.components.themed.Title
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.forcePlay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.compose.viewmodel.koinViewModel
import org.koin.java.KoinJavaComponent.get


@Composable
fun SearchResultScreen(
    navController: NavController,
    miniPlayer: @Composable () -> Unit = {},
    query: String,
    viewModel: SearchResultViewModel = koinViewModel()
) {
    val (colorPalette, typography) = LocalAppearance.current
    val menu = LocalBottomMenu.current
    val tabIndex by Preferences.SEARCH_RESULTS_TAB_INDEX.collectAsStateWithLifecycle()
    val onTabIndexChanges: (Int) -> Unit = { index ->
        Preferences.SEARCH_RESULTS_TAB_INDEX.update( index )
    }
    val listState = rememberLazyListState()
    val songItemValues = remember( colorPalette, typography ) {
        SongItem.Values.from( colorPalette, typography )
    }

    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle( false )
    val isFetching by viewModel.isFetching.collectAsStateWithLifecycle()

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


        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = Dimensions.bottomSpacer)
        ) {
            stickyHeader {
                Column(
                    Modifier.fillMaxWidth()
                            .padding( bottom = 12.dp )
                            .background( colorPalette.background0 )
                ) {
                    Title(
                        title = stringResource( R.string.search_results_for ),
                        verticalPadding = 4.dp
                    )
                    Title(
                        title = query,
                        icon = R.drawable.pencil,
                        onClick = {
                            navController.navigate( "${NavRoutes.search}?text=${query.toUri()}")
                        },
                        verticalPadding = 4.dp
                    )
                }
            }

            items(
                items = searchResults,
                key = InnertubeItem::id
            ) { item ->
                // TODO: Split duration, add download icon to InnertubeSong.
                //   Reimplement swipe action mechanism
                ListItem(
                    headlineContent = {
                        SongItem.Title( item.name, songItemValues )
                    },
                    supportingContent = {
                        // Only render subtitle if there's something to render
                        val subtitle = item.subtitle?.joinToString( "" ) ?: return@ListItem
                        SongItem.Artists( subtitle, songItemValues )
                    },
                    leadingContent = {
                        val thumbnailUrl = item.thumbnails.lastOrNull()?.url
                        val contentScale = ContentScale.Crop
                        val sizeDp = SongItem.thumbnailSize()

                        when( currentTabIndex ) {
                            0, 3    -> SongItem.Thumbnail( thumbnailUrl, contentScale, sizeDp = sizeDp )
                            1       -> AlbumItem.Thumbnail( thumbnailUrl, contentScale, sizeDp = sizeDp )
                            2       -> ArtistItem.Thumbnail( thumbnailUrl, contentScale, sizeDp = sizeDp )
                            4, 5, 6 -> PlaylistItem.Thumbnail( thumbnailUrl, contentScale, sizeDp = sizeDp )
                        }
                    },
                    colors = ListItemDefaults.colors(
                        containerColor = Color.Transparent
                    ),
                    modifier = Modifier.combinedClickable(
                        role = Role.Button,
                        onClick = {
                            when( item ) {
                                is InnertubeSong -> {
                                    val player: StatefulPlayer = get(StatefulPlayer::class.java)
                                    player.forcePlay( item.toMediaItem )
                                }
                                is InnertubeAlbum -> NavRoutes.YT_ALBUM.navigateHere( navController, item.id )
                                is InnertubeArtist -> NavRoutes.YT_ARTIST.navigateHere( navController, item.id )
                                is InnertubePlaylist -> NavRoutes.YT_PLAYLIST.navigateHere( navController, item.id )
                            }
                        },
                        onLongClick = {
                            if( item !is InnertubeSong ) return@combinedClickable

                            val page = MenuPage.Song(item.toMediaItem)
                            menu.show( page, true )
                        }
                    )
                )
            }

            // Show placeholder if there's more
            if( hasMore && !isFetching )
                item( contentType = SearchResultViewModel.GetMore ) {
                    when( currentTabIndex ) {
                        0, 3    -> SongItem.Placeholder()
                        1       -> AlbumItem.VerticalPlaceholder()
                        2       -> ArtistItem.Placeholder()
                        4, 5, 6 -> PlaylistItem.VerticalPlaceholder()
                    }
                }
            // Show placeholders while getting results
            if( isFetching && searchResults.isEmpty() )
                items( count = 5 ) {
                    when( currentTabIndex ) {
                        0, 3    -> SongItem.Placeholder()
                        1       -> AlbumItem.VerticalPlaceholder()
                        2       -> ArtistItem.Placeholder()
                        4, 5, 6 -> PlaylistItem.VerticalPlaceholder()
                    }
                }
            // Show text if no results found
            if( !isFetching && searchResults.isEmpty() )
                item {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth()
                                           .padding(vertical = 16.dp)
                    ) {
                        Text(
                            text = stringResource( R.string.no_results_found ),
                            color = colorPalette.text
                        )
                    }
                }
        }
    }

    // Fetch new results when category changes
    LaunchedEffect( tabIndex ) {
        val filter = when( tabIndex ) {
            0       -> SearchFilter.SONGS
            1       -> SearchFilter.ALBUMS
            2       -> SearchFilter.ARTISTS
            3       -> SearchFilter.VIDEOS
            4       -> SearchFilter.COMMUNITY_PLAYLISTS
            5       -> SearchFilter.FEATURED_PLAYLISTS
            6       -> SearchFilter.PODCASTS
            else    -> return@LaunchedEffect
        }
        viewModel.onFilterChanged( query, filter )
    }
    // Check if [SearchResultViewModel.GetMore] is visible on screen. If it is, get more results
    LaunchedEffect( listState ) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .map { visibleItems ->
                visibleItems.any { it.contentType === SearchResultViewModel.GetMore }
            }
            .distinctUntilChanged()
            .collectLatest {
                if( it ) viewModel.onGetMore()
            }
    }
}
