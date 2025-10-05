package app.kreate.android.themed.rimusic.screen.playlist

import android.content.Intent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastFilter
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.coil3.ImageFactory
import app.kreate.android.themed.common.component.tab.DeleteAllDownloadedDialog
import app.kreate.android.themed.common.component.tab.DownloadAllDialog
import app.kreate.android.themed.rimusic.component.ItemSelector
import app.kreate.android.themed.rimusic.component.Search
import app.kreate.android.themed.rimusic.component.song.SongItem
import app.kreate.android.utils.innertube.CURRENT_LOCALE
import app.kreate.android.utils.innertube.toSong
import app.kreate.android.utils.renderDescription
import app.kreate.android.utils.scrollingText
import app.kreate.database.models.Song
import app.kreate.util.EXPLICIT_PREFIX
import it.fast4x.innertube.YtMusic
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.UiType
import it.fast4x.rimusic.service.modern.isLocal
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.Skeleton
import it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import it.fast4x.rimusic.ui.components.tab.toolbar.DualIcon
import it.fast4x.rimusic.ui.components.tab.toolbar.DynamicColor
import it.fast4x.rimusic.ui.components.themed.AutoResizeText
import it.fast4x.rimusic.ui.components.themed.Enqueue
import it.fast4x.rimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import it.fast4x.rimusic.ui.components.themed.FontSizeRange
import it.fast4x.rimusic.ui.components.themed.PlaylistsMenu
import it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.DisposableListener
import it.fast4x.rimusic.utils.addNext
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.collectLatest
import it.fast4x.rimusic.utils.enqueue
import it.fast4x.rimusic.utils.fadingEdge
import it.fast4x.rimusic.utils.forcePlayAtIndex
import it.fast4x.rimusic.utils.isDownloadedSong
import it.fast4x.rimusic.utils.isLandscape
import it.fast4x.rimusic.utils.isNetworkAvailable
import it.fast4x.rimusic.utils.manageDownload
import it.fast4x.rimusic.utils.medium
import it.fast4x.rimusic.utils.playShuffled
import it.fast4x.rimusic.utils.semiBold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.knighthat.component.tab.ExportSongsToCSVDialog
import me.knighthat.component.tab.LikeComponent
import me.knighthat.component.tab.Radio
import me.knighthat.component.tab.SongShuffler
import me.knighthat.component.ui.screens.DynamicOrientationLayout
import me.knighthat.innertube.Constants
import me.knighthat.innertube.Innertube
import me.knighthat.innertube.model.InnertubePlaylist
import me.knighthat.innertube.model.InnertubeSong
import me.knighthat.utils.Toaster
import timber.log.Timber

