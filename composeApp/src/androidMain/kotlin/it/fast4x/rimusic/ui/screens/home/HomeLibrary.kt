package it.fast4x.rimusic.ui.screens.home

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.rimusic.component.Search
import app.kreate.android.themed.rimusic.component.tab.ItemSize
import app.kreate.android.themed.rimusic.component.tab.Sort
import it.fast4x.compose.persist.persistList
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.MONTHLY_PREFIX
import it.fast4x.rimusic.PINNED_PREFIX
import it.fast4x.rimusic.YTP_PREFIX
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.enums.PlaylistsType
import it.fast4x.rimusic.enums.UiType
import it.fast4x.rimusic.models.Playlist
import it.fast4x.rimusic.models.PlaylistPreview
import it.fast4x.rimusic.ui.components.ButtonsRow
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import it.fast4x.rimusic.ui.components.tab.TabHeader
import it.fast4x.rimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import it.fast4x.rimusic.ui.components.themed.HeaderInfo
import it.fast4x.rimusic.ui.components.themed.MultiFloatingActionsContainer
import it.fast4x.rimusic.ui.items.PlaylistItem
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.utils.CheckMonthlyPlaylist
import it.fast4x.rimusic.utils.autoSyncToolbutton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.knighthat.component.playlist.NewPlaylistDialog
import me.knighthat.component.tab.ImportSongsFromCSV
import me.knighthat.component.tab.SongShuffler


@ExperimentalMaterial3Api
@UnstableApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalAnimationApi
@ExperimentalFoundationApi
@Composable
fun HomeLibrary(
    onPlaylistClick: (Playlist) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    // Essentials
    val lazyGridState = rememberLazyGridState()
    val menuState = LocalMenuState.current

    // Non-vital
    var playlistType by Preferences.HOME_LIBRARY_TYPE
    val disableScrollingText by Preferences.SCROLLING_TEXT_DISABLED

    var items by persistList<PlaylistPreview>("home/playlists")

    var itemsOnDisplay by persistList<PlaylistPreview>("home/playlists/on_display")

    val search = remember { Search(lazyGridState) }

    val sort = remember {
        Sort(menuState, Preferences.HOME_LIBRARY_SORT_BY, Preferences.HOME_LIBRARY_SORT_ORDER)
    }
    val itemSize = remember { ItemSize(Preferences.HOME_LIBRARY_ITEM_SIZE, menuState) }

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
    val sync = autoSyncToolbutton(R.string.autosync)

    LaunchedEffect( sort.sortBy, sort.sortOrder ) {
        Database.playlistTable
                .sortPreviews( sort.sortBy, sort.sortOrder )
                .distinctUntilChanged()
                .collect { items = it }
    }
    LaunchedEffect( items, search.input ) {
        itemsOnDisplay = items.filter {
            search appearsIn it.playlist.name
        }
    }

    // START: Additional playlists
    val showPinnedPlaylists by Preferences.SHOW_PINNED_PLAYLISTS
    val showMonthlyPlaylists by Preferences.SHOW_MONTHLY_PLAYLISTS

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
    if ( Preferences.MONTHLY_PLAYLIST_COMPILATION.value )
        CheckMonthlyPlaylist()
    // END - Monthly playlist

    val doAutoSync by Preferences.AUTO_SYNC
    var justSynced by rememberSaveable { mutableStateOf(!doAutoSync) }

    var refreshing by remember { mutableStateOf(false) }
    val refreshScope = rememberCoroutineScope()

    fun refresh() {
        if (refreshing) return
        refreshScope.launch(Dispatchers.IO) {
            refreshing = true
            justSynced = false
            delay(500)
            refreshing = false
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = ::refresh
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
                    HeaderInfo( items.size.toString(), R.drawable.playlist )
                }

                // Sticky tab's tool bar
                TabToolBar.Buttons( sort, sync, search, shuffle, newPlaylistDialog, importPlaylistDialog, itemSize )

                // Sticky search bar
                search.SearchBar()

                LazyVerticalGrid(
                    state = lazyGridState,
                    columns = GridCells.Adaptive( itemSize.size.dp ),
                    modifier = Modifier
                        .background(colorPalette().background0)
                ) {
                    item(
                        key = "separator",
                        contentType = 0,
                        span = { GridItemSpan(maxLineSpan) }) {
                        ButtonsRow(
                            chips = buttonsList,
                            currentValue = playlistType,
                            onValueUpdate = { playlistType = it },
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp)
                        )
                    }

                    val listPrefix =
                        when( playlistType ) {
                            PlaylistsType.Playlist -> ""    // Matches everything
                            PlaylistsType.PinnedPlaylist -> PINNED_PREFIX
                            PlaylistsType.MonthlyPlaylist -> MONTHLY_PREFIX
                            PlaylistsType.YTPlaylist -> YTP_PREFIX
                        }
                    val condition: (PlaylistPreview) -> Boolean = {
                        if (playlistType == PlaylistsType.YTPlaylist){
                            it.playlist.isYoutubePlaylist
                        } else it.playlist.name.startsWith( listPrefix, true )
                    }
                    items(
                        items = itemsOnDisplay.filter( condition ),
                        key = { it.playlist.id }
                    ) { preview ->
                        PlaylistItem(
                            playlist = preview,
                            thumbnailSizeDp = itemSize.size.dp,
                            thumbnailSizePx = itemSize.size.px,
                            alternative = true,
                            modifier = Modifier
                                .fillMaxSize()
                                .animateItem(fadeInSpec = null, fadeOutSpec = null)
                                .clickable(onClick = {
                                    search.hideIfEmpty()
                                    onPlaylistClick(preview.playlist)
                                }),
                            disableScrollingText = disableScrollingText,
                            isYoutubePlaylist = preview.playlist.isYoutubePlaylist,
                            isEditable = preview.playlist.isEditable
                        )
                    }

                    item(
                        key = "footer",
                        contentType = 0,
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
                    }

                }
            }

            FloatingActionsContainerWithScrollToTop(lazyGridState = lazyGridState)

            val showFloatingIcon by Preferences.SHOW_FLOATING_ICON
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
