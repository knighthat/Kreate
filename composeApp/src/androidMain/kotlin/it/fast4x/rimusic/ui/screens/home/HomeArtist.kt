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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.themed.rimusic.component.Search
import app.kreate.android.themed.rimusic.component.artist.ArtistItem
import app.kreate.android.themed.rimusic.component.tab.ItemSize
import app.kreate.android.themed.rimusic.component.tab.Sort
import app.kreate.android.viewmodel.home.HomeArtistsViewModel
import app.kreate.compose.R
import app.kreate.database.Database
import app.kreate.database.models.Artist
import app.kreate.preferences.Preferences
import it.fast4x.compose.persist.persistList
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
import it.fast4x.rimusic.utils.semiBold
import me.knighthat.component.tab.SongShuffler
import org.koin.compose.viewmodel.koinViewModel

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
    onSettingsClick: () -> Unit,
    viewModel: HomeArtistsViewModel = koinViewModel()
) {
    // Essentials
    val lazyGridState = rememberLazyGridState()
    val (colorPalette, typography) = LocalAppearance.current
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()

    // Settings
    val artistType by Preferences.HOME_ARTIST_TYPE.collectAsStateWithLifecycle()
    val filterBy by Preferences.HOME_ARTIST_AND_ALBUM_FILTER.collectAsStateWithLifecycle()


    val items by viewModel.artists.collectAsStateWithLifecycle()

    val search = remember { Search(lazyGridState) }

    var itemsOnDisplay by persistList<Artist>( "home/artists/on_display" )

    val sort = remember {
        Sort(menuState, Preferences.HOME_ARTISTS_SORT_BY, Preferences.HOME_ARTISTS_SORT_ORDER, coroutineScope)
    }
    val itemSize = remember { ItemSize(coroutineScope, Preferences.HOME_ARTIST_ITEM_SIZE, menuState) }
    val sizeDp by remember {derivedStateOf {
        DpSize(itemSize.size.dp, itemSize.size.dp)
    }}

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
        Preferences.HOME_ARTIST_AND_ALBUM_FILTER.update( FilterBy.All )
    }

    LaunchedEffect( items, search.input ) {
        itemsOnDisplay = items.filter {
            it.name?.let( search::appearsIn ) ?: false
        }
    }

    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::onRefresh
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
                TabToolBar.Buttons( sort, search, randomizer, shuffle, itemSize )

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
                            onValueUpdate = { newValue ->
                                Preferences.HOME_ARTIST_TYPE.update( newValue )
                            },
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
                                                    onAll = {
                                                        Preferences.HOME_ARTIST_AND_ALBUM_FILTER.update( FilterBy.All )
                                                            },
                                                    onYoutubeLibrary = {
                                                        Preferences.HOME_ARTIST_AND_ALBUM_FILTER.update( FilterBy.YoutubeLibrary )
                                                    },
                                                    onLocal = {
                                                        Preferences.HOME_ARTIST_AND_ALBUM_FILTER.update( FilterBy.Local )
                                                    }
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
                        items = itemsOnDisplay,
                        key = Artist::id
                    ) { artist ->
                        ArtistItem.Render(
                            artist = artist,
                            values = artistItemValues,
                            navController = navController,
                            sizeDp = sizeDp,
                            onClick = search::hideIfEmpty,
                        )
                    }
                }
            }

            FloatingActionsContainerWithScrollToTop(lazyGridState = lazyGridState)

            val showFloatingIcon by Preferences.SHOW_FLOATING_ICON.collectAsStateWithLifecycle()
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

