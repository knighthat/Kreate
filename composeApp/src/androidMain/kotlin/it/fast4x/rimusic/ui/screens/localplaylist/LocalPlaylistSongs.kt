package it.fast4x.rimusic.ui.screens.localplaylist

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.R
import com.github.doyaaaaaken.kotlincsv.client.KotlinCsvExperimental
import it.fast4x.compose.persist.persistList
import it.fast4x.compose.reordering.draggedItem
import it.fast4x.compose.reordering.rememberReorderingState
import it.fast4x.compose.reordering.reorder
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.bodies.BrowseBody
import it.fast4x.innertube.models.bodies.NextBody
import it.fast4x.innertube.requests.playlistPage
import it.fast4x.innertube.requests.relatedSongs
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.EXPLICIT_PREFIX
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.MONTHLY_PREFIX
import it.fast4x.rimusic.PIPED_PREFIX
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.cleanPrefix
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.enums.PlaylistSongSortBy
import it.fast4x.rimusic.enums.RecommendationsNumber
import it.fast4x.rimusic.enums.ThumbnailRoundness
import it.fast4x.rimusic.enums.UiType
import it.fast4x.rimusic.models.Song
import it.fast4x.rimusic.service.isLocal
import it.fast4x.rimusic.thumbnailShape
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.SwipeableQueueItem
import it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import it.fast4x.rimusic.ui.components.tab.toolbar.Button
import it.fast4x.rimusic.ui.components.tab.toolbar.Dialog
import it.fast4x.rimusic.ui.components.themed.Enqueue
import it.fast4x.rimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import it.fast4x.rimusic.ui.components.themed.HeaderIconButton
import it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import it.fast4x.rimusic.ui.components.themed.IconButton
import it.fast4x.rimusic.ui.components.themed.IconInfo
import it.fast4x.rimusic.ui.components.themed.ListenOnYouTube
import it.fast4x.rimusic.ui.components.themed.PlayNext
import it.fast4x.rimusic.ui.components.themed.Playlist
import it.fast4x.rimusic.ui.components.themed.PlaylistsMenu
import it.fast4x.rimusic.ui.components.themed.ResetThumbnail
import it.fast4x.rimusic.ui.components.themed.Search
import it.fast4x.rimusic.ui.components.themed.Synchronize
import it.fast4x.rimusic.ui.components.themed.ThumbnailPicker
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.onOverlay
import it.fast4x.rimusic.ui.styling.overlay
import it.fast4x.rimusic.ui.styling.px
import it.fast4x.rimusic.utils.DeletePlaylist
import it.fast4x.rimusic.utils.PlaylistSongsSort
import it.fast4x.rimusic.utils.PositionLock
import it.fast4x.rimusic.utils.Reposition
import it.fast4x.rimusic.utils.addNext
import it.fast4x.rimusic.utils.addToPipedPlaylist
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.autosyncKey
import it.fast4x.rimusic.utils.center
import it.fast4x.rimusic.utils.checkFileExists
import it.fast4x.rimusic.utils.color
import it.fast4x.rimusic.utils.completed
import it.fast4x.rimusic.utils.deleteFileIfExists
import it.fast4x.rimusic.utils.deletePipedPlaylist
import it.fast4x.rimusic.utils.disableScrollingTextKey
import it.fast4x.rimusic.utils.durationTextToMillis
import it.fast4x.rimusic.utils.enqueue
import it.fast4x.rimusic.utils.forcePlayAtIndex
import it.fast4x.rimusic.utils.forcePlayFromBeginning
import it.fast4x.rimusic.utils.formatAsTime
import it.fast4x.rimusic.utils.getPipedSession
import it.fast4x.rimusic.utils.isLandscape
import it.fast4x.rimusic.utils.isPipedEnabledKey
import it.fast4x.rimusic.utils.isRecommendationEnabledKey
import it.fast4x.rimusic.utils.manageDownload
import it.fast4x.rimusic.utils.operatorFilterSong
import it.fast4x.rimusic.utils.parentalControlEnabledKey
import it.fast4x.rimusic.utils.recommendationsNumberKey
import it.fast4x.rimusic.utils.rememberPreference
import it.fast4x.rimusic.utils.removeFromPipedPlaylist
import it.fast4x.rimusic.utils.saveImageToInternalStorage
import it.fast4x.rimusic.utils.semiBold
import it.fast4x.rimusic.utils.showFloatingIconKey
import it.fast4x.rimusic.utils.syncSongsInPipedPlaylist
import it.fast4x.rimusic.utils.thumbnailRoundnessKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.knighthat.component.ResetCache
import me.knighthat.component.SongItem
import me.knighthat.component.playlist.PinPlaylist
import me.knighthat.component.playlist.RenamePlaylistDialog
import me.knighthat.component.tab.DeleteAllDownloadedSongsDialog
import me.knighthat.component.tab.DownloadAllSongsDialog
import me.knighthat.component.tab.ExportSongsToCSVDialog
import me.knighthat.component.tab.ItemSelector
import me.knighthat.component.tab.LikeComponent
import me.knighthat.component.tab.Locator
import me.knighthat.component.tab.SongShuffler
import me.knighthat.utils.Toaster
import timber.log.Timber
import java.util.UUID