@ExperimentalAnimationApi
@ExperimentalFoundationApi
@UnstableApi
@Composable
fun YouTubePlaylist(
    navController: NavController,
    browseId: String,
    params: String?,
    useLogin: Boolean,
    miniPlayer: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current

    Skeleton(
        navController = navController,
        miniPlayer = miniPlayer,
        navBarContent = { item ->
            item(0, stringResource(R.string.songs), R.drawable.musical_notes)
        }
    ) {
        val binder = LocalPlayerServiceBinder.current ?: return@Skeleton
        val (colorPalette, typography) = LocalAppearance.current
        val hapticFeedback = LocalHapticFeedback.current
        val lazyListState = rememberLazyListState()

        var playlistPage: InnertubePlaylist? by remember { mutableStateOf(null) }
        var continuation: String? by remember { mutableStateOf(null) }
        val items = remember { mutableStateListOf<Song>() }
        val itemsOnDisplay = remember { mutableStateListOf<Song>() }

        val itemSelector = remember {
            ItemSelector(menuState) { addAll( itemsOnDisplay ) }
        }
        fun getSongs() = itemSelector.ifEmpty { itemsOnDisplay }
        fun getMediaItems() = getSongs().map( Song::asMediaItem )

        //<editor-fold desc="Toolbar buttons">
        val search = remember { Search(lazyListState) }
        val shuffle = SongShuffler ( ::getSongs )
        val exportDialog = ExportSongsToCSVDialog(
            playlistBrowseId = playlistPage?.id.orEmpty(),
            playlistName = playlistPage?.name.orEmpty(),
            songs = ::getSongs
        )
        val downloadAllDialog = remember {
            DownloadAllDialog( binder, context, ::getSongs )
        }
        val deleteDownloadsDialog = remember {
            DeleteAllDownloadedDialog( binder, context, ::getSongs )
        }
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
        val addToFavorite = LikeComponent( ::getSongs )
        val enqueue = Enqueue {
            getSongs().also( binder.player::enqueue )

            // Turn of selector clears the selected list
            itemSelector.isActive = false
        }
        val radio = Radio( ::getSongs )
        val saveToYouTubeLibrary = remember {
            object: DualIcon, DynamicColor {

                override val secondIconId: Int = R.drawable.bookmark
                override val iconId: Int = R.drawable.bookmark_outline

                override var isFirstIcon: Boolean by mutableStateOf( false )
                override var isFirstColor: Boolean by mutableStateOf( false )

                override fun onShortClick() {
                    if( !isNetworkAvailable( context ) ) {
                        Toaster.noInternet()
                        return
                    }

                    CoroutineScope( Dispatchers.IO ).launch {
                        YtMusic.removelikePlaylistOrAlbum(
                            browseId.substringAfter("VL")
                        )

                        Database.playlistTable
                                .findByBrowseId( browseId.substringAfter("VL") )
                                .first()
                                ?.let( Database.playlistTable::delete )
                    }
                }
            }
        }
        //</editor-fold>

        val pageProvider: suspend (String?) -> Unit by rememberUpdatedState {
            if ( it.isNullOrBlank() )
                Innertube.browsePlaylist( browseId, CURRENT_LOCALE, useLogin )
                         .onSuccess { page ->
                             playlistPage = page

                             items.addAll( playlistPage!!.songs.map( InnertubeSong::toSong ) )
                             continuation = playlistPage!!.songContinuation
                         }
                         .onFailure { err ->
                             Timber.tag( "YouTubePlaylist" ).e( err )
                             Toaster.e( R.string.error_failed_to_load_playlist )
                         }
            else if ( playlistPage?.visitorData != null || useLogin )
                Innertube.playlistContinued(
                             if( useLogin ) null else playlistPage!!.visitorData!!,
                             it,
                             CURRENT_LOCALE,
                             params,
                             useLogin
                         )
                         .onSuccess { continued ->
                             items.addAll(
                                 continued.songs
                                          .map( InnertubeSong::toSong )
                             )
                             continuation = continued.continuation
                         }
                         .onFailure { err ->
                             Timber.tag( "YouTubePlaylist" ).e( err )
                             Toaster.e( R.string.error_failed_to_get_playlists_next_songs )
                         }
        }

        val dislikedSongs by remember {
            Database.songTable
                .allDisliked()
                .map { list ->
                    list.map( Song::id )
                }
                .distinctUntilChanged()
        }.collectAsState( emptyList(), Dispatchers.IO )

        LaunchedEffect( lazyListState ) {
            snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" } }
                .collect { shouldLoadMore ->
                    if (!shouldLoadMore) return@collect

                    CoroutineScope(Dispatchers.IO).launch {
                        pageProvider(continuation)
                    }
                }
        }
        LaunchedEffect( Unit ) {
            // [items] uses [mutableStateListOf] which is content observable'
            // not reference observable.
            snapshotFlow { items.toList() to search.input.text }
                .collectLatest(
                    CoroutineScope(Dispatchers.Default)
                ) { (list, query) ->
                    list.fastFilter { it.id !in dislikedSongs }
                        .fastFilter {
                            !Preferences.PARENTAL_CONTROL.value || !it.title.startsWith( EXPLICIT_PREFIX, true )
                        }
                        .fastFilter {
                            val containsTitle = it.cleanTitle().contains( query, true )
                            val containsArtist = it.cleanArtistsText().contains( query, true )

                            containsTitle || containsArtist
                        }
                        .also {
                            itemsOnDisplay.clear()
                            itemsOnDisplay.addAll( it )
                        }
                }
        }

        exportDialog.Render()
        downloadAllDialog.Render()
        deleteDownloadsDialog.Render()

        var currentlyPlaying by remember { mutableStateOf(binder.player.currentMediaItem?.mediaId) }
        binder.player.DisposableListener {
            object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int ) {
                    currentlyPlaying = mediaItem?.mediaId
                }
            }
        }
        val songItemValues = remember( colorPalette, typography ) {
            SongItem.Values.from( colorPalette, typography )
        }

        val thumbnailPainter =
            ImageFactory.rememberAsyncImagePainter( playlistPage?.thumbnails?.firstOrNull()?.url )

        DynamicOrientationLayout(thumbnailPainter) {
            Box( Modifier.fillMaxSize() ) {
                LazyColumn(
                    state = lazyListState,
                    userScrollEnabled = items.isNotEmpty(),
                    contentPadding = PaddingValues(bottom = Dimensions.bottomSpacer),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()
                ) {
                    item("header") {
                        Box( Modifier.fillMaxWidth() ) {
                            if ( !isLandscape )
                                Image(
                                    painter = thumbnailPainter,
                                    contentDescription = null,
                                    contentScale = ContentScale.FillWidth,
                                    modifier = Modifier.aspectRatio(4f / 3)      // Limit height
                                                       .fillMaxWidth()
                                                       .align( Alignment.Center )
                                                       .fadingEdge(
                                                           top = WindowInsets.systemBars
                                                               .asPaddingValues()
                                                               .calculateTopPadding() + Dimensions.fadeSpacingTop,
                                                           bottom = Dimensions.fadeSpacingBottom
                                                       )
                                )

                            if( playlistPage?.id?.startsWith( "VL", true ) == true ) {
                                Icon(
                                    painter = painterResource( R.drawable.ytmusic ),
                                    contentDescription = null,
                                    tint = Color.Red
                                                .compositeOver( Color.White )
                                                .copy( 0.5f ),
                                    modifier = Modifier.padding( all = 5.dp )
                                                       .size( 40.dp )
                                                       .align( Alignment.TopStart )
                                )

                                Icon(
                                    painter = painterResource( R.drawable.share_social ),
                                    contentDescription = stringResource( R.string.listen_on_youtube_music ),
                                    tint = colorPalette().text.copy( .5f ),
                                    modifier = Modifier.padding( all = 5.dp )
                                                       .size( 40.dp )
                                                       .align( Alignment.TopEnd )
                                                       .clickable {
                                                           playlistPage?.shareUrl( Constants.YOUTUBE_MUSIC_URL )?.also { url ->
                                                               val sendIntent = Intent().apply {
                                                                   action = Intent.ACTION_SEND
                                                                   type = "text/plain"
                                                                   putExtra(Intent.EXTRA_TEXT, url)
                                                               }

                                                               context.startActivity(
                                                                   Intent.createChooser(
                                                                       sendIntent,
                                                                       null
                                                                   )
                                                               )
                                                           }
                                                       }
                                )
                            }

                            AutoResizeText(
                                text = playlistPage?.name.orEmpty(),
                                style = typography().l.semiBold,
                                fontSizeRange = FontSizeRange(32.sp, 38.sp),
                                fontWeight = typography().l.semiBold.fontWeight,
                                fontFamily = typography().l.semiBold.fontFamily,
                                color = typography().l.semiBold.color,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.align( Alignment.BottomCenter )
                                                   .padding( horizontal = 30.dp )
                                                   .scrollingText()
                            )
                        }
                    }

                    item( "subtitle" ) {
                        BasicText(
                            text = playlistPage?.subtitleText.orEmpty(),
                            style = typography().xs.medium,
                            maxLines = 1
                        )
                    }

                    item( "toolbarButtons" ) {
                        Box( Modifier.fillMaxWidth( .8f ) ) {
                            TabToolBar.Buttons(
                                buildList {
                                    add( search )
                                    add( downloadAllDialog )
                                    add( deleteDownloadsDialog )
                                    add( enqueue )
                                    add( shuffle )
                                    add( radio )
                                    add( addToPlaylist )
                                    add( addToFavorite )
                                    if( isYouTubeSyncEnabled() )
                                        add( saveToYouTubeLibrary )
                                }
                            )
                        }

                        search.SearchBar()
                    }

                    playlistPage?.description?.let {
                        renderDescription( it )
                    }

                    itemsIndexed(
                        items = itemsOnDisplay,
                        // Include index to key so when reposition happens, the content
                        // will get updated accordingly
                        key = { i, s -> "${System.identityHashCode(s)} - $i" }
                    ) { index, song ->
                        val isLocal by remember { derivedStateOf { song.isLocal } }
                        val isDownloaded = !isLocal && isDownloadedSong( song.id )

                        SwipeablePlaylistItem(
                            mediaItem = song.asMediaItem,
                            onPlayNext = { binder.player.addNext( song ) },
                            onDownload = {
                                binder.cache.removeResource( song.id )
                                Database.asyncTransaction {
                                    formatTable.updateContentLengthOf( song.id )
                                }

                                if (!isLocal)
                                    manageDownload(
                                        context = context,
                                        mediaItem = song.asMediaItem,
                                        downloadState = isDownloaded
                                    )
                            },
                            onEnqueue = { binder.player.enqueue( song ) }
                        ) {
                            SongItem.Render(
                                song = song,
                                context = context,
                                binder = binder,
                                hapticFeedback = hapticFeedback,
                                isPlaying = currentlyPlaying == song.id,
                                values = songItemValues,
                                itemSelector = itemSelector,
                                navController = navController,
                                modifier = Modifier.animateItem(),
                                onClick = {
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


                    if ( playlistPage == null || continuation != null )
                        item("loading") { SongItem.Placeholder() }
                }

                val showFloatingIcon by Preferences.SHOW_FLOATING_ICON
                if( UiType.ViMusic.isCurrent() && showFloatingIcon )
                    FloatingActionsContainerWithScrollToTop(
                        lazyListState = lazyListState,
                        iconId = R.drawable.shuffle,
                        onClick = {
                            if( items.all { it.id in dislikedSongs } ) {
                                Toaster.e( R.string.disliked_this_collection )
                                return@FloatingActionsContainerWithScrollToTop
                            }

                            binder.stopRadio()
                            getSongs().also( binder.player::playShuffled )
                        }
                    )
            }
        }
    }
}