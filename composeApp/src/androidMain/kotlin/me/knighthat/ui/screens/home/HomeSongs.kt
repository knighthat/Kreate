package me.knighthat.ui.screens.home

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import app.kreate.android.R
import it.fast4x.compose.persist.persistList
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.EXPLICIT_PREFIX
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.BuiltInPlaylist
import it.fast4x.rimusic.enums.CacheType
import it.fast4x.rimusic.enums.DurationInMinutes
import it.fast4x.rimusic.enums.MaxTopPlaylistItems
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.enums.OnDeviceSongSortBy
import it.fast4x.rimusic.enums.SongSortBy
import it.fast4x.rimusic.enums.UiType
import it.fast4x.rimusic.models.Format
import it.fast4x.rimusic.models.Song
import it.fast4x.rimusic.service.LOCAL_KEY_PREFIX
import it.fast4x.rimusic.service.MyDownloadHelper
import it.fast4x.rimusic.service.isLocal
import it.fast4x.rimusic.thumbnailShape
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.ButtonsRow
import it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import it.fast4x.rimusic.ui.components.tab.Sort
import it.fast4x.rimusic.ui.components.tab.TabHeader
import it.fast4x.rimusic.ui.components.tab.toolbar.Button
import it.fast4x.rimusic.ui.components.themed.CacheSpaceIndicator
import it.fast4x.rimusic.ui.components.themed.Enqueue
import it.fast4x.rimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import it.fast4x.rimusic.ui.components.themed.HeaderInfo
import it.fast4x.rimusic.ui.components.themed.MultiFloatingActionsContainer
import it.fast4x.rimusic.ui.components.themed.PlayNext
import it.fast4x.rimusic.ui.components.themed.PlaylistsMenu
import it.fast4x.rimusic.ui.components.themed.Search
import it.fast4x.rimusic.ui.items.SongItemPlaceholder
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.onOverlay
import it.fast4x.rimusic.ui.styling.overlay
import it.fast4x.rimusic.utils.MaxTopPlaylistItemsKey
import it.fast4x.rimusic.utils.Preference
import it.fast4x.rimusic.utils.addNext
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.builtInPlaylistKey
import it.fast4x.rimusic.utils.center
import it.fast4x.rimusic.utils.color
import it.fast4x.rimusic.utils.durationTextToMillis
import it.fast4x.rimusic.utils.enqueue
import it.fast4x.rimusic.utils.excludeSongsWithDurationLimitKey
import it.fast4x.rimusic.utils.forcePlayAtIndex
import it.fast4x.rimusic.utils.includeLocalSongsKey
import it.fast4x.rimusic.utils.isDownloadedSong
import it.fast4x.rimusic.utils.manageDownload
import it.fast4x.rimusic.utils.onDeviceSongSortByKey
import it.fast4x.rimusic.utils.operatorFilterSong
import it.fast4x.rimusic.utils.parentalControlEnabledKey
import it.fast4x.rimusic.utils.rememberPreference
import it.fast4x.rimusic.utils.semiBold
import it.fast4x.rimusic.utils.showCachedPlaylistKey
import it.fast4x.rimusic.utils.showDownloadedPlaylistKey
import it.fast4x.rimusic.utils.showFavoritesPlaylistKey
import it.fast4x.rimusic.utils.showFloatingIconKey
import it.fast4x.rimusic.utils.showFoldersOnDeviceKey
import it.fast4x.rimusic.utils.showMyTopPlaylistKey
import it.fast4x.rimusic.utils.songSortByKey
import it.fast4x.rimusic.utils.songSortOrderKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import me.knighthat.component.FolderItem
import me.knighthat.component.ResetCache
import me.knighthat.component.SongItem
import me.knighthat.component.song.PeriodSelector
import me.knighthat.component.tab.DeleteAllDownloadedSongsDialog
import me.knighthat.component.tab.DeleteHiddenSongsDialog
import me.knighthat.component.tab.DeleteSongDialog
import me.knighthat.component.tab.DownloadAllSongsDialog
import me.knighthat.component.tab.ExportSongsToCSVDialog
import me.knighthat.component.tab.HiddenSongs
import me.knighthat.component.tab.HideSongDialog
import me.knighthat.component.tab.ImportSongsFromCSV
import me.knighthat.component.tab.ItemSelector
import me.knighthat.component.tab.LikeComponent
import me.knighthat.component.tab.Locator
import me.knighthat.component.tab.SongShuffler
import me.knighthat.database.ext.FormatWithSong
import me.knighthat.utils.PathUtils
import me.knighthat.utils.getLocalSongs
import timber.log.Timber

