package app.kreate.android.themed.rimusic.screen.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastMap
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import app.kreate.android.Preferences
import app.kreate.android.themed.common.component.tab.DeleteAllDownloadedDialog
import app.kreate.android.themed.common.component.tab.DownloadAllDialog
import app.kreate.android.themed.rimusic.component.ItemSelector
import app.kreate.android.themed.rimusic.component.Search
import app.kreate.android.themed.rimusic.component.song.PeriodSelector
import app.kreate.android.themed.rimusic.component.song.SongItem
import app.kreate.android.themed.rimusic.component.tab.Sort
import app.kreate.constant.SongSortBy
import app.kreate.database.extension.FormatWithSong
import app.kreate.database.models.Song
import app.kreate.util.toDuration
import it.fast4x.compose.persist.persistList
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.BuiltInPlaylist
import it.fast4x.rimusic.enums.DurationInMinutes
import it.fast4x.rimusic.service.MyDownloadHelper
import it.fast4x.rimusic.service.modern.isLocal
import it.fast4x.rimusic.thumbnailShape
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import it.fast4x.rimusic.ui.components.tab.toolbar.Button
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.ui.styling.onOverlay
import it.fast4x.rimusic.ui.styling.overlay
import it.fast4x.rimusic.utils.DisposableListener
import it.fast4x.rimusic.utils.addNext
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.center
import it.fast4x.rimusic.utils.color
import it.fast4x.rimusic.utils.enqueue
import it.fast4x.rimusic.utils.forcePlayAtIndex
import it.fast4x.rimusic.utils.isDownloadedSong
import it.fast4x.rimusic.utils.manageDownload
import it.fast4x.rimusic.utils.semiBold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import me.knighthat.component.tab.ExportSongsToCSVDialog
import me.knighthat.component.tab.HiddenSongs

