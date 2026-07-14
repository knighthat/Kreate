package app.kreate.android.themed.common.screens

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.themed.rimusic.component.album.AlbumItem
import app.kreate.android.themed.rimusic.component.artist.ArtistItem
import app.kreate.android.themed.rimusic.component.playlist.PlaylistItem
import app.kreate.android.utils.ItemUtils
import app.kreate.android.viewmodel.SeeMorePageViewModel
import app.kreate.gateway.innertube.models.InnertubeAlbum
import app.kreate.gateway.innertube.models.InnertubeArtist
import app.kreate.gateway.innertube.models.InnertubePlaylist
import app.kreate.preferences.Preferences
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.ui.components.Skeleton
import it.fast4x.rimusic.ui.components.themed.Title
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import org.koin.compose.viewmodel.koinViewModel


@OptIn(UnstableApi::class)
@Composable
fun SeeMoreScreen(
    navController: NavController,
    miniPlayer: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SeeMorePageViewModel = koinViewModel()
) {
    val appearance = LocalAppearance.current

    Skeleton(
        navController = navController,
        tabIndex = 0,
        onTabChanged = {},
        miniPlayer = miniPlayer,
        navBarContent = { item ->
            item(0, "", 0)
        }
    ) { _ ->
        val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
        val paddingValues = PaddingValues(16.dp, 8.dp, 16.dp, Dimensions.bottomSpacer)

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::onRefresh,
            modifier = modifier
        ) {
            val sections by viewModel.sections.collectAsStateWithLifecycle()

            if( sections.size > 1 ) {
                LazyColumn(
                    contentPadding = paddingValues
                ) {
                    items( items = sections ) { section ->
                        if( !section.title.isNullOrBlank() )
                            Title(
                                title = section.title.orEmpty(),
                                onClick = section.browseId?.let { browseId -> {
                                    NavRoutes.YT_SEE_MORE.navigateHere(navController, "$browseId?params=${section.params}")
                                } }
                            )

                        ItemUtils.LazyRowItem(
                            navController = navController,
                            innertubeItems = section.contents,
                            currentlyPlaying = null
                        )
                    }
                }
            } else {
                val albumItemValues = AlbumItem.Values.from( appearance )
                val artistItemValues = ArtistItem.Values.from( appearance )
                val playlistItemValues = PlaylistItem.Values.from( appearance )
                val cellSize = remember( sections ) {
                    val firstSection = sections.firstOrNull()
                    if( firstSection?.contents?.all { it is InnertubeAlbum } == true )
                        Preferences.HOME_ALBUM_ITEM_SIZE.value.dp
                    else if( firstSection?.contents?.all { it is InnertubeArtist } == true )
                        Preferences.HOME_ARTIST_ITEM_SIZE.value.dp
                    else if( firstSection?.contents?.all { it is InnertubePlaylist } == true )
                        Preferences.HOME_LIBRARY_ITEM_SIZE.value.dp
                    else
                        120.dp
                }

                LazyVerticalGrid(
                    columns = GridCells.FixedSize( cellSize ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy( 12.dp ),
                    contentPadding = paddingValues
                ) {
                    items(
                        items = sections.firstOrNull()?.contents.orEmpty()
                    ) { item ->
                        when( item ) {
                            is InnertubeAlbum -> {
                                AlbumItem.Vertical(
                                    innertubeAlbum = item,
                                    values = albumItemValues,
                                    navController = navController
                                )
                            }

                            is InnertubeArtist -> {
                                ArtistItem.Render(
                                    innertubeArtist = item,
                                    values = artistItemValues,
                                    navController = navController
                                )
                            }

                            is InnertubePlaylist -> {
                                PlaylistItem.Vertical(
                                    innertubePlaylist = item,
                                    values = playlistItemValues,
                                    navController = navController
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}