@KotlinCsvExperimental
@ExperimentalMaterialApi
@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalAnimationApi
@ExperimentalFoundationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun LocalPlaylistSongs(
    navController: NavController,
    playlistId: Long,
    onDelete: () -> Unit,
) {
    // Essentials
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val coroutineScope = rememberCoroutineScope()
    val lazyListState = rememberLazyListState()
    val uriHandler = LocalUriHandler.current

    // Settings
    val parentalControlEnabled by rememberPreference( parentalControlEnabledKey, false )
    val isPipedEnabled by rememberPreference( isPipedEnabledKey, false )
    val disableScrollingText by rememberPreference( disableScrollingTextKey, false )
    var isRecommendationEnabled by rememberPreference( isRecommendationEnabledKey, false )

    // Non-vital
    val pipedSession = getPipedSession()
    val thumbnailUrl = remember { mutableStateOf("") }

    val playlist by remember {
        Database.playlistTable
                .findById( playlistId )
    }.collectAsState( null, Dispatchers.IO )

    val sort = PlaylistSongsSort.init()
    val items by remember( sort.sortBy, sort.sortOrder ) {
        Database.songPlaylistMapTable
                .sortSongs( playlistId, sort.sortBy, sort.sortOrder )
                .flowOn( Dispatchers.IO )
                .distinctUntilChanged()
    }.collectAsState( emptyList(), Dispatchers.IO )
    var itemsOnDisplay by persistList<Song>("localPlaylist/$playlistId/songs/on_display")

    val itemSelector = ItemSelector<Song>()

    fun getSongs() = itemSelector.ifEmpty { itemsOnDisplay }
    fun getMediaItems() = getSongs().map( Song::asMediaItem )

    val search = Search.init()
    val shuffle = SongShuffler ( ::getSongs )
    val renameDialog = RenamePlaylistDialog { playlist }
    val exportDialog = ExportSongsToCSVDialog(
        playlistId = playlistId,
        playlistName = playlist?.name ?: "",
        songs = ::getSongs
    )
    val deleteDialog = DeletePlaylist {
        Database.asyncTransaction {
            playlist?.let( playlistTable::delete )
        }

        if (
            playlist?.name?.startsWith(PIPED_PREFIX) == true
            && isPipedEnabled
            && pipedSession.token.isNotEmpty()
        )
            deletePipedPlaylist(
                context = context,
                coroutineScope = coroutineScope,
                pipedSession = pipedSession.toApiSession(),
                id = UUID.fromString(playlist?.browseId)
            )

        onDismiss()

        if (navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED)
            navController.popBackStack()
    }
    val renumberDialog = Reposition(playlistId)
    val downloadAllDialog = DownloadAllSongsDialog ( ::getSongs )
    val deleteDownloadsDialog = DeleteAllDownloadedSongsDialog ( ::getSongs )
    val editThumbnailLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        // Callback is invoked after the user selects a media item or closes the
        // photo picker.
        if (uri != null) {
            val thumbnailName = "playlist_${playlist?.id}"
            val permaUri = saveImageToInternalStorage(context, uri, "thumbnail", thumbnailName)
            thumbnailUrl.value = permaUri.toString()
        } else {
            Toaster.w( R.string.thumbnail_not_selected )
        }
    }
    val pin = PinPlaylist( playlist )
    val positionLock = PositionLock.init( sort.sortOrder )
    LaunchedEffect( itemSelector.isActive ) {
        // Setting this field to true means disable it
        if( itemSelector.isActive )
            positionLock.isFirstIcon = true
    }
    // Either position lock or item selector can be turned on at a time
    LaunchedEffect( positionLock.isFirstIcon ) {
        if( !positionLock.isFirstIcon ) {
            // Open to move position
            itemSelector.isActive = false
            // Disable smart recommendation, it breaks the index
            isRecommendationEnabled = false
        }
    }

    val playNext = PlayNext {
        binder?.player?.addNext( getMediaItems(), appContext() )

        // Turn of selector clears the selected list
        itemSelector.isActive = false
    }
    val enqueue = Enqueue {
        binder?.player?.enqueue( getMediaItems(), context )

        // Turn of selector clears the selected list
        itemSelector.isActive = false
    }
    val addToFavorite = LikeComponent( ::getSongs )

    val addToPlaylist = PlaylistsMenu.init(
        navController,
        {
            if( it.playlist.name.startsWith(PIPED_PREFIX)
                && isPipedEnabled
                && pipedSession.token.isNotEmpty()
            )
                addToPipedPlaylist(
                    context = context,
                    coroutineScope = coroutineScope,
                    pipedSession = pipedSession.toApiSession(),
                    id = UUID.fromString(it.playlist.browseId),
                    videos = getSongs().map( Song::id )
                )

            getMediaItems()
        },
        { throwable, preview ->
            Timber.e( "Failed to add songs to playlist ${preview.playlist.name} on LocalPlaylistSongs" )
            throwable.printStackTrace()
        },
        {
            // Turn of selector clears the selected list
            itemSelector.isActive = false
        }
    )

    fun sync() {
        playlist?.let {
            if ( !it.name.startsWith(PIPED_PREFIX, true) ) {
                Database.asyncTransaction {
                    runBlocking(Dispatchers.IO) {
                        withContext(Dispatchers.IO) {
                            Innertube.playlistPage(
                                BrowseBody(
                                    browseId = it.browseId
                                        ?: ""
                                )
                            )
                                ?.completed()
                        }
                    }?.getOrNull()?.let { remotePlaylist ->
                        songPlaylistMapTable.clear( playlistId )

                        remotePlaylist.songsPage
                                      ?.items
                                      ?.map(Innertube.SongItem::asMediaItem)
                                      ?.let { mediaItems ->
                                          mapIgnore( it, *mediaItems.toTypedArray() )
                                      }
                    }
                }
            } else {
                syncSongsInPipedPlaylist(
                    context = context,
                    coroutineScope = coroutineScope,
                    pipedSession = pipedSession.toApiSession(),
                    idPipedPlaylist = UUID.fromString(
                        it.browseId
                    ),
                    playlistId = it.id

                )
            }
        }
    }
    val syncComponent = Synchronize { sync() }
    val listenOnYT = ListenOnYouTube {
        val browseId = playlist?.browseId?.removePrefix( "VL" )

        binder?.player?.pause()
        uriHandler.openUri( "https://youtube.com/playlist?list=$browseId" )
    }
    val resetCache = ResetCache( ::getSongs )

    fun openEditThumbnailPicker() {
        editThumbnailLauncher.launch("image/*")
    }
    val thumbnailPicker = ThumbnailPicker { openEditThumbnailPicker() }

    fun resetThumbnail() {
        if(thumbnailUrl.value == "") {
            Toaster.w( R.string.no_thumbnail_present )
            return
        }
        val thumbnailName = "thumbnail/playlist_${playlist?.id}"
        val retVal = deleteFileIfExists(context, thumbnailName)
        if(retVal == true){
            Toaster.s( R.string.removed_thumbnail )
            thumbnailUrl.value = ""
        } else
            Toaster.e( R.string.failed_to_remove_thumbnail )
    }
    val resetThumbnail = ResetThumbnail { resetThumbnail() }

    val locator = Locator( lazyListState, ::getSongs )

    //<editor-fold defaultstate="collapsed" desc="Smart recommendation">
    val recommendationsNumber by rememberPreference( recommendationsNumberKey, RecommendationsNumber.`5` )
    var relatedSongs by rememberSaveable {
        // SongEntity before Int in case random position is equal
        mutableStateOf( emptyMap<Song, Int>() )
    }

    LaunchedEffect( isRecommendationEnabled ) {
        if( !isRecommendationEnabled ) {
            relatedSongs = emptyMap()
            return@LaunchedEffect
        }

        /*
            This process will be run before [items]
               most of the time.
            When it does, an exception will
               be thrown because [items] is not ready yet.
            To make sure that it is ready to use, a
               delay is set to suspend the thread.
        */
        while( items.isEmpty() )
            delay( 100L )

        val requestBody = NextBody( videoId =  items.random().id )
        Innertube.relatedSongs( requestBody )
                 ?.getOrNull()      // If result is null, all subsequence calls are cancelled
                 ?.songs
                 ?.filterNot { songItem ->
                     // Fetched Song may not have properties like [likedAt]
                     // so the result of [List.any] may be false.
                     // Comparing their IDs is the most effective way
                     items.map( Song::id )
                          .any{ songItem.info?.endpoint?.videoId == it }
                 }
                 ?.take( recommendationsNumber.toInt() )
                 ?.associate { songItem ->
                     with( songItem ) {
                         // Do NOT use [Utils#Innertube.SongItem.asSong]
                         // It doesn't have explicit prefix
                         val prefix = if( explicit ) EXPLICIT_PREFIX else ""

                         Song(
                             // Song's ID & title must not be "null". If they are,
                             // Something is wrong with Innertube.
                             id = "$prefix${info!!.endpoint!!.videoId!!}",
                             title = info!!.name!!,
                             artistsText = authors?.joinToString { author -> author.name ?: "" },
                             durationText = durationText,
                             thumbnailUrl = thumbnail?.url
                         ) to (0..items.size).random()      // Map this song with a random position from [items]
                     }
                 }
                 ?.let {
                     relatedSongs = it

                     // Enable position lock
                     positionLock.isFirstIcon = true
                 }
    }
    //</editor-fold>
    LaunchedEffect( items, relatedSongs, search.input, parentalControlEnabled ) {
        items.toMutableList()
             .apply {
                 relatedSongs.forEach { (song, index) ->
                     // Make sure position can't go outside permissible range
                     // causing [IndexOutOfBoundException]
                     val position = index.coerceIn(0, size)
                     add( position, song )
                 }
             }
             .distinctBy( Song::id )
             .filter { !parentalControlEnabled || !it.title.startsWith( EXPLICIT_PREFIX ) }
            .operatorFilterSong(search.input)
            .let { itemsOnDisplay = it }
    }
    LaunchedEffect( playlist?.name ) {
//        renameDialog.playlistName = playlistPreview?.playlist?.name?.let { name ->
//            if( name.startsWith( MONTHLY_PREFIX, true ) )
//                getTitleMonthlyPlaylist(context, name.substringAfter(MONTHLY_PREFIX))
//            else
//                name.substringAfter( PINNED_PREFIX )
//                    .substringAfter( PIPED_PREFIX )
//        } ?: "Unknown"

        val thumbnailName = "thumbnail/playlist_${playlistId}"
        val presentThumbnailUrl: String? = checkFileExists(context, thumbnailName)
        if (presentThumbnailUrl != null) {
            thumbnailUrl.value = presentThumbnailUrl
        }
    }

    var autosync by rememberPreference(autosyncKey, false)

    val thumbnailRoundness by rememberPreference(
        thumbnailRoundnessKey,
        ThumbnailRoundness.Heavy
    )


    val reorderingState = rememberReorderingState(
        lazyListState = lazyListState,
        key = items,
        onDragEnd = { fromIndex, toIndex ->
            //Log.d("mediaItem","reoder playlist $playlistId, from $fromIndex, to $toIndex")
            Database.asyncTransaction {
                songPlaylistMapTable.move( playlistId, fromIndex, toIndex )
            }
        },
        extraItemCount = 1
    )

    renameDialog.Render()
    exportDialog.Render()
    deleteDialog.Render()
    (renumberDialog as Dialog).Render()
    downloadAllDialog.Render()
    deleteDownloadsDialog.Render()

    val playlistThumbnailSizeDp = Dimensions.thumbnails.playlist
    val playlistThumbnailSizePx = playlistThumbnailSizeDp.px

    val rippleIndication = ripple(bounded = false)

    val playlistNotMonthlyType =
        playlist?.name?.startsWith(MONTHLY_PREFIX, 0, true) == false


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
        //LookaheadScope {
        LazyColumn(
            state = reorderingState.lazyListState,
            //contentPadding = LocalPlayerAwareWindowInsets.current
            //    .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
            //    .asPaddingValues(),
            modifier = Modifier
                .background(colorPalette().background0)
                .fillMaxSize()
        ) {
            item(
                key = "header",
                contentType = 0
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                ) {

                    HeaderWithIcon(
                        title = cleanPrefix( playlist?.name ?: "" ),
                        iconId = R.drawable.playlist,
                        enabled = true,
                        showIcon = false,
                        modifier = Modifier
                            .padding(bottom = 8.dp),
                        onClick = {}
                    )

                }

                Row(
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        //.background(colorPalette().background4)
                        .fillMaxSize(0.99F)
                        .background(
                            color = colorPalette().background1,
                            shape = thumbnailRoundness.shape
                        )
                ) {

                    playlist?.let {
                        Playlist(
                            playlist = it,
                            songCount = items.size,
                            thumbnailSizeDp = playlistThumbnailSizeDp,
                            thumbnailSizePx = playlistThumbnailSizePx,
                            alternative = true,
                            showName = false,
                            modifier = Modifier
                                .padding(top = 14.dp),
                            disableScrollingText = disableScrollingText,
                            thumbnailUrl = if (thumbnailUrl.value == "") null else thumbnailUrl.value
                        )
                    }


                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier
                            //.fillMaxHeight()
                            .padding(end = 10.dp)
                            .fillMaxWidth(if (isLandscape) 0.90f else 0.80f)
                    ) {
                        Spacer(modifier = Modifier.height(10.dp))
                        IconInfo(
                            title = items.size.toString(),
                            icon = painterResource(R.drawable.musical_notes)
                        )
                        Spacer(modifier = Modifier.height(5.dp))

                        val totalDuration = items.sumOf { durationTextToMillis(it.durationText ?: "0:0") }
                        IconInfo(
                            title = formatAsTime( totalDuration ),
                            icon = painterResource(R.drawable.time)
                        )
                        if (isRecommendationEnabled) {
                            Spacer(modifier = Modifier.height(5.dp))
                            IconInfo(
                                title = relatedSongs.size.toString(),
                                icon = painterResource(R.drawable.smart_shuffle)
                            )
                        }
                        Spacer(modifier = Modifier.height(30.dp))
                    }

                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        HeaderIconButton(
                            icon = R.drawable.smart_shuffle,
                            enabled = true,
                            color = if (isRecommendationEnabled) colorPalette().text else colorPalette().textDisabled,
                            onClick = {},
                            modifier = Modifier
                                .combinedClickable(
                                    onClick = {
                                        isRecommendationEnabled = !isRecommendationEnabled
                                    },
                                    onLongClick = {
                                        Toaster.i( R.string.info_smart_recommendation )
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        shuffle.ToolBarButton()
                        Spacer(modifier = Modifier.height(10.dp))
                        search.ToolBarButton()
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                TabToolBar.Buttons(
                    mutableListOf<Button>().apply {
                        if (playlistNotMonthlyType)
                            this.add( pin )
                        if ( sort.sortBy == PlaylistSongSortBy.Position )
                            this.add( positionLock )

                        this.add( downloadAllDialog )
                        this.add( deleteDownloadsDialog )
                        this.add( itemSelector )
                        this.add( playNext )
                        this.add( enqueue )
                        this.add( addToFavorite )
                        this.add( addToPlaylist )
                        if( !playlist?.browseId.isNullOrBlank() ) {
                            this.add( syncComponent )
                            this.add( listenOnYT )
                        }
                        this.add( renameDialog )
                        this.add( renumberDialog )
                        this.add( deleteDialog )
                        this.add( exportDialog )
                        this.add( thumbnailPicker )
                        this.add( resetThumbnail )
                        this.add( resetCache )
                    }
                )

                if ( autosync && playlist?.browseId.isNullOrBlank() ) {
                    sync()
                }

                Spacer(modifier = Modifier.height(10.dp))

                /*        */
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .fillMaxWidth()
                ) {

                    sort.ToolBarButton()

                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) { locator.ToolBarButton() }

                }

                Column { search.SearchBar( this ) }
            }

            itemsIndexed(
                items = itemsOnDisplay,
                key = { _, song -> song.id },
                contentType = { _, song -> song },
            ) { index, song ->

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .draggedItem(
                            reorderingState = reorderingState,
                            index = index
                        )
                        .zIndex(2f)
                ) {
                    val isLocal by remember { derivedStateOf { song.asMediaItem.isLocal } }

                    // Drag anchor
                    if ( !positionLock.isLocked() ) {
                        Box(
                            modifier = Modifier.padding( end = 16.dp ) // Accommodate horizontal padding of SongItem
                                               .size( 24.dp )
                                               .zIndex( 2f )
                                               .align( Alignment.CenterEnd ),
                            contentAlignment = Alignment.Center
                        ) {

                            IconButton(
                                icon = R.drawable.reorder,
                                color = colorPalette().textDisabled,
                                indication = rippleIndication,
                                onClick = {},
                                modifier = Modifier
                                    .reorder(
                                        reorderingState = reorderingState,
                                        index = index
                                    )
                            )
                        }
                    }

                    SwipeableQueueItem(
                        mediaItem = song.asMediaItem,
                        onPlayNext = {
                            binder?.player?.addNext(song.asMediaItem)
                        },
                        onRemoveFromQueue = {
                            Database.asyncTransaction {
                                songPlaylistMapTable.deleteBySongId( song.id, playlistId )
                            }


                            if (playlist?.name?.startsWith(PIPED_PREFIX) == true && isPipedEnabled && pipedSession.token.isNotEmpty()) {
                                Timber.d("MediaItemMenu LocalPlaylistSongs onSwipeToLeft browseId ${playlist?.browseId}")
                                removeFromPipedPlaylist(
                                    context = context,
                                    coroutineScope = coroutineScope,
                                    pipedSession = pipedSession.toApiSession(),
                                    id = UUID.fromString(playlist?.browseId),
                                    index
                                )
                            }

                            Toaster.s(
                                "${context.resources.getString( R.string.deleted )} \"${song.asMediaItem.mediaMetadata.title}\" - \"${song.asMediaItem.mediaMetadata.artist}\""
                            )
                        },
                        onDownload = {
                            binder?.cache?.removeResource(song.asMediaItem.mediaId)
                            Database.asyncTransaction {
                                formatTable.updateContentLengthOf( song.id )
                            }

                            if (!isLocal) {
                                manageDownload(
                                    context = context,
                                    mediaItem = song.asMediaItem,
                                    downloadState = song.isLocal
                                )
                            }
                        },
                        onEnqueue = {
                            binder?.player?.enqueue(
                                song.asMediaItem,
                                context
                            )
                        },
                    ) {
                        SongItem(
                            song = song,
                            itemSelector = itemSelector,
                            navController = navController,
                            isRecommended = song in relatedSongs,
                            modifier = Modifier.background(color = colorPalette().background0),
                            trailingContent = {
                                if( !positionLock.isLocked() )
                                    // Create a fake box to store drag anchor and checkbox
                                    Box( Modifier.width( 24.dp ) )
                            },
                            thumbnailOverlay = {
                                if (sort.sortBy == PlaylistSongSortBy.PlayTime) {
                                    BasicText(
                                        text = song.formattedTotalPlayTime,
                                        style = typography().xxs.semiBold.center.color(
                                            colorPalette().onOverlay
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                                           .background(
                                                               brush = Brush.verticalGradient(
                                                                   colors = listOf(
                                                                       Color.Transparent,
                                                                       colorPalette().overlay
                                                                   )
                                                               ),
                                                               shape = thumbnailShape()
                                                           )
                                                           .padding( horizontal = 8.dp, vertical = 4.dp )
                                                           .align( Alignment.BottomCenter )
                                    )
                                }
                            },
                            onClick = {
                                binder?.stopRadio()
                                binder?.player?.forcePlayAtIndex(
                                    itemsOnDisplay.map( Song::asMediaItem ),
                                    index
                                )

                                /*
                                    Due to the small size of checkboxes,
                                    we shouldn't disable [itemSelector]
                                 */

                                search.onItemSelected()
                            }
                        )
                    }
                }

            }

            item(
                key = "footer",
                contentType = 0,
            ) {
                Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
            }
        }

        FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)

        val showFloatingIcon by rememberPreference(showFloatingIconKey, false)
        if ( UiType.ViMusic.isCurrent() && showFloatingIcon )
            FloatingActionsContainerWithScrollToTop(
                lazyListState = lazyListState,
                iconId = R.drawable.shuffle,
                visible = !reorderingState.isDragging,
                onClick = {
                    getMediaItems().let { songs ->
                        if (songs.isNotEmpty()) {
                            binder?.stopRadio()
                            binder?.player
                                  ?.forcePlayFromBeginning( songs.shuffled() )
                        }
                    }
                }
            )
    }
}
