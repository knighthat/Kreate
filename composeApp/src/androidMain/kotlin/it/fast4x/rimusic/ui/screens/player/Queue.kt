package it.fast4x.rimusic.ui.screens.player


import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.navigation.NavController
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.rimusic.component.ItemSelector
import app.kreate.android.themed.rimusic.component.Search
import app.kreate.android.themed.rimusic.component.playlist.PositionLock
import app.kreate.android.themed.rimusic.component.song.SongItem
import app.kreate.android.utils.shallowCompare
import app.kreate.database.models.Song
import it.fast4x.compose.persist.persist
import it.fast4x.compose.persist.persistList
import it.fast4x.compose.reordering.draggedItem
import it.fast4x.compose.reordering.rememberReorderingState
import it.fast4x.compose.reordering.reorder
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.QueueLoopType
import it.fast4x.rimusic.enums.QueueType
import it.fast4x.rimusic.service.modern.isLocal
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.SwipeableQueueItem
import it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import it.fast4x.rimusic.ui.components.tab.toolbar.Button
import it.fast4x.rimusic.ui.components.tab.toolbar.Dialog
import it.fast4x.rimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import it.fast4x.rimusic.ui.components.themed.IconButton
import it.fast4x.rimusic.ui.components.themed.PlaylistsMenu
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.asSong
import it.fast4x.rimusic.utils.enqueue
import it.fast4x.rimusic.utils.isDownloadedSong
import it.fast4x.rimusic.utils.isLandscape
import it.fast4x.rimusic.utils.isNowPlaying
import it.fast4x.rimusic.utils.manageDownload
import it.fast4x.rimusic.utils.mediaItems
import it.fast4x.rimusic.utils.shouldBePlaying
import me.knighthat.component.tab.ExportSongsToCSVDialog
import me.knighthat.component.tab.Locator
import me.knighthat.component.ui.screens.player.DeleteFromQueue
import me.knighthat.component.ui.screens.player.Discover
import me.knighthat.component.ui.screens.player.QueueArrow
import me.knighthat.component.ui.screens.player.Repeat
import me.knighthat.component.ui.screens.player.ShuffleQueue
import me.knighthat.utils.Toaster
import timber.log.Timber


