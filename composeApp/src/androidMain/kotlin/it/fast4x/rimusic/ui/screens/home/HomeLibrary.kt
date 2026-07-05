package it.fast4x.rimusic.ui.screens.home

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFilter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.LocalBottomMenu
import app.kreate.android.R
import app.kreate.android.constant.MenuPage
import app.kreate.android.themed.rimusic.component.Search
import app.kreate.android.themed.rimusic.component.playlist.PlaylistItem
import app.kreate.android.themed.rimusic.component.tab.ItemSize
import app.kreate.android.themed.rimusic.component.tab.Sort
import app.kreate.android.viewmodel.home.HomeLibraryViewModel
import app.kreate.database.Database
import app.kreate.preferences.Preferences
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.enums.PlaylistsType
import it.fast4x.rimusic.enums.UiType
import it.fast4x.rimusic.ui.components.ButtonsRow
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import it.fast4x.rimusic.ui.components.tab.TabHeader
import it.fast4x.rimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import it.fast4x.rimusic.ui.components.themed.HeaderInfo
import it.fast4x.rimusic.ui.components.themed.MultiFloatingActionsContainer
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.CheckMonthlyPlaylist
import kotlinx.coroutines.ExperimentalCoroutinesApi
import me.knighthat.component.playlist.NewPlaylistDialog
import me.knighthat.component.tab.ImportSongsFromCSV
import me.knighthat.component.tab.SongShuffler
import org.koin.compose.viewmodel.koinViewModel


