package it.fast4x.rimusic.ui.screens.home

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMapNotNull
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.rimusic.component.Search
import app.kreate.android.themed.rimusic.component.album.AlbumItem
import app.kreate.android.themed.rimusic.component.tab.ItemSize
import app.kreate.android.themed.rimusic.component.tab.Sort
import app.kreate.android.utils.innertube.CURRENT_LOCALE
import app.kreate.android.utils.innertube.InnertubeUtils
import it.fast4x.compose.persist.persistList
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.MODIFIED_PREFIX
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.AlbumsType
import it.fast4x.rimusic.enums.FilterBy
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.enums.UiType
import it.fast4x.rimusic.models.Album
import it.fast4x.rimusic.models.Song
import it.fast4x.rimusic.ui.components.ButtonsRow
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import it.fast4x.rimusic.ui.components.tab.TabHeader
import it.fast4x.rimusic.ui.components.tab.toolbar.Randomizer
import it.fast4x.rimusic.ui.components.themed.AlbumsItemMenu
import it.fast4x.rimusic.ui.components.themed.FilterMenu
import it.fast4x.rimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import it.fast4x.rimusic.ui.components.themed.HeaderIconButton
import it.fast4x.rimusic.ui.components.themed.HeaderInfo
import it.fast4x.rimusic.ui.components.themed.InputTextDialog
import it.fast4x.rimusic.ui.components.themed.MultiFloatingActionsContainer
import it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.addNext
import it.fast4x.rimusic.utils.addToYtPlaylist
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.autoSyncToolbutton
import it.fast4x.rimusic.utils.enqueue
import it.fast4x.rimusic.utils.importYTMLikedAlbums
import it.fast4x.rimusic.utils.semiBold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.knighthat.component.tab.SongShuffler
import me.knighthat.database.AlbumTable
import me.knighthat.innertube.Innertube
import me.knighthat.innertube.model.InnertubeAlbum
import me.knighthat.utils.Toaster
import timber.log.Timber

