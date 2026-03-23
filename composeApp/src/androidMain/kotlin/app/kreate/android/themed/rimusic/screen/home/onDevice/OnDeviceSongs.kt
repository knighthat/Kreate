package app.kreate.android.themed.rimusic.screen.home.onDevice

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.util.fastMap
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.Preferences
import app.kreate.android.service.player.StatefulPlayer
import app.kreate.android.themed.rimusic.component.ItemSelector
import app.kreate.android.themed.rimusic.component.Search
import app.kreate.android.themed.rimusic.component.song.SongItem
import app.kreate.android.themed.rimusic.component.tab.Sort
import app.kreate.android.utils.shallowCompare
import app.kreate.android.viewmodel.home.OnDeviceSongsViewModel
import app.kreate.database.models.Song
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import it.fast4x.rimusic.ui.components.tab.toolbar.Button
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.addNext
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.enqueue
import it.fast4x.rimusic.utils.forcePlayAtIndex
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach
import me.knighthat.component.FolderItem
import me.knighthat.utils.PathUtils
import me.knighthat.utils.getLocalSongs
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@UnstableApi
@ExperimentalFoundationApi
@Composable
fun OnDeviceSong(
    navController: NavController,
    lazyListState: LazyListState,
    itemSelector: ItemSelector<Song>,
    search: Search,
    buttons: MutableList<Button>,
    itemsOnDisplay: MutableList<Song>,
    getSongs: () -> List<Song>,
) {
    // Essentials
    val context = LocalContext.current
    val player: StatefulPlayer = koinInject()
    val viewModel: OnDeviceSongsViewModel = koinViewModel()
    val (colorPalette, typography) = LocalAppearance.current
    val hapticFeedback = LocalHapticFeedback.current
    val menuState = LocalMenuState.current

    if( !viewModel.isPermissionGranted ) {
        RequestMediaPermissionScreen( viewModel )
        return
    }

    //<editor-fold defaultstate="collapsed" desc="Settings">
    val parentalControlEnabled by Preferences.PARENTAL_CONTROL
    val showFolder4LocalSongs by Preferences.HOME_SONGS_ON_DEVICE_SHOW_FOLDERS
    //</editor-fold>

    val odSort = remember {
        Sort(menuState, Preferences.HOME_ON_DEVICE_SONGS_SORT_BY, Preferences.HOME_SONGS_SORT_ORDER)
    }
    var songsOnDevice by remember {
        mutableStateOf( emptyMap<Song, String>() )
    }
    var currentPath by remember( songsOnDevice.values ) {
        mutableStateOf( PathUtils.findCommonPath( songsOnDevice.values ) )
    }
    LaunchedEffect( Unit ) {
        buttons.add( 0, odSort )
    }
    LaunchedEffect( odSort.sortBy, odSort.sortOrder ) {
        context.getLocalSongs( odSort.sortBy, odSort.sortOrder )
               .distinctUntilChanged()
               .onEach { lazyListState.scrollToItem( 0, 0 ) }
               .collect {
                   songsOnDevice = it
               }
    }
    LaunchedEffect( songsOnDevice, search.input, currentPath ) {
        songsOnDevice.keys.filter { !parentalControlEnabled || !it.isExplicit }
                          .filter {
                              // [showFolder4LocalSongs] must be false and
                              // this song must be inside [currentPath] to show song
                              !showFolder4LocalSongs
                                      || currentPath.equals( songsOnDevice[it], true )
                                      || "$currentPath/".equals( songsOnDevice[it], true )
                          }
                          .filter {
                              // Without cleaning, user can search explicit songs with "e:"
                              // I kinda want this to be a feature, but it seems unnecessary
                              val containsTitle = search appearsIn it.cleanTitle()
                              val containsArtist = search appearsIn it.cleanArtistsText()

                              containsTitle || containsArtist
                          }
                          .let {
                              itemsOnDisplay.clear()
                              itemsOnDisplay.addAll( it )
                          }
    }

    val currentMediaItem by player.currentMediaItemState.collectAsState()
    val songItemValues = remember( colorPalette, typography ) {
        SongItem.Values.from( colorPalette, typography )
    }

    LazyColumn(
        state = lazyListState,
        userScrollEnabled = songsOnDevice.isNotEmpty(),
        contentPadding = PaddingValues( bottom = Dimensions.bottomSpacer )
    ) {
        if( showFolder4LocalSongs && songsOnDevice.isNotEmpty() ) {
            item( "folder_paths" ) {
                PathUtils.AddressBar(
                    paths = songsOnDevice.values,
                    currentPath = currentPath,
                    onSpecificAddressClick = { currentPath = it }
                )
            }

            items(
                items = PathUtils.getAvailablePaths( songsOnDevice.values, currentPath ),
                key = { it }
            ) {
                FolderItem( it ) { currentPath += "/$it" }
            }
        }

        itemsIndexed(
            items = itemsOnDisplay,
            key = { _, song -> song.id }
        ) { index, song ->
            val mediaItem = song.asMediaItem

            SwipeablePlaylistItem(
                mediaItem = mediaItem,
                onPlayNext = { player.addNext( mediaItem ) },
                onEnqueue = {
                    player.enqueue(mediaItem)
                }
            ) {
                SongItem.Render(
                    song = song,
                    hapticFeedback = hapticFeedback,
                    isPlaying = song.shallowCompare( currentMediaItem ),
                    values = songItemValues,
                    itemSelector = itemSelector,
                    navController = navController,
                    modifier = Modifier.animateItem(),
                    onClick = {
                        search.hideIfEmpty()

                        player.stopRadio()

                        val selectedSongs = getSongs()
                        if( song in selectedSongs )
                            player.forcePlayAtIndex(
                                selectedSongs.fastMap( Song::asMediaItem ),
                                selectedSongs.indexOf( song )
                            )
                        else
                            player.forcePlayAtIndex(
                                itemsOnDisplay.fastMap( Song::asMediaItem ),
                                index
                            )
                    }
                )
            }
        }
    }
}