@OptIn(ExperimentalCoroutinesApi::class)
@ExperimentalMaterial3Api
@UnstableApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalAnimationApi
@ExperimentalFoundationApi
@Composable
fun HomeLibrary(
    navController: NavController,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeLibraryViewModel = koinViewModel()
) {
    // Essentials
    val lazyGridState = rememberLazyGridState()
    val menuState = LocalMenuState.current
    val appearance = LocalAppearance.current
    val menu = LocalBottomMenu.current
    val coroutineScope = rememberCoroutineScope()

    // Non-vital
    val playlistType by Preferences.HOME_LIBRARY_TYPE.collectAsStateWithLifecycle()

    val items by viewModel.playlists.collectAsStateWithLifecycle()

    val search = remember { Search(lazyGridState) }

    val itemsOnDisplay by remember {derivedStateOf {
        items.fastFilter {
                 when( playlistType ) {
                     PlaylistsType.MonthlyPlaylist -> it.playlist.isMonthly
                     PlaylistsType.PinnedPlaylist -> it.playlist.isPinned
                     PlaylistsType.YTPlaylist -> it.playlist.isYoutubePlaylist
                     PlaylistsType.Playlist -> true
                 }
             }
             .fastFilter { search appearsIn it.playlist.cleanName() }
    }}

    val sort = remember {
        Sort(menuState, Preferences.HOME_LIBRARY_SORT_BY, Preferences.HOME_LIBRARY_SORT_ORDER, coroutineScope)
    }
    val itemSize = remember { ItemSize(coroutineScope, Preferences.HOME_LIBRARY_ITEM_SIZE, menuState) }
    val sizeDp by remember {derivedStateOf {
        DpSize(itemSize.size.dp, itemSize.size.dp)
    }}

    //<editor-fold desc="Songs shuffler">
    /**
     * Previous implementation calls this every time shuffle button is clicked.
     * It is extremely slow since the database needs some time to look for and
     * sort songs before it can go through and start playing.
     *
     * This implementation will make sure that new list is fetched when [PlaylistsType]
     * is changed, but this process happens in the background, therefore, there's no
     * visible penalty. Furthermore, this will reduce load time significantly.
     */
    val shuffle = SongShuffler(
        databaseCall = when( playlistType ) {
            PlaylistsType.Playlist          -> Database.playlistTable::allSongs
            PlaylistsType.PinnedPlaylist    -> Database.playlistTable::allPinnedSongs
            PlaylistsType.MonthlyPlaylist   -> Database.playlistTable::allMonthlySongs
            PlaylistsType.YTPlaylist        -> Database.playlistTable::allYTPlaylistSongs
        },
        key = arrayOf( playlistType )
    )
    //</editor-fold>
    //<editor-fold desc="New playlist dialog">
    val newPlaylistDialog = NewPlaylistDialog()
    //</editor-fold>
    val importPlaylistDialog = ImportSongsFromCSV()

    // START: Additional playlists
    val showPinnedPlaylists by Preferences.SHOW_PINNED_PLAYLISTS.collectAsStateWithLifecycle()
    val showMonthlyPlaylists by Preferences.SHOW_MONTHLY_PLAYLISTS.collectAsStateWithLifecycle()

    val buttonsList = mutableListOf(PlaylistsType.Playlist to stringResource(R.string.playlists))
    buttonsList += PlaylistsType.YTPlaylist to stringResource(R.string.yt_playlists)
    if (showPinnedPlaylists) buttonsList +=
        PlaylistsType.PinnedPlaylist to stringResource(R.string.pinned_playlists)
    if (showMonthlyPlaylists) buttonsList +=
        PlaylistsType.MonthlyPlaylist to stringResource(R.string.monthly_playlists)
    // END - Additional playlists


    // START - New playlist
    newPlaylistDialog.Render()
    // END - New playlist

    // START - Monthly playlist
    val compileMonthlyPlaylist by Preferences.MONTHLY_PLAYLIST_COMPILATION.collectAsStateWithLifecycle()
    if ( compileMonthlyPlaylist )
        CheckMonthlyPlaylist()
    // END - Monthly playlist

    val playlistItemValues = remember( appearance ) {
        PlaylistItem.Values.from( appearance )
    }

    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::onRefresh
    ) {
        Box(
            modifier = Modifier
                .background(colorPalette().background0)
                //.fillMaxSize()
                .fillMaxHeight()
                .fillMaxWidth(
                    if (NavigationBarPosition.Right.isCurrent())
                        Dimensions.contentWidthRightBar
                    else
                        1f
                )
        ) {
            Column( Modifier.fillMaxSize() ) {
                // Sticky tab's title
                TabHeader( R.string.playlists ) {
                    HeaderInfo( itemsOnDisplay.size.toString(), R.drawable.playlist )
                }

                // Sticky tab's tool bar
                TabToolBar.Buttons( sort, search, shuffle, newPlaylistDialog, importPlaylistDialog, itemSize )

                // Sticky search bar
                search.SearchBar()

                LazyVerticalGrid(
                    state = lazyGridState,
                    columns = GridCells.Adaptive( itemSize.size.dp ),
                    modifier = Modifier.background( colorPalette().background0 ),
                    verticalArrangement = Arrangement.spacedBy( PlaylistItem.ROW_SPACING.dp ),
                    contentPadding = PaddingValues(bottom = Dimensions.bottomSpacer)
                ) {
                    item(
                        key = "separator",
                        contentType = 0,
                        span = { GridItemSpan(maxLineSpan) }) {
                        ButtonsRow(
                            chips = buttonsList,
                            currentValue = playlistType,
                            onValueUpdate = { newValue ->
                                Preferences.HOME_LIBRARY_TYPE.update( newValue )
                            },
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp)
                        )
                    }

                    items(
                        items = itemsOnDisplay,
                        key = System::identityHashCode
                    ) { preview ->
                        PlaylistItem.Vertical(
                            playlist = preview.playlist,
                            values = playlistItemValues,
                            songCount = preview.songCount,
                            navController = navController,
                            sizeDp = sizeDp,
                            thumbnailUrl = preview.thumbnailUrl,
                            onClick = search::hideIfEmpty,
                            onLongClick = {
                                val page = MenuPage.LocalPlaylist(preview)
                                menu.show( page, true )
                            }
                        )
                    }
                }
            }

            FloatingActionsContainerWithScrollToTop(lazyGridState = lazyGridState)

            val showFloatingIcon by Preferences.SHOW_FLOATING_ICON.collectAsStateWithLifecycle()
            if (UiType.ViMusic.isCurrent() && showFloatingIcon)
                MultiFloatingActionsContainer(
                    iconId = R.drawable.search,
                    onClick = onSearchClick,
                    onClickSettings = onSettingsClick,
                    onClickSearch = onSearchClick
                )
        }
    }
}
