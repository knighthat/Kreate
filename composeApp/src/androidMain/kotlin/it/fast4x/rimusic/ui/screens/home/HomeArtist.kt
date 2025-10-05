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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import app.kreate.android.themed.rimusic.component.artist.ArtistItem
import app.kreate.android.themed.rimusic.component.tab.ItemSize
import app.kreate.android.themed.rimusic.component.tab.Sort
import app.kreate.android.utils.innertube.CURRENT_LOCALE
import app.kreate.android.utils.innertube.InnertubeUtils
import app.kreate.database.models.Artist
import it.fast4x.compose.persist.persistList
import it.fast4x.innertube.YtMusic
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.ArtistsType
import it.fast4x.rimusic.enums.FilterBy
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.enums.UiType
import it.fast4x.rimusic.ui.components.ButtonsRow
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import it.fast4x.rimusic.ui.components.tab.TabHeader
import it.fast4x.rimusic.ui.components.tab.toolbar.Randomizer
import it.fast4x.rimusic.ui.components.themed.FilterMenu
import it.fast4x.rimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import it.fast4x.rimusic.ui.components.themed.HeaderIconButton
import it.fast4x.rimusic.ui.components.themed.HeaderInfo
import it.fast4x.rimusic.ui.components.themed.MultiFloatingActionsContainer
import it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.autoSyncToolbutton
import it.fast4x.rimusic.utils.importYTMSubscribedChannels
import it.fast4x.rimusic.utils.semiBold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.component.tab.SongShuffler
import me.knighthat.innertube.Innertube
import me.knighthat.innertube.model.InnertubeArtist
import me.knighthat.utils.Toaster
import timber.log.Timber