@UnstableApi
@ExperimentalFoundationApi
@Composable
fun HomeSongs(
    navController: NavController,
    builtInPlaylist: BuiltInPlaylist,
    lazyListState: LazyListState,
    itemSelector: ItemSelector<Song>,
    search: Search,
    buttons: MutableList<Button>,
    itemsOnDisplay: MutableList<Song>,
    getSongs: () -> List<Song>,
) {
    // Essentials
    val binder = LocalPlayerServiceBinder.current ?: return
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val hapticFeedback = LocalHapticFeedback.current
    val (colorPalette, typography) = LocalAppearance.current

    //<editor-fold defaultstate="collapsed" desc="Settings">
    val parentalControlEnabled by Preferences.PARENTAL_CONTROL
    val maxTopPlaylistItems by Preferences.MAX_NUMBER_OF_TOP_PLAYED
    val includeLocalSongs by Preferences.HOME_SONGS_INCLUDE_ON_DEVICE_IN_ALL
    val excludeSongWithDurationLimit by Preferences.LIMIT_SONGS_WITH_DURATION
    //</editor-fold>

    var items by persistList<Song>( "home/songs" )

    val songSort = remember {
        Sort(menuState, Preferences.HOME_SONGS_SORT_BY, Preferences.HOME_SONGS_SORT_ORDER)
    }
    val topPlaylists = remember { PeriodSelector(menuState) }
    val hiddenSongs = HiddenSongs()
    val exportDialog = ExportSongsToCSVDialog(
        playlistName = builtInPlaylist.text,
        songs = getSongs
    )
    val downloadAllDialog = remember {
        DownloadAllDialog( binder, context, getSongs )
    }
    val deleteDownloadsDialog = remember {
        DeleteAllDownloadedDialog( binder, context, getSongs )
    }

    /**
     * This variable tells [LazyColumn] to render [SongItemPlaceholder]
     * instead of [SongItem] queried from the database.
     *
     * This indication also tells user that songs are being loaded
     * and not it's definitely not freezing up.
     *
     * > This variable should **_NOT_** be set to `false` while inside **first** phrase,
     * and should **_NOT_** be set to `true` while in **second** phrase.
     */
    var isLoading by rememberSaveable { mutableStateOf(false) }

    // This phrase loads all songs across types into [items]
    // No filtration applied to this stage, only sort
    LaunchedEffect( builtInPlaylist, topPlaylists.period, songSort.sortBy, songSort.sortOrder, hiddenSongs.isFirstIcon ) {
        isLoading = true

        val retrievedSongs = when( builtInPlaylist ) {
            BuiltInPlaylist.All -> Database.songTable
                                           .sortAll( songSort.sortBy, songSort.sortOrder, excludeHidden = hiddenSongs.isHiddenExcluded() )
                                           .map { list ->
                                               // Include local songs if enabled
                                               list.fastFilter {
                                                   !includeLocalSongs || !it.isLocal
                                               }
                                           }

            BuiltInPlaylist.Downloaded -> {
                // [MyDownloadHelper] provide a list of downloaded songs, which is faster to retrieve
                // than using `Cache.isCached()` call
                val downloaded: List<String> = MyDownloadHelper.instance
                                                               .downloads
                                                               .value
                                                               .values
                                                               .filter { it.state == Download.STATE_COMPLETED }
                                                               .fastMap { it.request.id }
                Database.songTable
                        .sortAll( songSort.sortBy, songSort.sortOrder )
                        .map { list ->
                            list.fastFilter { it.id in downloaded }
                        }
            }

            BuiltInPlaylist.Offline -> Database.formatTable
                                               .sortAllWithSongs( songSort.sortBy, songSort.sortOrder, excludeHidden = hiddenSongs.isHiddenExcluded() )
                                               .map { list ->
                                                   list.fastFilter {
                                                       val contentLength = it.format.contentLength ?: return@fastFilter false
                                                       binder?.cache?.isCached( it.song.id, 0, contentLength ) == true
                                                   }.map( FormatWithSong::song )
                                               }

            BuiltInPlaylist.Favorites -> Database.songTable.sortFavorites( songSort.sortBy, songSort.sortOrder )

            BuiltInPlaylist.Top -> Database.eventTable
                                           .findSongsMostPlayedBetween(
                                               from = topPlaylists.period.timeStampInMillis(),
                                               limit = maxTopPlaylistItems.toInt()
                                           )
                                           .map { list ->
                                               // Exclude songs with duration higher than what [excludeSongWithDurationLimit] is
                                               list.fastFilter { song ->
                                                   excludeSongWithDurationLimit == DurationInMinutes.Disabled
                                                           || song.durationText.toDuration().inWholeMilliseconds < excludeSongWithDurationLimit.asMillis
                                               }
                                           }

            BuiltInPlaylist.OnDevice -> flowOf( emptyList() )
        }

        retrievedSongs.flowOn( Dispatchers.IO )
                      .distinctUntilChanged()
                      // Scroll list to top to prevent weird artifacts
                      .onEach { lazyListState.scrollToItem( 0, 0 ) }
                      .collect { items = it }
    }

    LaunchedEffect( items, search.input ) {
    items.filter { !parentalControlEnabled || !it.isExplicit }
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

            isLoading = false
        }
    }

    LaunchedEffect( builtInPlaylist ) {
        val firstButton = if( builtInPlaylist == BuiltInPlaylist.Top ) topPlaylists else songSort
        buttons.add( 0, firstButton )
        buttons.add( 3, downloadAllDialog )
        buttons.add( 4, deleteDownloadsDialog )
        buttons.add( exportDialog )
    }

    //<editor-fold defaultstate="collapsed" desc="Dialog Renders">
    exportDialog.Render()
    downloadAllDialog.Render()
    deleteDownloadsDialog.Render()
    //</editor-fold>

    var currentlyPlaying by remember { mutableStateOf(binder.player.currentMediaItem?.mediaId) }
    binder.player.DisposableListener {
        object : Player.Listener {
            override fun onMediaItemTransition( mediaItem: MediaItem?, reason: Int ) {
                currentlyPlaying = mediaItem?.mediaId
            }
        }
    }
    val songItemValues = remember( colorPalette, typography ) {
        SongItem.Values.from( colorPalette, typography )
    }

    LazyColumn(
        state = lazyListState,
        userScrollEnabled = !isLoading,
        contentPadding = PaddingValues( bottom = Dimensions.bottomSpacer )
    ) {
        if( isLoading )
            items( 20, null ) { SongItem.Placeholder() }

        itemsIndexed(
            items = itemsOnDisplay,
            key = { _, song -> song.id }
        ) { index, song ->
            val mediaItem = song.asMediaItem

            val isLocal by remember { derivedStateOf { mediaItem.isLocal } }
            val isDownloaded = isLocal || isDownloadedSong( mediaItem.mediaId )

            SwipeablePlaylistItem(
                mediaItem = mediaItem,
                onPlayNext = { binder.player.addNext( song ) },
                onDownload = {
                    if( builtInPlaylist != BuiltInPlaylist.OnDevice ) {
                        binder?.cache?.removeResource(mediaItem.mediaId)
                        Database.asyncTransaction {
                            formatTable.updateContentLengthOf( mediaItem.mediaId )
                        }
                        if ( !isLocal )
                            manageDownload(
                                context = context,
                                mediaItem = mediaItem,
                                downloadState = isDownloaded
                            )
                    }
                },
                onEnqueue = { binder.player.enqueue( song ) }
            ) {
                SongItem.Render(
                    song = song,
                    context = context,
                    binder = binder,
                    hapticFeedback = hapticFeedback,
                    isPlaying = song.id == currentlyPlaying,
                    values = songItemValues,
                    itemSelector = itemSelector,
                    navController = navController,
                    modifier = Modifier.animateItem(),
                    thumbnailOverlay = {
                        if ( songSort.sortBy == SongSortBy.TOTAL_PLAY_TIME || builtInPlaylist == BuiltInPlaylist.Top ) {
                            var text = song.formattedTotalPlayTime
                            var typography = typography().xxs
                            var alignment = Alignment.BottomCenter

                            if( builtInPlaylist == BuiltInPlaylist.Top ) {
                                text = (index + 1).toString()
                                typography = typography().m
                                alignment = Alignment.Center
                            }

                            BasicText(
                                text = text,
                                style = typography.semiBold.center.color(colorPalette().onOverlay),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .align(alignment)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                colorPalette().overlay
                                            )
                                        ),
                                        shape = thumbnailShape()
                                    )
                            )
                        }
                    },
                    onClick = {
                        search.hideIfEmpty()

                        binder.stopRadio()

                        val selectedSongs = getSongs()
                        if( song in selectedSongs )
                            binder.player.forcePlayAtIndex( selectedSongs, selectedSongs.indexOf( song ) )
                        else
                            binder.player.forcePlayAtIndex( itemsOnDisplay, index )
                    }
                )
            }
        }
    }
}