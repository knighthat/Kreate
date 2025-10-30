package app.kreate.android.themed.rimusic.screen.home

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.common.component.settings.SettingComponents
import app.kreate.android.themed.common.component.settings.data.ExoCacheIndicator
import app.kreate.android.themed.rimusic.component.ItemSelector
import app.kreate.android.themed.rimusic.component.Search
import app.kreate.android.themed.rimusic.screen.home.onDevice.OnDeviceSong
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.BuiltInPlaylist
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.enums.UiType
import it.fast4x.rimusic.models.Song
import it.fast4x.rimusic.ui.components.ButtonsRow
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import it.fast4x.rimusic.ui.components.tab.TabHeader
import it.fast4x.rimusic.ui.components.tab.toolbar.Button
import it.fast4x.rimusic.ui.components.themed.Enqueue
import it.fast4x.rimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import it.fast4x.rimusic.ui.components.themed.HeaderInfo
import it.fast4x.rimusic.ui.components.themed.MultiFloatingActionsContainer
import it.fast4x.rimusic.ui.components.themed.PlayNext
import it.fast4x.rimusic.ui.components.themed.PlaylistsMenu
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.utils.addNext
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.enqueue
import me.knighthat.component.ResetCache
import me.knighthat.component.tab.ImportSongsFromCSV
import me.knighthat.component.tab.LikeComponent
import me.knighthat.component.tab.Locator
import me.knighthat.component.tab.SongShuffler
import timber.log.Timber

@UnstableApi
@ExperimentalMaterial3Api
@ExperimentalAnimationApi
@ExperimentalFoundationApi
@Composable
fun HomeSongsScreen(navController: NavController ) {
    // Essentials
    val binder = LocalPlayerServiceBinder.current ?: return
    val lazyListState = rememberLazyListState()
    val menuState = LocalMenuState.current

    var builtInPlaylist by Preferences.HOME_SONGS_TYPE

    val itemsOnDisplayState = remember { mutableStateListOf<Song>() }

    val itemSelector = remember {
        ItemSelector( menuState ) { addAll( itemsOnDisplayState ) }
    }
    fun getSongs() = itemSelector.ifEmpty { itemsOnDisplayState }.toList()
    fun getMediaItems() = getSongs().map( Song::asMediaItem )

    val search = remember { Search(lazyListState) }
    val locator = Locator( lazyListState, ::getSongs )
    val import = ImportSongsFromCSV()
    val shuffle = SongShuffler(::getSongs)
    val playNext = PlayNext {
        getSongs().also( binder.player::addNext )

        // Turn of selector clears the selected list
        itemSelector.isActive = false
    }
    val enqueue = Enqueue {
        getSongs().also( binder.player::enqueue )

        // Turn of selector clears the selected list
        itemSelector.isActive = false
    }
    val addToFavorite = LikeComponent(::getSongs)
    val addToPlaylist = PlaylistsMenu.init(
        navController = navController,
        mediaItems = { _ -> getMediaItems() },
        onFailure = { throwable, preview ->
            Timber.e( "Failed to add songs to playlist ${preview.playlist.name} on HomeSongs" )
            throwable.printStackTrace()
        },
        finalAction = {
            // Turn of selector clears the selected list
            itemSelector.isActive = false
        }
    )
    val resetCache = ResetCache( ::getSongs )

    val buttons = remember( builtInPlaylist ) {
        // Disable checkboxes when category has changed
        itemSelector.isActive = false

        mutableStateListOf<Button>() .apply {
            this.add( search )
            this.add( locator )
            this.add( shuffle )
            this.add( itemSelector )
            this.add( playNext )
            this.add( enqueue )
            this.add( addToFavorite )
            this.add( addToPlaylist )
            this.add( import )
            if( builtInPlaylist != BuiltInPlaylist.OnDevice )
                this.add( resetCache )
        }
    }

    Box(
        modifier = Modifier.background( colorPalette().background0 )
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
            TabHeader( R.string.songs ) {
                HeaderInfo( itemsOnDisplayState.size.toString(), R.drawable.musical_notes )
            }

            // Sticky tab's tool bar
            TabToolBar.Buttons( buttons )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding( horizontal = 12.dp )
                    .padding( bottom = 8.dp )
                    .fillMaxWidth()
            ) {
                Column {
                    //<editor-fold defaultstate="collapsed" desc="Chips">
                    val showFavoritesPlaylist by Preferences.HOME_SONGS_SHOW_FAVORITES_CHIP
                    val showCachedPlaylist by Preferences.HOME_SONGS_SHOW_CACHED_CHIP
                    val showDownloadedPlaylist by Preferences.HOME_SONGS_SHOW_DOWNLOADED_CHIP
                    val showMyTopPlaylist by Preferences.HOME_SONGS_SHOW_MOST_PLAYED_CHIP
                    val showOnDevice by Preferences.HOME_SONGS_SHOW_ON_DEVICE_CHIP
                    val chips = remember( showFavoritesPlaylist, showCachedPlaylist, showMyTopPlaylist, showDownloadedPlaylist) {
                        buildList {
                            add( BuiltInPlaylist.All )
                            if( showFavoritesPlaylist )
                                add( BuiltInPlaylist.Favorites )
                            if( showCachedPlaylist )
                                add( BuiltInPlaylist.Offline )
                            if( showDownloadedPlaylist )
                                add( BuiltInPlaylist.Downloaded )
                            if( showMyTopPlaylist )
                                add( BuiltInPlaylist.Top )
                            if( showOnDevice )
                                add( BuiltInPlaylist.OnDevice )
                        }
                    }
                    //</editor-fold>

                    ButtonsRow(
                        chips = chips,
                        currentValue = builtInPlaylist,
                        onValueUpdate = { builtInPlaylist = it }
                    )

                    when (builtInPlaylist) {
                        BuiltInPlaylist.Downloaded, BuiltInPlaylist.Offline -> {
                            val indicator = remember( builtInPlaylist, binder.cache ) {
                                val preference = if( builtInPlaylist === BuiltInPlaylist.Downloaded )
                                    Preferences.EXO_DOWNLOAD_SIZE
                                else
                                    Preferences.EXO_CACHE_SIZE

                                ExoCacheIndicator(preference, binder.cache)
                            }
                            indicator.ProgressBar(
                                Modifier.padding(
                                            horizontal = SettingComponents.HORIZONTAL_PADDING.dp,
                                            vertical = 12.dp
                                        )
                                        .fillMaxWidth()
                            )
                        }
                        else -> {}
                    }

                }
            }

            // Sticky search bar
            search.SearchBar()

            when( builtInPlaylist ) {
                BuiltInPlaylist.OnDevice -> OnDeviceSong( navController, lazyListState, itemSelector, search, buttons, itemsOnDisplayState, ::getSongs )
                else                     -> HomeSongs( navController, builtInPlaylist, lazyListState, itemSelector, search, buttons, itemsOnDisplayState, ::getSongs )
            }
        }

        FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)

        val showFloatingIcon by Preferences.SHOW_FLOATING_ICON
        if( UiType.ViMusic.isCurrent() && showFloatingIcon )
            MultiFloatingActionsContainer(
                iconId = R.drawable.search,
                onClick = {
                    NavRoutes.search.navigateHere( navController )
                },
                onClickSettings = {
                    NavRoutes.settings.navigateHere( navController )
                },
                onClickSearch = {
                    NavRoutes.search.navigateHere( navController )
                }
            )
    }
}