@OptIn(
    ExperimentalAnimationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
)
@UnstableApi
@Composable
fun HomeSongs( navController: NavController ) {
    // Essentials
    val binder = LocalPlayerServiceBinder.current
    val context = LocalContext.current
    val lazyListState = rememberLazyListState()

    //<editor-fold defaultstate="collapsed" desc="Settings">
    val parentalControlEnabled by rememberPreference( parentalControlEnabledKey, false )
    var builtInPlaylist by rememberPreference( builtInPlaylistKey, BuiltInPlaylist.Favorites )
    val includeLocalSongs by rememberPreference( includeLocalSongsKey, true )
    val maxTopPlaylistItems by rememberPreference( MaxTopPlaylistItemsKey, MaxTopPlaylistItems.`10` )
    val excludeSongWithDurationLimit by rememberPreference( excludeSongsWithDurationLimitKey, DurationInMinutes.Disabled )
    val odsSortBy by rememberPreference( onDeviceSongSortByKey, OnDeviceSongSortBy.DateAdded )
    val showFolder4LocalSongs by rememberPreference( showFoldersOnDeviceKey, false )
    //</editor-fold>

    var items by persistList<Song>( "home/songs" )
    var itemsOnDisplay by persistList<Song>( "home/songs/on_display" )
    val formats by remember( builtInPlaylist ) {
        if( builtInPlaylist != BuiltInPlaylist.Offline )
            return@remember emptyFlow<List<Format>>()

        Database.formatTable
                .allWithSongs()
                .distinctUntilChanged()
                .map { list ->
                    list.map( FormatWithSong::format )
                }
    }.collectAsState( emptyList(), Dispatchers.IO )

    var songsOnDevice by remember( builtInPlaylist ) {
        mutableStateOf( emptyMap<Song, String>() )
    }
    var currentPath by remember( songsOnDevice.values ) {
        mutableStateOf( PathUtils.findCommonPath( songsOnDevice.values ) )
    }

    fun getSongs() = itemsOnDisplay.ifEmpty { items }
    fun getMediaItems() = getSongs().map( Song::asMediaItem )

    //<editor-fold defaultstate="collapsed" desc="Chips">
    val showFavoritesPlaylist by rememberPreference( showFavoritesPlaylistKey, true )
    val showCachedPlaylist by rememberPreference( showCachedPlaylistKey, true )
    val showMyTopPlaylist by rememberPreference( showMyTopPlaylistKey, true )
    val showDownloadedPlaylist by rememberPreference( showDownloadedPlaylistKey, true )

    val buttonsList = remember( showFavoritesPlaylist, showCachedPlaylist, showMyTopPlaylist, showDownloadedPlaylist) {
        mutableListOf<BuiltInPlaylist>().apply {
            add( BuiltInPlaylist.All )

            if( showFavoritesPlaylist )
                add( BuiltInPlaylist.Favorites )

            if( showCachedPlaylist )
                add( BuiltInPlaylist.Offline )

            if( showDownloadedPlaylist )
                add( BuiltInPlaylist.Downloaded )

            if( showMyTopPlaylist )
                add( BuiltInPlaylist.Top )

            add( BuiltInPlaylist.OnDevice )
        }.toList()
    }
    //</editor-fold>
    //<editor-fold desc="Toolbar buttons">
    val songSort = Sort.init(
        songSortOrderKey,
        SongSortBy.entries,
        rememberPreference(songSortByKey, SongSortBy.DateAdded)
    )
    val topPlaylists = PeriodSelector( Preference.HOME_SONGS_TOP_PLAYLIST_PERIOD )
    val hiddenSongs = HiddenSongs()
    val search = Search.init()
    val hideSongDialog = HideSongDialog()
    val itemSelector = ItemSelector<Song>()
    val exportDialog = ExportSongsToCSVDialog(
        playlistName = builtInPlaylist.text,
        songs = ::getSongs
    )
    val downloadAllDialog = DownloadAllSongsDialog( ::getSongs )
    val deleteDownloadsDialog = DeleteAllDownloadedSongsDialog( ::getSongs )
    val deleteSongDialog = DeleteSongDialog()
    val deleteHiddenSongs = DeleteHiddenSongsDialog()
    val locator = Locator( lazyListState, ::getSongs )
    val shuffle = SongShuffler( ::getSongs )
    val playNext = PlayNext {
        binder?.player?.addNext( getMediaItems(), appContext() )

        // Turn of selector clears the selected list
        itemSelector.isActive = false
    }
    val enqueue = Enqueue {
        binder?.player?.enqueue( getMediaItems(), appContext() )

        // Turn of selector clears the selected list
        itemSelector.isActive = false
    }
    val addToFavorite = LikeComponent( ::getSongs )
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
    val import = ImportSongsFromCSV()
    val resetCache = ResetCache( ::getSongs )
    //</editor-fold>

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
    LaunchedEffect( builtInPlaylist, topPlaylists.period, odsSortBy, songSort.sortBy, songSort.sortOrder, hiddenSongs.isShown() ) {
        isLoading = true
        items = emptyList()

        when( builtInPlaylist ) {
            BuiltInPlaylist.All, BuiltInPlaylist.Offline, BuiltInPlaylist.Downloaded ->
                Database.songTable.sortAll( songSort.sortBy, songSort.sortOrder, excludeHidden = hiddenSongs.isShown() )

            BuiltInPlaylist.Favorites -> Database.songTable.sortFavorites( songSort.sortBy, songSort.sortOrder )

            BuiltInPlaylist.Top -> Database.eventTable.findSongsMostPlayedBetween(
                from = topPlaylists.period.timeStampInMillis(),
                limit = maxTopPlaylistItems.toInt()
            )

            BuiltInPlaylist.OnDevice -> context.getLocalSongs( odsSortBy, songSort.sortOrder )
                                               .map {
                                                   songsOnDevice = it
                                                   it.keys.toList()
                                               }
        }.flowOn( Dispatchers.IO ).distinctUntilChanged().collect { items = it }
    }

    // This phrase will filter out songs depends on search inputs, and natural filter
    // parameters, such as get downloaded songs when [BuiltInPlaylist.Offline] is set.
    fun naturalFilter( song: Song ): Boolean =
        when( builtInPlaylist ) {
            BuiltInPlaylist.All -> !includeLocalSongs || !song.id.startsWith( LOCAL_KEY_PREFIX )

            BuiltInPlaylist.Offline -> runBlocking( Dispatchers.IO ) {
                val contentLength = formats.firstOrNull { it.songId == song.id }?.contentLength
                contentLength != null && binder?.cache?.isCached( song.id, 0, contentLength ) ?: false
            }

            BuiltInPlaylist.Downloaded -> MyDownloadHelper.downloads
                                                          .value
                                                          .values
                                                          .filter { it.state == Download.STATE_COMPLETED }
                                                          .any {
                                                              it.request.id == song.id
                                                          }

            BuiltInPlaylist.Top ->
                excludeSongWithDurationLimit == DurationInMinutes.Disabled
                        || song.durationText?.let { durationTextToMillis(it) < excludeSongWithDurationLimit.asMillis } == true

            BuiltInPlaylist.OnDevice ->
                !showFolder4LocalSongs
                        || currentPath.equals( songsOnDevice[song], true )
                        || "$currentPath/".equals( songsOnDevice[song], true )

            else -> true
        }
    LaunchedEffect( items, search.input, currentPath ) {
        items.filter( ::naturalFilter )
             .filter { !parentalControlEnabled || !it.title.startsWith( EXPLICIT_PREFIX, true ) }
             .operatorFilterSong(search.input)
            .let {
                itemsOnDisplay = it

                lazyListState.scrollToItem( 0 )
                isLoading = false
            }
    }

    //<editor-fold defaultstate="collapsed" desc="Dialog Renders">
    exportDialog.Render()
    downloadAllDialog.Render()
    deleteDownloadsDialog.Render()
    deleteSongDialog.Render()
    hideSongDialog.Render()
    deleteHiddenSongs.Render()
    resetCache.Render()
    //</editor-fold>

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
                HeaderInfo( itemsOnDisplay.size.toString(), R.drawable.musical_notes )
            }

            // Sticky tab's tool bar
            TabToolBar.Buttons(
                mutableListOf<Button>().apply {
                    this.add(
                        when( builtInPlaylist ) {
                            BuiltInPlaylist.Top -> topPlaylists
                            else -> songSort
                        }
                    )
                    this.add( search )
                    this.add( locator )
                    this.add( downloadAllDialog )
                    this.add( deleteDownloadsDialog )
                    //this.add( deleteSongDialog )
                    if (builtInPlaylist == BuiltInPlaylist.All || builtInPlaylist == BuiltInPlaylist.Downloaded)
                        this.add( hiddenSongs )
                    this.add( shuffle )
                    this.add( itemSelector )
                    this.add( playNext )
                    this.add( enqueue )
                    this.add( addToFavorite )
                    this.add( addToPlaylist )
                    this.add( exportDialog )
                    this.add( import )
                    this.add( deleteHiddenSongs )
                    this.add( resetCache )
                }
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding( horizontal = 12.dp )
                                   .padding( bottom = 8.dp )
                                   .fillMaxWidth()
            ) {
                Column {
                    ButtonsRow(
                        chips = buttonsList,
                        currentValue = builtInPlaylist,
                        onValueUpdate = {
                            builtInPlaylist = it
                        },
                        modifier = Modifier.padding(end = 12.dp)
                    )

                    when (builtInPlaylist) {
                        BuiltInPlaylist.Downloaded, BuiltInPlaylist.Offline -> {
                            CacheSpaceIndicator(
                                cacheType = when (builtInPlaylist) {
                                    BuiltInPlaylist.Downloaded -> CacheType.DownloadedSongs
                                    BuiltInPlaylist.Offline -> CacheType.CachedSongs
                                    else -> CacheType.CachedSongs
                                }
                            )
                        }
                        else -> {}
                    }

                }
            }

            // Sticky search bar
            search.SearchBar( this )

            LazyColumn(
                state = lazyListState,
                userScrollEnabled = !isLoading,
                contentPadding = PaddingValues( bottom = Dimensions.bottomSpacer )
            ) {
                if( isLoading )
                    items(
                        count = 20,
                        key = { it }
                    ) { SongItemPlaceholder() }

                if( builtInPlaylist == BuiltInPlaylist.OnDevice && showFolder4LocalSongs ) {
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

                    val isLocal by remember { derivedStateOf { mediaItem.isLocal } }
                    val isDownloaded = isLocal || isDownloadedSong( mediaItem.mediaId )

                    SwipeablePlaylistItem(
                        mediaItem = mediaItem,
                        onPlayNext = { binder?.player?.addNext( mediaItem ) },
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
                        onEnqueue = {
                            binder?.player?.enqueue(mediaItem)
                        }
                    ) {
                        SongItem(
                            song = song,
                            itemSelector = itemSelector,
                            navController = navController,
                            modifier = Modifier.animateItem(),
                            thumbnailOverlay = {
                                if ( songSort.sortBy == SongSortBy.PlayTime || builtInPlaylist == BuiltInPlaylist.Top ) {
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
                                search.onItemSelected()

                                binder?.stopRadio()
                                binder?.player?.forcePlayAtIndex( getMediaItems(), index )
                            }
                        )
                    }
                }
            }
        }

        FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)

        val showFloatingIcon by rememberPreference( showFloatingIconKey, false )
        if( UiType.ViMusic.isCurrent() && showFloatingIcon )
            MultiFloatingActionsContainer(
                iconId = R.drawable.search,
                onClick = {
                    navController.navigate(NavRoutes.search.name)
                },
                onClickSettings = {
                    navController.navigate(NavRoutes.settings.name)
                },
                onClickSearch = {
                    navController.navigate(NavRoutes.search.name)
                }
            )
    }
}