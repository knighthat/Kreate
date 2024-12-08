package it.fast4x.rimusic.ui.screens.home


import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import it.fast4x.compose.persist.persistList
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.R
import it.fast4x.rimusic.enums.ArtistSortBy
import it.fast4x.rimusic.enums.ArtistsType
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.enums.UiType
import it.fast4x.rimusic.models.Artist
import it.fast4x.rimusic.models.Song
import it.fast4x.rimusic.ui.components.ButtonsRow
import it.fast4x.rimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import it.fast4x.rimusic.ui.components.themed.HeaderInfo
import it.fast4x.rimusic.ui.components.themed.MultiFloatingActionsContainer
import it.fast4x.rimusic.ui.items.ArtistItem
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.utils.artistSortByKey
import it.fast4x.rimusic.utils.artistSortOrderKey
import it.fast4x.rimusic.utils.artistTypeKey
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.disableScrollingTextKey
import it.fast4x.rimusic.utils.rememberPreference
import it.fast4x.rimusic.utils.showFloatingIconKey
import kotlinx.coroutines.flow.map
import me.knighthat.colorPalette
import me.knighthat.component.Search
import me.knighthat.component.header.TabToolBar
import me.knighthat.component.tab.ItemSize
import me.knighthat.component.tab.Sort
import me.knighthat.component.tab.TabHeader
import me.knighthat.component.tab.toolbar.Randomizer
import me.knighthat.component.tab.toolbar.SongsShuffle
import me.knighthat.preference.Preference.HOME_ARTIST_ITEM_SIZE

@ExperimentalMaterial3Api
@UnstableApi
@ExperimentalMaterialApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@Composable
fun HomeArtists(
    onArtistClick: (Artist) -> Unit,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    // Essentials
    val lazyGridState = rememberLazyGridState()

    var items by persistList<Artist>( "home/artists" )

    var itemsOnDisplay by persistList<Artist>( "home/artists/on_display" )

    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    val search = Search.init()

    val sort = Sort.init(
        artistSortOrderKey,
        ArtistSortBy.entries,
        rememberPreference(artistSortByKey, ArtistSortBy.DateAdded)
    )

    val itemSize = ItemSize.init( HOME_ARTIST_ITEM_SIZE )

    val randomizer = object: Randomizer<Artist> {
        override fun getItems(): List<Artist> = itemsOnDisplay
        override fun onClick(index: Int) = onArtistClick(itemsOnDisplay[index])

    }
    val shuffle = SongsShuffle.init {
        Database.songsInAllFollowedArtists().map{ it.map( Song::asMediaItem ) }
    }

    var artistType by rememberPreference(artistTypeKey, ArtistsType.Favorites )
    val buttonsList = ArtistsType.entries.map { it to it.text }


    LaunchedEffect( sort.sortBy, sort.sortOrder, artistType ) {
        when( artistType ) {
            ArtistsType.Favorites -> Database.artists( sort.sortBy, sort.sortOrder ).collect { items = it }
            ArtistsType.Library -> Database.artistsInLibrary( sort.sortBy, sort.sortOrder ).collect { items = it }
            //ArtistsType.All -> Database.artistsWithSongsSaved( sort.sortBy, sort.sortOrder ).collect { items = it }
        }

    }
    LaunchedEffect( items, search.input ) {
        val scrollIndex = lazyGridState.firstVisibleItemIndex
        val scrollOffset = lazyGridState.firstVisibleItemScrollOffset

        itemsOnDisplay = items.filter {
            it.name?.contains( search.input, true ) ?: false
        }

        lazyGridState.scrollToItem( scrollIndex, scrollOffset )
    }

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
                HeaderInfo(items.size.toString(), R.drawable.artists)
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
                ButtonsRow(
                    chips = buttonsList,
                    currentValue = artistType,
                    onValueUpdate = { artistType = it } ,
                    modifier = Modifier.padding(end = 12.dp)
                )
            }

            // Sticky search bar
            search.SearchBar( this )

            LazyVerticalGrid(
                state = lazyGridState,
                columns = GridCells.Adaptive( itemSize.size.dp ),
                modifier = Modifier.background( colorPalette().background0 )
                                   .fillMaxSize(),
                contentPadding = PaddingValues( bottom = Dimensions.bottomSpacer )
            ) {
                items(items = itemsOnDisplay, key = Artist::id) { artist ->
                    ArtistItem(
                        artist = artist,
                        thumbnailSizeDp = itemSize.size.dp,
                        thumbnailSizePx = itemSize.size.px,
                        alternative = true,
                        modifier = Modifier.animateItem( fadeInSpec = null, fadeOutSpec = null )
                                           .clickable(onClick = {
                                               search.onItemSelected()
                                               onArtistClick( artist )
                                           }),
                        disableScrollingText = disableScrollingText
                    )
                }
            }
        }

        FloatingActionsContainerWithScrollToTop(lazyGridState = lazyGridState)

        val showFloatingIcon by rememberPreference(showFloatingIconKey, false)
        if( UiType.ViMusic.isCurrent() && showFloatingIcon )
            MultiFloatingActionsContainer(
                iconId = R.drawable.search,
                onClick = onSearchClick,
                onClickSettings = onSettingsClick,
                onClickSearch = onSearchClick
            )
    }
}