@OptIn(ExperimentalMaterial3Api::class)
@ExperimentalTextApi
@UnstableApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@Composable
fun HomeAlbums(
    navController: NavController,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    // Essentials
    val menuState = LocalMenuState.current
    val binder = LocalPlayerServiceBinder.current
    val lazyGridState = rememberLazyGridState()
    val (colorPalette, typography) = LocalAppearance.current
    val context = LocalContext.current

    // Settings
    var albumType by Preferences.HOME_ALBUM_TYPE
    var filterBy by Preferences.HOME_ARTIST_AND_ALBUM_FILTER


    var items by persistList<Album>( "home/albums" )
    var itemsToFilter by persistList<Album>( "home/artists" )
    var onlineAlbums by remember {
        mutableStateOf( emptyList<InnertubeAlbum>() )
    }

    val search = remember { Search(lazyGridState) }

    var itemsOnDisplay by persistList<Album>( "home/albums/on_display" )
    val onlineOnDisplay by remember {derivedStateOf {
        val localIds = itemsOnDisplay.fastMap( Album::id )
        onlineAlbums.fastFilter { it.id !in localIds }
                    .fastFilter { filterBy === FilterBy.All || filterBy === FilterBy.YoutubeLibrary }
                    .fastFilter { search appearsIn it.name }
    }}

    val sort = remember {
        Sort(menuState, Preferences.HOME_ALBUMS_SORT_BY, Preferences.HOME_ALBUM_SORT_ORDER)
    }
    val itemSize = remember { ItemSize(Preferences.HOME_ALBUM_ITEM_SIZE, menuState) }

    val randomizer = object: Randomizer<Album> {
        override fun getItems(): List<Album> = itemsOnDisplay
        override fun onClick(index: Int) = NavRoutes.YT_ALBUM.navigateHere( navController, itemsOnDisplay[index].id )
    }
    val shuffle = SongShuffler(
        databaseCall = Database.albumTable::allSongsInBookmarked,
        key = arrayOf( albumType )
    )

    val buttonsList = AlbumsType.entries.map { it to it.text }

    if (!isYouTubeSyncEnabled()) {
        filterBy = FilterBy.All
    }

    LaunchedEffect( sort.sortBy, sort.sortOrder, albumType ) {
        when ( albumType ) {
            AlbumsType.Favorites -> Database.albumTable.sortBookmarked( sort.sortBy, sort.sortOrder )
            AlbumsType.Library -> Database.albumTable.sortInLibrary( sort.sortBy, sort.sortOrder )
        }.collect { itemsToFilter = it }
    }
    LaunchedEffect( Unit, itemsToFilter, filterBy ) {
        items = when(filterBy) {
            FilterBy.All -> itemsToFilter
            FilterBy.YoutubeLibrary -> itemsToFilter.filter { it.isYoutubeAlbum }
            FilterBy.Local -> itemsToFilter.filterNot { it.isYoutubeAlbum }
        }

    }
    LaunchedEffect( items, search.input ) {
        itemsOnDisplay = items.filter {
            it.title?.let( search::appearsIn ) ?: false
                    || it.year?.let( search::appearsIn ) ?: false
                    || it.authorsText?.let( search::appearsIn ) ?: false
        }
    }

    LaunchedEffect( Unit ) {
        // TODO Convert to fetch from the internet
        Database.asyncTransaction {
            // Only occurs when album doesn't have thumbnailUrl assigned
            items.filter { it.thumbnailUrl == null }
                 .forEach { album ->
                     /**
                      * Topology:
                      *
                      * Return the most frequently occurring [Song.thumbnailUrl]
                      * among all songs of this album.
                      *
                      * Explanation:
                      *
                      * [Song.thumbnailUrl] can be changed by user.
                      * If 1 song has its thumbnail changed, the result
                      * remains the same because all others have the same url.
                      *
                      * Even when most changed to different urls, it only needs
                      * 2 songs to have the same [Song.thumbnailUrl] to return
                      * the same result.
                      */
                     runBlocking {
                         songAlbumMapTable.allSongsOf( album.id )
                                          .first()
                                          .groupingBy( Song::thumbnailUrl )
                                          .eachCount()
                                          .maxByOrNull { it.value }
                                          ?.key
                     }?.let { albumTable.updateCover( album.id, it ) }
                 }
        }
    }
    LaunchedEffect( Unit ) {
        if( !InnertubeUtils.isLoggedIn || !Preferences.YOUTUBE_ALBUMS_SYNC.value )
            return@LaunchedEffect

        CoroutineScope( Dispatchers.IO ).launch {
            Innertube.library( CURRENT_LOCALE )
                     .onSuccess { results ->
                         onlineAlbums = results.fastMapNotNull { it as? InnertubeAlbum }
                     }
                     .onFailure { err ->
                         Timber.tag( "HomeAlbums" ).e( err )
                         Toaster.e(
                             R.string.error_failed_to_sync_tab,
                             context.getString( R.string.albums ).lowercase()
                         )
                     }
        }
    }

    val sync = autoSyncToolbutton(R.string.autosync_albums)

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

    // START: Import YTM subscribed channels
    LaunchedEffect(justSynced, doAutoSync) {
        if (!justSynced && importYTMLikedAlbums())
            justSynced = true
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = ::refresh
    ) {
        Box(
            modifier = Modifier
                .background(colorPalette().background0)
                .fillMaxHeight()
                .fillMaxWidth(
                    if( NavigationBarPosition.Right.isCurrent() )
                        Dimensions.contentWidthRightBar
                    else
                        1f
                )
        ) {
            Column( Modifier.fillMaxSize() ) {
                // Sticky tab's title
                TabHeader(R.string.albums) {
                    HeaderInfo(items.size.toString(), R.drawable.album)
                }

                // Sticky tab's tool bar
                TabToolBar.Buttons( sort, sync, search, randomizer, shuffle, itemSize )

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        //.padding(vertical = 4.dp)
                        .padding(bottom = 8.dp)
                        .fillMaxWidth()
                ) {
                    Box {
                        ButtonsRow(
                            chips = buttonsList,
                            currentValue = albumType,
                            onValueUpdate = { albumType = it },
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        if (isYouTubeSyncEnabled()) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                            ) {
                                BasicText(
                                    text = when (filterBy) {
                                        FilterBy.All -> stringResource(R.string.all)
                                        FilterBy.Local -> stringResource(R.string.on_device)
                                        FilterBy.YoutubeLibrary -> stringResource(R.string.ytm_library)
                                    },
                                    style = typography.xs.semiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically)
                                        .padding(end = 5.dp)
                                        .clickable {
                                            menuState.display {
                                                FilterMenu(
                                                    title = stringResource(R.string.filter_by),
                                                    onDismiss = menuState::hide,
                                                    onAll = { filterBy = FilterBy.All },
                                                    onYoutubeLibrary = {
                                                        filterBy = FilterBy.YoutubeLibrary
                                                    },
                                                    onLocal = { filterBy = FilterBy.Local }
                                                )
                                            }

                                        }
                                )
                                HeaderIconButton(
                                    icon = R.drawable.playlist,
                                    color = colorPalette.text,
                                    onClick = {},
                                    modifier = Modifier
                                        .offset(0.dp, 2.5.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = {}
                                        )
                                )
                            }
                        }
                    }
                }

                // Sticky search bar
                search.SearchBar()

                val albumItemValues = remember( colorPalette, typography ) {
                    AlbumItem.Values.from( colorPalette, typography )
                }

                LazyVerticalGrid(
                    state = lazyGridState,
                    columns = GridCells.Adaptive( itemSize.size.dp ),
                    //contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                    modifier = Modifier.background( colorPalette().background0 )
                                       .fillMaxSize(),
                    contentPadding = PaddingValues( bottom = Dimensions.bottomSpacer ),
                    verticalArrangement = Arrangement.spacedBy(AlbumItem.ROW_SPACING.dp )
                ) {
                    items(
                        items = onlineOnDisplay,
                        key = InnertubeAlbum::id
                    ) { album ->
                        AlbumItem.Vertical(
                            innertubeAlbum = album,
                            widthDp = itemSize.size.dp,
                            values = albumItemValues,
                            navController = navController,
                            onClick = search::hideIfEmpty
                        )
                    }

                    items(
                        items = itemsOnDisplay,
                        key = Album::id
                    ) { album ->
                        val songs by remember {
                            Database.songAlbumMapTable
                                    .allSongsOf( album.id )
                                    .distinctUntilChanged()
                        }.collectAsState( emptyList(), Dispatchers.IO )

                        var showDialogChangeAlbumTitle by remember {
                            mutableStateOf(false)
                        }
                        var showDialogChangeAlbumAuthors by remember {
                            mutableStateOf(false)
                        }
                        var showDialogChangeAlbumCover by remember {
                            mutableStateOf(false)
                        }

                        var onDismiss: () -> Unit = {}
                        var titleId = 0
                        var defValue = ""
                        var placeholderTextId: Int = 0
                        var queryBlock: (AlbumTable, String, String) -> Int = { _, _, _ -> 0}

                        if( showDialogChangeAlbumCover ) {
                            onDismiss = { showDialogChangeAlbumCover = false }
                            titleId = R.string.update_cover
                            defValue = album.thumbnailUrl.toString()
                            placeholderTextId = R.string.cover
                            queryBlock = AlbumTable::updateCover
                        } else if( showDialogChangeAlbumTitle ) {
                            onDismiss = { showDialogChangeAlbumTitle = false }
                            titleId = R.string.update_title
                            defValue = album.title.toString()
                            placeholderTextId = R.string.title
                            queryBlock = AlbumTable::updateTitle
                        } else if( showDialogChangeAlbumAuthors ) {
                            onDismiss = { showDialogChangeAlbumAuthors = false }
                            titleId = R.string.update_authors
                            defValue = album.authorsText.toString()
                            placeholderTextId = R.string.authors
                            queryBlock = AlbumTable::updateAuthors
                        }

                        if( showDialogChangeAlbumTitle || showDialogChangeAlbumAuthors || showDialogChangeAlbumCover )
                            InputTextDialog(
                                onDismiss = onDismiss,
                                title = stringResource( titleId ),
                                value = defValue,
                                placeholder = stringResource( placeholderTextId ),
                                setValue = {
                                    if (it.isNotEmpty())
                                        Database.asyncTransaction { queryBlock( albumTable, album.id, it ) }
                                },
                                prefix = MODIFIED_PREFIX
                            )

                        var position by remember {
                            mutableIntStateOf(0)
                        }

                        AlbumItem.Vertical(
                            album = album,
                            widthDp = itemSize.size.dp,
                            values = albumItemValues,
                            navController = navController,
                            onLongClick = {
                                menuState.display {
                                    AlbumsItemMenu(
                                        navController = navController,
                                        onDismiss = menuState::hide,
                                        album = album,
                                        onChangeAlbumTitle = {
                                            showDialogChangeAlbumTitle = true
                                        },
                                        onChangeAlbumAuthors = {
                                            showDialogChangeAlbumAuthors = true
                                        },
                                        onChangeAlbumCover = {
                                            showDialogChangeAlbumCover = true
                                        },
                                        onPlayNext = {
                                            println("mediaItem ${songs}")
                                            binder?.player?.addNext( songs )
                                        },
                                        onEnqueue = {
                                            println("mediaItem ${songs}")
                                            binder?.player?.enqueue( songs )
                                        },
                                        onAddToPlaylist = { playlistPreview ->
                                            position =
                                                playlistPreview.songCount.minus(1) ?: 0
                                            //Log.d("mediaItem", " maxPos in Playlist $it ${position}")
                                            if (position > 0) position++ else position =
                                                0

                                            if (!isYouTubeSyncEnabled() || !playlistPreview.playlist.isYoutubePlaylist) {
                                                songs.forEachIndexed { index, song ->
                                                    Database.asyncTransaction {
                                                        mapIgnore( playlistPreview.playlist, song )
                                                    }
                                                }
                                            } else {
                                                CoroutineScope(Dispatchers.IO).launch {
                                                    addToYtPlaylist(playlistPreview.playlist.id,
                                                        position,
                                                        playlistPreview.playlist.browseId ?: "",
                                                        songs.map{it.asMediaItem})
                                                }
                                            }


                                        },
                                        onGoToPlaylist = {
                                            NavRoutes.localPlaylist.navigateHere( navController, it.toString() )
                                        }
                                    )
                                }
                            },
                            onClick = search::hideIfEmpty
                        )
                    }
                }
            }

            FloatingActionsContainerWithScrollToTop( lazyGridState )

            val showFloatingIcon by Preferences.SHOW_FLOATING_ICON
            if ( UiType.ViMusic.isCurrent() && showFloatingIcon )
                MultiFloatingActionsContainer(
                    iconId = R.drawable.search,
                    onClick = onSearchClick,
                    onClickSettings = onSettingsClick,
                    onClickSearch = onSearchClick
                )
        }
    }
}