@ExperimentalMaterial3Api
@UnstableApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@Composable
fun HomeArtists(
    navController: NavController,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    // Essentials
    val context = LocalContext.current
    val lazyGridState = rememberLazyGridState()
    val (colorPalette, typography) = LocalAppearance.current
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()

    // Settings
    var artistType by Preferences.HOME_ARTIST_TYPE
    var filterBy by Preferences.HOME_ARTIST_AND_ALBUM_FILTER


    var items by persistList<Artist>( "")
    var itemsToFilter by persistList<Artist>( "home/artists" )

    var onlineArtists by remember {
        mutableStateOf( emptyList<InnertubeArtist>() )
    }

    val search = remember { Search(lazyGridState) }

    var itemsOnDisplay by persistList<Artist>( "home/artists/on_display" )
    val onlineOnDisplay by remember {derivedStateOf {
        val localIds = itemsOnDisplay.fastMap( Artist::id )
        onlineArtists.fastFilter { it.id !in localIds  }
                     .fastFilter { filterBy === FilterBy.All || filterBy === FilterBy.YoutubeLibrary }
                     .fastFilter { search appearsIn it.name }
    }}


    val sort = remember {
        Sort(menuState, Preferences.HOME_ARTISTS_SORT_BY, Preferences.HOME_ARTISTS_SORT_ORDER)
    }
    val itemSize = remember { ItemSize(Preferences.HOME_ARTIST_ITEM_SIZE, menuState) }

    val randomizer = object: Randomizer<Artist> {
        override fun getItems(): List<Artist> = itemsOnDisplay
        override fun onClick(index: Int) = NavRoutes.YT_ARTIST.navigateHere( navController, itemsOnDisplay[index].id )

    }
    val shuffle = SongShuffler(
        databaseCall = Database.artistTable::allSongsInFollowing,
        key = arrayOf( artistType )
    )

    val buttonsList = ArtistsType.entries.map { it to it.text }

    if (!isYouTubeSyncEnabled()) {
        filterBy = FilterBy.All
    }

    LaunchedEffect( Unit, sort.sortBy, sort.sortOrder, artistType ) {
        when( artistType ) {
            ArtistsType.Favorites -> Database.artistTable.sortFollowing( sort.sortBy, sort.sortOrder )
            ArtistsType.Library -> Database.artistTable.sortInLibrary( sort.sortBy, sort.sortOrder )
        }.collect { itemsToFilter = it }
    }
    LaunchedEffect( Unit, itemsToFilter, filterBy ) {
        items = when(filterBy) {
            FilterBy.All -> itemsToFilter
            FilterBy.YoutubeLibrary -> itemsToFilter.filter { it.isYoutubeArtist }
            FilterBy.Local -> itemsToFilter.filterNot { it.isYoutubeArtist }
        }

    }
    LaunchedEffect( items, search.input ) {
        itemsOnDisplay = items.filter {
            it.name?.let( search::appearsIn ) ?: false
        }
    }
    if (items.any{it.thumbnailUrl == null}) {
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                items.filter { it.thumbnailUrl == null }.forEach { artist ->
                    coroutineScope.launch(Dispatchers.IO) {
                        val artistThumbnail = YtMusic.getArtistPage(artist.id).getOrNull()?.artist?.thumbnail?.url
                        Database.asyncTransaction {
                            artistTable.update( artist.copy(thumbnailUrl = artistThumbnail) )
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect( Unit ) {
        if( !InnertubeUtils.isLoggedIn || !Preferences.YOUTUBE_ARTISTS_SYNC.value )
            return@LaunchedEffect

        CoroutineScope( Dispatchers.IO ).launch {
            Innertube.library( CURRENT_LOCALE )
                     .onSuccess { results ->
                         onlineArtists = results.fastMapNotNull { it as? InnertubeArtist }
                     }
                     .onFailure { err ->
                         Timber.tag( "HomeArtists" ).e( err )
                         Toaster.e(
                             R.string.error_failed_to_sync_tab,
                             context.getString( R.string.artists ).lowercase()
                         )
                     }
        }
    }

    val sync = autoSyncToolbutton(R.string.autosync_channels)

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
        if (!justSynced && importYTMSubscribedChannels())
                justSynced = true
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = ::refresh
    ) {
        Box (
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
                TabHeader( R.string.artists ) {
                    HeaderInfo(items.size.toString(), R.drawable.people)
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
                            currentValue = artistType,
                            onValueUpdate = { artistType = it },
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

                val artistItemValues = remember( colorPalette, typography ) {
                    ArtistItem.Values.from( colorPalette, typography )
                }

                LazyVerticalGrid(
                    state = lazyGridState,
                    columns = GridCells.Adaptive( itemSize.size.dp ),
                    modifier = Modifier.background( colorPalette().background0 )
                                       .fillMaxSize(),
                    contentPadding = PaddingValues( bottom = Dimensions.bottomSpacer ),
                    verticalArrangement = Arrangement.spacedBy( ArtistItem.ROW_SPACING.dp )
                ) {
                    items(
                        items = onlineOnDisplay,
                        key = InnertubeArtist::id
                    ) { artist ->
                        ArtistItem.Render(
                            innertubeArtist = artist,
                            widthDp = itemSize.size.dp,
                            values = artistItemValues,
                            navController = navController,
                            onClick = search::hideIfEmpty
                        )
                    }

                    items(
                        items = itemsOnDisplay,
                        key = Artist::id
                    ) { artist ->
                        ArtistItem.Render(
                            artist = artist,
                            widthDp = itemSize.size.dp,
                            values = artistItemValues,
                            navController = navController,
                            onClick = search::hideIfEmpty,
                        )
                    }
                }
            }

            FloatingActionsContainerWithScrollToTop(lazyGridState = lazyGridState)

            val showFloatingIcon by Preferences.SHOW_FLOATING_ICON
            if( UiType.ViMusic.isCurrent() && showFloatingIcon )
                MultiFloatingActionsContainer(
                    iconId = R.drawable.search,
                    onClick = onSearchClick,
                    onClickSettings = onSettingsClick,
                    onClickSearch = onSearchClick
                )
        }
    }
}

