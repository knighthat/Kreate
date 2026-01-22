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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastMapNotNull
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.rimusic.component.Search
import app.kreate.android.themed.rimusic.component.playlist.PlaylistItem
import app.kreate.android.themed.rimusic.component.tab.ItemSize
import app.kreate.android.themed.rimusic.component.tab.Sort
import app.kreate.android.utils.innertube.CURRENT_LOCALE
import app.kreate.android.utils.innertube.InnertubeUtils
import app.kreate.database.models.PlaylistPreview
import app.kreate.util.MONTHLY_PREFIX
import app.kreate.util.PINNED_PREFIX
import app.kreate.util.YTP_PREFIX
import it.fast4x.compose.persist.persistList
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavRoutes
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
import it.fast4x.rimusic.utils.autoSyncToolbutton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import me.knighthat.component.playlist.NewPlaylistDialog
import me.knighthat.component.tab.ImportSongsFromCSV
import me.knighthat.component.tab.SongShuffler
import me.knighthat.innertube.Innertube
import me.knighthat.innertube.model.InnertubePlaylist
import me.knighthat.utils.Toaster
import timber.log.Timber


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
    onSettingsClick: () -> Unit
) {
    // Essentials
    val context = LocalContext.current
    val lazyGridState = rememberLazyGridState()
    val menuState = LocalMenuState.current
    val appearance = LocalAppearance.current

    // Non-vital
    var playlistType by Preferences.HOME_LIBRARY_TYPE
    val listPrefix by remember {derivedStateOf {
        when( playlistType ) {
            PlaylistsType.Playlist -> ""    // Matches everything
            PlaylistsType.PinnedPlaylist -> PINNED_PREFIX
            PlaylistsType.MonthlyPlaylist -> MONTHLY_PREFIX
            PlaylistsType.YTPlaylist -> YTP_PREFIX
        }
    }}

    var items by persistList<PlaylistPreview>("home/playlists")
    var onlinePlaylists by remember { mutableStateOf( emptyList<InnertubePlaylist>() ) }

    val search = remember { Search(lazyGridState) }

    val itemsOnDisplay by remember {derivedStateOf {
        items.fastFilter {
                 (playlistType == PlaylistsType.YTPlaylist && it.playlist.isYoutubePlaylist)
                         || it.playlist.name.startsWith( listPrefix, true )
             }
             .fastFilter { search appearsIn it.playlist.cleanName() }
    }}
    val onlineOnDisplay by remember {derivedStateOf {
        onlinePlaylists.fastFilter { listPrefix.isBlank() || playlistType == PlaylistsType.YTPlaylist }
                       .fastFilter { search appearsIn it.name }
    }}

    val sort = remember {
        Sort(menuState, Preferences.HOME_LIBRARY_SORT_BY, Preferences.HOME_LIBRARY_SORT_ORDER)
    }
    val itemSize = remember { ItemSize(Preferences.HOME_LIBRARY_ITEM_SIZE, menuState) }
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
    val sync = autoSyncToolbutton(R.string.autosync)

    LaunchedEffect( sort.sortBy, sort.sortOrder ) {
        Database.playlistTable
                .sortPreviews( sort.sortBy, sort.sortOrder )
                .distinctUntilChanged()
                .flowOn(Dispatchers.Default )
                .collectLatest { items = it }
    }
    LaunchedEffect( Unit ) {
        if( !InnertubeUtils.isLoggedIn || !Preferences.YOUTUBE_PLAYLISTS_SYNC.value )
            return@LaunchedEffect
        
        CoroutineScope( Dispatchers.IO ).launch {
            Innertube.library( CURRENT_LOCALE )
                     .onSuccess { results ->
                         onlinePlaylists = results.fastMapNotNull { it as? InnertubePlaylist }
                     }
                     .onFailure { err ->
                         Timber.tag( "HomePlaylist" ).e( err )
                         Toaster.e(
                             R.string.error_failed_to_sync_tab,
                             context.getString( R.string.playlists ).lowercase()
                         )
                     }
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

    val playlistItemValues = remember( appearance ) {
        PlaylistItem.Values.from( appearance )
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
                            onValueUpdate = { playlistType = it },
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp)
                        )
                    }

                    items(
                        items = onlineOnDisplay,
                        key = InnertubePlaylist::id
                    ) { playlist ->
                        PlaylistItem.Vertical(
                            innertubePlaylist = playlist,
                            values = playlistItemValues,
                            navController = null,
                            sizeDp = sizeDp,
                            onClick = {
                                search.hideIfEmpty()

                                NavRoutes.YT_PLAYLIST.navigateHere(
                                    navController = navController,
                                    path = "${playlist.id}?useLogin=true"
                                )
                            }
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
                            onClick = search::hideIfEmpty
                        )
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