@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@androidx.media3.common.util.UnstableApi
@Composable
fun Queue(
    navController: NavController,
    onDismiss: (QueueLoopType) -> Unit,
    onDiscoverClick: (Boolean) -> Unit,
) {
    // Essentials
    val context = LocalContext.current
    val windowInsets = WindowInsets.systemBars
    val binder = LocalPlayerServiceBinder.current ?: return
    val (colorPalette, typography) = LocalAppearance.current
    val hapticFeedback = LocalHapticFeedback.current
    val player = binder.player
    val menuState = LocalMenuState.current

    val rippleIndication = ripple(bounded = false)

    Box( Modifier.fillMaxSize() ) {
        var items by persist(
            tag = "queue/songs",
            player.currentTimeline.mediaItems.map( MediaItem::asSong )
        )
        val currentMediaItem by player.currentMediaItemState.collectAsState()
        val currentTimeline by player.currentTimelineState.collectAsState()
        // TODO: Merge this LaunchedEffect to [items]
        LaunchedEffect( currentTimeline ) {
            items = currentTimeline.mediaItems.map( MediaItem::asSong )
        }
        val songItemValues = remember( colorPalette, typography ) {
            SongItem.Values.from( colorPalette, typography )
        }
        var itemsOnDisplay by persistList<Song>( "queue/on_display" )

        val lazyListState = rememberLazyListState()
        val reorderingState = rememberReorderingState(
            lazyListState = lazyListState,
            key = items,
            onDragEnd = player::moveMediaItem,
            extraItemCount = 0
        )

        val positionLock = remember { PositionLock() }

        val itemSelector = remember {
            ItemSelector( menuState ) { addAll( itemsOnDisplay ) }
        }
        LaunchedEffect( itemSelector.isActive ) {
            // Setting this field to true means disable it
            if( itemSelector.isActive )
                positionLock.isFirstIcon = true
        }

        fun getSongs() = itemSelector.ifEmpty { items }

        val search = remember { Search(lazyListState) }
        LaunchedEffect( items, search.input ) {
            items.filter {
                    // Without cleaning, user can search explicit songs with "e:"
                    // I kinda want this to be a feature, but it seems unnecessary
                    val containsTitle = search appearsIn it.cleanTitle()
                    val containsArtist = search appearsIn it.cleanArtistsText()

                    containsTitle || containsArtist
                }
                .let { itemsOnDisplay = it }
        }

        val exportDialog = ExportSongsToCSVDialog(
            playlistName = "",
            songs = ::getSongs
        )
        val shuffle = ShuffleQueue( player, reorderingState )
        val discover = Discover( onDiscoverClick )
        val repeat = Repeat.init()
        val deleteDialog = DeleteFromQueue {
            if( itemSelector.isEmpty() ) {
                player.stop()
                player.clearMediaItems()
            } else
                itemSelector.map( items::indexOf )
                            .sorted()
                            // Goes backward to prevent item from being skipped
                            // due to the previous element is removed and the indices
                            // are updated.
                            .reversed()
                            .forEach( player::removeMediaItem )

            itemSelector.isActive = false

            onDismiss()
        }
        val addToPlaylist = PlaylistsMenu.init(
            navController = navController,
            mediaItems = { getSongs().map( Song::asMediaItem ) },
            onFailure = { throwable, preview ->
                Timber.e( "Failed to add songs to playlist ${preview.playlist.name} on HomeSongs" )
                throwable.printStackTrace()
            },
            finalAction = {
                // Turn of selector clears the selected list
                itemSelector.isActive = false
            }
        )
        val queueArrow = QueueArrow { onDismiss( repeat.type ) }
        val locator = Locator( lazyListState, ::getSongs )

        // Dialog renders
        exportDialog.Render()
        (deleteDialog as Dialog).Render()

        Column {
            val queueType by Preferences.QUEUE_TYPE
            val backgroundAlpha = if( queueType == QueueType.Modern ) .5f else 1f

            LazyColumn(
                state = reorderingState.lazyListState,
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = windowInsets
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                    .add( WindowInsets(bottom = Dimensions.bottomSpacer) )
                    .asPaddingValues(),
                modifier = Modifier.weight( 1f )
                                   .background(
                                       colorPalette().background0.copy( alpha = backgroundAlpha )
                                   )

            ) {
                itemsIndexed(
                    items = itemsOnDisplay,
                    key = { index, song -> "%s-%d".format( System.identityHashCode( song ), index ) }
                ) { index, song ->

                    val isLocal by remember { derivedStateOf { song.isLocal } }
                    val isDownloaded = isLocal || isDownloadedSong(song.id)

                    Box(
                        modifier = Modifier.fillMaxWidth()
                                           .draggedItem(
                                               reorderingState = reorderingState,
                                               index = index
                                           )
                    ) {
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
                                    modifier = Modifier.reorder(
                                        reorderingState = reorderingState,
                                        index = index
                                    )
                                )
                            }
                        }

                        val mediaItem = song.asMediaItem
                        SwipeableQueueItem(
                            mediaItem = mediaItem,
                            onPlayNext = {
                                binder.player.moveMediaItem( index, binder.player.currentMediaItemIndex + 1 )
                            },
                            onDownload = {
                                binder.cache.removeResource(song.id)
                                if (!isLocal)
                                    manageDownload(
                                        context = context,
                                        mediaItem = mediaItem,
                                        downloadState = isDownloaded
                                    )
                            },
                            onRemoveFromQueue = {
                                player.removeMediaItem( index )
                                Toaster.s(
                                    "${context.resources.getString(R.string.deleted)} ${song.cleanTitle()}"
                                )
                            },
                            onEnqueue = {
                                binder.player.enqueue(mediaItem)
                            }
                        ) {
                            SongItem.Render(
                                song = song,
                                context = context,
                                binder = binder,
                                hapticFeedback = hapticFeedback,
                                isPlaying = song.shallowCompare( currentMediaItem ),
                                values = songItemValues,
                                itemSelector = itemSelector,
                                navController = navController,
                                trailingContent = {
                                    if( !positionLock.isLocked() )
                                    // Create a fake box to store drag anchor and checkbox
                                        Box( Modifier.width( 24.dp ) )
                                },
                                onClick = {
                                    if( player.isNowPlaying(song.id) ) {
                                        if(player.shouldBePlaying)
                                            player.pause()
                                        else
                                            player.play()
                                    } else {
                                        player.seekToDefaultPosition(index)
                                        player.prepare()
                                        player.playWhenReady = true
                                    }

                                    /*
                                        Due to the small size of checkboxes,
                                        we shouldn't disable [itemSelector]
                                     */

                                    search.hideIfEmpty()
                                }
                            )
                        }
                    }
                }

                if( binder.isLoadingRadio )
                    items( 3 ) {
                        SongItem.Placeholder()
                    }
            }

            // Search box
            Box(
                modifier = Modifier.fillMaxWidth()
                                   .background( colorPalette().background1 ),
            ) { search.SearchBar() }

            Box(
                modifier = Modifier.fillMaxWidth()
                                   .clickable { onDismiss( repeat.type ) }
                                   .background (colorPalette().background1 )
                                   .height( 60.dp ) //bottom bar queue
            ) {
                if( !isLandscape ) {
                    // Move mini player up as search bar appears
                    val yOffset = if( search.isVisible ) -125 else -65

                    Box(
                        Modifier.absoluteOffset( 0.dp, yOffset.dp )
                                .align( Alignment.TopCenter )
                    ) { MiniPlayer( {}, {} ) }
                }

                if ( !queueArrow.isEnabled )
                    Image(
                        painter = painterResource( R.drawable.horizontal_bold_line_rounded ),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette().text),
                        modifier = Modifier.absoluteOffset( 0.dp, (-10).dp )
                                           .align( Alignment.TopCenter )
                                           .size( 30.dp )
                    )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.padding( horizontal = 8.dp )
                                       .fillMaxWidth()
                ) {
                    /* Number of songs
                     *
                     * Opted out of using [IconInfo] because it has [Modifier#fillMaxWidth]
                     * which makes it harder to adopt flexible width.
                     */
                    Row(
                        modifier = Modifier.height( TabToolBar.TOOLBAR_ICON_SIZE )
                                           .wrapContentWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource( R.drawable.musical_notes ),
                            contentDescription = "Number of songs in queue",
                            tint = colorPalette().text,
                            modifier = Modifier.padding( end = 2.dp )
                        )
                        BasicText(
                            text = player.mediaItemCount.toString(),
                            style = TextStyle(
                                color = colorPalette().text,
                                fontStyle = typography().l.fontStyle
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    TabToolBar.Buttons(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.weight( 1f ),
                        buttons = mutableListOf<Button>().apply {
                            add( locator )
                            add( search )
                            if( Preferences.PLAYER_ACTION_DISCOVER.value )
                                add( discover )
                            add( positionLock )
                            add( repeat )
                            add( shuffle )
                            add( itemSelector )
                            add( deleteDialog )
                            add( addToPlaylist )
                            add( exportDialog )
                        }
                    )

                    if( queueArrow.isEnabled )
                        queueArrow.ToolBarButton()
                }
            }
        }

        FloatingActionsContainerWithScrollToTop(
            lazyListState = reorderingState.lazyListState,
            modifier = Modifier.padding(bottom = Dimensions.miniPlayerHeight)
        )
    }
}
