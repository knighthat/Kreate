package app.kreate.android.themed.rimusic.screen.player

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.coil3.ImageFactory
import app.kreate.android.service.player.StatefulPlayer
import app.kreate.android.utils.scrollingText
import app.kreate.di.CacheType
import app.kreate.util.cleanPrefix
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.enums.ColorPaletteMode
import it.fast4x.rimusic.enums.ColorPaletteName
import it.fast4x.rimusic.enums.PlayerBackgroundColors
import it.fast4x.rimusic.enums.PlayerType
import it.fast4x.rimusic.enums.QueueLoopType
import it.fast4x.rimusic.enums.SongsNumber
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.themed.AddToPlaylistPlayerMenu
import it.fast4x.rimusic.ui.components.themed.PlayerMenu
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.DisposableListener
import it.fast4x.rimusic.utils.addNext
import it.fast4x.rimusic.utils.getDownloadState
import it.fast4x.rimusic.utils.isDownloadedSong
import it.fast4x.rimusic.utils.isLandscape
import it.fast4x.rimusic.utils.manageDownload
import it.fast4x.rimusic.utils.mediaItems
import it.fast4x.rimusic.utils.playAtIndex
import it.fast4x.rimusic.utils.semiBold
import it.fast4x.rimusic.utils.shuffleQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import me.knighthat.component.player.PlaybackSpeed
import me.knighthat.kreate.composeapp.generated.resources.Res
import me.knighthat.kreate.composeapp.generated.resources.add_in_playlist
import me.knighthat.kreate.composeapp.generated.resources.chevron_up
import me.knighthat.kreate.composeapp.generated.resources.download
import me.knighthat.kreate.composeapp.generated.resources.download_progress
import me.knighthat.kreate.composeapp.generated.resources.downloaded
import me.knighthat.kreate.composeapp.generated.resources.ellipsis_vertical
import me.knighthat.kreate.composeapp.generated.resources.equalizer
import me.knighthat.kreate.composeapp.generated.resources.maximize
import me.knighthat.kreate.composeapp.generated.resources.radio
import me.knighthat.kreate.composeapp.generated.resources.shuffle
import me.knighthat.kreate.composeapp.generated.resources.sleep
import me.knighthat.kreate.composeapp.generated.resources.song_lyrics
import me.knighthat.kreate.composeapp.generated.resources.sound_effect
import me.knighthat.kreate.composeapp.generated.resources.star_brilliant
import me.knighthat.kreate.composeapp.generated.resources.trash
import me.knighthat.kreate.composeapp.generated.resources.video
import me.knighthat.utils.Toaster
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject


private const val BUTTON_SIZE = 24

private class PagerViewPort(
    private val showSongsState: MutableState<SongsNumber>,
    private val pagerState: PagerState,
): PageSize {

    override fun Density.calculateMainAxisPageSize( availableSpace: Int, pageSpacing: Int ): Int {
        val canShow = minOf( showSongsState.value.toInt() , pagerState.pageCount )
        return if( canShow > 1 )
            (availableSpace - 2 * pageSpacing) / canShow
        else
            availableSpace
    }
}

@Composable
fun ActionButton(
    resource: DrawableResource,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    IconButton(
        onClick = {},
        interactionSource = interactionSource,
        modifier = modifier
    ) {
        Icon(
            painter = painterResource( resource ),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size( BUTTON_SIZE.dp ).combinedClickable(
                // Use the same [interactionSource] for ripple effect
                interactionSource = interactionSource,
                // No ripple on inner icon, just outer one
                indication = null,
                onLongClick = onLongClick,
                onClick = onClick
            )
        )
    }
}

@ExperimentalTextApi
@ExperimentalAnimationApi
@UnstableApi
@Composable
fun BoxScope.ActionBar(
    navController: NavController,
    showQueueState: MutableState<Boolean>,
    showSearchEntityState: MutableState<Boolean>,
    rotateState: MutableState<Boolean>,
    showVisualizerState: MutableState<Boolean>,
    showSleepTimerState: MutableState<Boolean>,
    showLyricsState: MutableState<Boolean>,
    discoverState: MutableState<Boolean>,
    queueLoopState: MutableState<QueueLoopType>,
    expandPlayerState: MutableState<Boolean>,
    onDismiss: () -> Unit
) {
    // Essentials
    val context = LocalContext.current
    val player: StatefulPlayer = koinInject()
    val menuState = LocalMenuState.current
    val (colorPalette, typography) = LocalAppearance.current

    val mediaItem = player.currentMediaItem ?: return

    val playerBackgroundColors by Preferences.PLAYER_BACKGROUND
    val blackGradient by Preferences.BLACK_GRADIENT
    val showLyricsThumbnail by Preferences.LYRICS_SHOW_THUMBNAIL
    val showNextSongsInPlayer by Preferences.PLAYER_SHOW_NEXT_IN_QUEUE
    val miniQueueExpanded by Preferences.PLAYER_IS_NEXT_IN_QUEUE_EXPANDED
    val tapQueue by Preferences.PLAYER_ACTIONS_BAR_TAP_TO_OPEN_QUEUE
    val transparentBackgroundActionBarPlayer by Preferences.PLAYER_TRANSPARENT_ACTIONS_BAR
    val swipeUpQueue by Preferences.PLAYER_ACTIONS_BAR_SWIPE_UP_TO_OPEN_QUEUE

    var showQueue by showQueueState
    var isShowingVisualizer by showVisualizerState
    var isShowingLyrics by showLyricsState

    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.padding( if( isLandscape ) WindowInsets.navigationBars.asPaddingValues() else PaddingValues() )
                           .align(if (isLandscape) Alignment.BottomEnd else Alignment.BottomCenter)
                           .requiredHeight(if (showNextSongsInPlayer && (showLyricsThumbnail || (!isShowingLyrics || miniQueueExpanded))) 90.dp else 50.dp)
                           .fillMaxWidth(if (isLandscape) 0.8f else 1f)
                           .clickable( enabled = tapQueue ) {
                               showQueue = true
                           }
                           .background(
                               colorPalette.background2.copy(
                                   alpha =
                                       if (transparentBackgroundActionBarPlayer
                                           || (playerBackgroundColors == PlayerBackgroundColors.CoverColorGradient
                                                   || (playerBackgroundColors == PlayerBackgroundColors.ThemeColorGradient)
                                               )
                                           && blackGradient)
                                           0.0f
                                       else
                                           0.7f // 0.0 > 0.1
                               )
                           )
                           .pointerInput(Unit) {
                               if (swipeUpQueue)
                                   detectVerticalDragGestures(
                                       onVerticalDrag = { _, dragAmount ->
                                           if (dragAmount < 0) showQueue = true
                                       }
                                   )
                           },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            verticalArrangement = Arrangement.SpaceAround,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            if ( showNextSongsInPlayer && (showLyricsThumbnail || !isShowingLyrics || miniQueueExpanded) ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .background(
                            colorPalette.background2.copy(
                                alpha = if (transparentBackgroundActionBarPlayer) 0.0f else 0.3f
                            )
                        )
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth()
                ) {
                    val coroutine = rememberCoroutineScope()
                    var currentIndex by remember { mutableIntStateOf( player.currentMediaItemIndex ) }
                    var nextIndex by remember { mutableIntStateOf( player.nextMediaItemIndex ) }
                    val mediaItems: List<MediaItem> by player
                                                             .currentTimelineState
                                                             .map { it.mediaItems }
                                                             .collectAsState(
                                                                 initial = emptyList(),
                                                                 context = Dispatchers.Main
                                                             )
                    val pagerStateQueue = rememberPagerState { mediaItems.size }

                    suspend fun scrollToNext() {
                        val page = nextIndex.coerceIn( 0, pagerStateQueue.pageCount )
                        if(
                            C.INDEX_UNSET == nextIndex
                            || (page > pagerStateQueue.currentPage && !pagerStateQueue.canScrollForward)
                            || (page < pagerStateQueue.currentPage && !pagerStateQueue.canScrollBackward)
                        )
                            return
                        else
                            pagerStateQueue.animateScrollToPage( page )
                    }
                    LaunchedEffect( mediaItems.size ) {
                        scrollToNext()
                    }

                    player.DisposableListener {
                        object : Player.Listener {
                            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                                currentIndex = player.currentMediaItemIndex
                                nextIndex = player.nextMediaItemIndex

                                coroutine.launch { scrollToNext() }
                            }
                        }
                    }

                    //<editor-fold desc="Relative position indicator">
                    val icon = if( pagerStateQueue.currentPage > currentIndex )
                        Icons.AutoMirrored.Default.KeyboardArrowLeft
                    else if( pagerStateQueue.currentPage == currentIndex )
                        Icons.Default.PlayArrow
                    else
                        Icons.AutoMirrored.Default.KeyboardArrowRight

                    IconButton(
                        onClick = {
                            coroutine.launch {
                                pagerStateQueue.animateScrollToPage( currentIndex )
                            }
                        },
                        modifier = Modifier.size( BUTTON_SIZE.dp )
                    ) {
                        Icon(
                            imageVector = icon,
                            tint = colorPalette.accent,
                            // TODO: localize
                            contentDescription = "Go to currently playing song"
                        )
                    }
                    //</editor-fold>

                    val showSongsState = Preferences.MAX_NUMBER_OF_NEXT_IN_QUEUE
                    val viewPort = remember {
                        PagerViewPort( showSongsState, pagerStateQueue )
                    }

                    HorizontalPager(
                        state = pagerStateQueue,
                        pageSize = viewPort,
                        pageSpacing = 10.dp,
                        modifier = Modifier.weight(1f)
                    ) { index ->
                        val mediaItemAtIndex by remember { derivedStateOf { mediaItems[index] } }

                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .combinedClickable(
                                    onClick = {
                                        player.playAtIndex(index)
                                    },
                                    onLongClick = {
                                        if ( index < mediaItems.size ) {
                                            player.addNext( mediaItemAtIndex )
                                            Toaster.s( R.string.addednext )
                                        }
                                    }
                                )
                        ) {
                            val showAlbumCover by Preferences.PLAYER_SHOW_NEXT_IN_QUEUE_THUMBNAIL
                            if ( showAlbumCover )
                                Box( Modifier.align(Alignment.CenterVertically) ) {
                                    ImageFactory.AsyncImage(
                                        thumbnailUrl = mediaItemAtIndex.mediaMetadata
                                                                       .artworkUri
                                                                       .toString(),
                                        contentDescription = "song_pos_$index",
                                        modifier = Modifier
                                            .padding(end = 5.dp)
                                            .clip(RoundedCornerShape(5.dp))
                                            .size(30.dp)
                                    )
                                }

                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .height(40.dp)
                                    .fillMaxWidth()
                            ) {
                                val colorPaletteMode by Preferences.THEME_MODE
                                val textOutline by Preferences.TEXT_OUTLINE

                                //<editor-fold defaultstate="collapsed" desc="Title">
                                Box {
                                    val titleText by remember {
                                        derivedStateOf {
                                            cleanPrefix( mediaItemAtIndex.mediaMetadata.title.toString() )
                                        }
                                    }

                                    BasicText(
                                        text = titleText,
                                        style = TextStyle(
                                            color = colorPalette.text,
                                            fontSize = typography.xxxs.semiBold.fontSize,
                                        ),
                                        maxLines = 1,
                                        modifier = Modifier.scrollingText()
                                    )
                                    BasicText(
                                        text = titleText,
                                        style = TextStyle(
                                            drawStyle = Stroke(
                                                width = 0.25f,
                                                join = StrokeJoin.Round
                                            ),
                                            color = if (!textOutline) Color.Transparent
                                            else if (colorPaletteMode == ColorPaletteMode.Light || (colorPaletteMode == ColorPaletteMode.System && (!isSystemInDarkTheme()))) Color.White.copy(
                                                0.65f
                                            )
                                            else Color.Black,
                                            fontSize = typography.xxxs.semiBold.fontSize,
                                        ),
                                        maxLines = 1,
                                        modifier = Modifier.scrollingText()
                                    )
                                }
                                //</editor-fold>
                                //<editor-fold defaultstate="collapsed" desc="Artists">
                                Box {
                                    val artistsText by remember {
                                        derivedStateOf {
                                            cleanPrefix( mediaItemAtIndex.mediaMetadata.artist.toString() )
                                        }
                                    }

                                    BasicText(
                                        text = artistsText,
                                        style = TextStyle(
                                            color = colorPalette.text,
                                            fontSize = typography.xxxs.semiBold.fontSize,
                                        ),
                                        maxLines = 1,
                                        modifier = Modifier.scrollingText()
                                    )
                                    BasicText(
                                        text = artistsText,
                                        style = TextStyle(
                                            drawStyle = Stroke(
                                                width = 0.25f,
                                                join = StrokeJoin.Round
                                            ),
                                            color =
                                                if ( !textOutline )
                                                    Color.Transparent
                                                else if (
                                                    colorPaletteMode == ColorPaletteMode.Light
                                                    || (colorPaletteMode == ColorPaletteMode.System && !isSystemInDarkTheme())
                                                )
                                                    Color.White.copy( 0.65f )
                                                else
                                                    Color.Black,
                                            fontSize = typography.xxxs.semiBold.fontSize,
                                        ),
                                        maxLines = 1,
                                        modifier = Modifier.scrollingText()
                                    )
                                }
                                //</editor-fold>
                            }
                        }
                    }

                    if ( showSongsState.value == SongsNumber.`1` )
                        ActionButton(
                            resource = Res.drawable.trash,
                            tint = Color.White,
                            // TODO: localize
                            contentDescription = "Delete from queue",
                            modifier = Modifier.weight( .07f )
                                               .size( 40.dp )
                                               .padding( vertical = 7.5.dp )
                        ) {
                            player.removeMediaItem( nextIndex )
                        }
                }
            }

            val actionsSpaceEvenly by Preferences.PLAYER_ACTION_BUTTONS_SPACED_EVENLY
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (actionsSpaceEvenly) Arrangement.SpaceEvenly else Arrangement.SpaceBetween,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
            ) {
                val showButtonPlayerVideo by Preferences.PLAYER_ACTION_TOGGLE_VIDEO
                if (showButtonPlayerVideo)
                    ActionButton(
                        resource = Res.drawable.video,
                        tint = colorPalette.accent,
                        // TODO: localize
                        contentDescription = "Toggle video mode"
                    ) {
                        player.pause()
                        showSearchEntityState.value = true
                    }

                val showButtonPlayerDiscover by Preferences.PLAYER_ACTION_DISCOVER
                if (showButtonPlayerDiscover) {
                    var discoverIsEnabled by discoverState
                    val tint = if ( discoverIsEnabled ) colorPalette.text else colorPalette.textDisabled

                    ActionButton(
                        resource = Res.drawable.star_brilliant,
                        tint = tint,
                        // TODO: localize
                        contentDescription = "Discovery",
                        onLongClick = {
                            Toaster.i(R.string.discoverinfo)
                        },
                        onClick = {
                            discoverIsEnabled = !discoverIsEnabled
                        }
                    )
                }

                val showButtonPlayerDownload by Preferences.PLAYER_ACTION_DOWNLOAD
                if (showButtonPlayerDownload) {
                    val cache: Cache = koinInject(CacheType.CACHE)
                    val isCached by remember {
                        Database.formatTable
                                .findBySongId( mediaItem.mediaId )
                                .mapNotNull { it?.contentLength }
                                .map {
                                    cache.isCached(mediaItem.mediaId, 0, it)
                                }
                    }.collectAsStateWithLifecycle(false)
                    val isDownloaded = isDownloadedSong( mediaItem.mediaId )
                    val icon = if( isDownloaded )
                        when( getDownloadState(mediaItem.mediaId) ) {
                            Download.STATE_DOWNLOADING -> Res.drawable.download_progress
                            Download.STATE_COMPLETED -> Res.drawable.downloaded
                            else -> Res.drawable.download
                        }
                    else
                        Res.drawable.download
                    val tint = if( isCached || isDownloaded ) colorPalette.accent else Color.Gray

                    ActionButton(
                        resource = icon,
                        tint = tint,
                        // TODO: localize
                        contentDescription = "Download media",
                        onLongClick = {
                            manageDownload(context, mediaItem, true)
                        },
                        onClick = {
                            manageDownload(context, mediaItem, false)
                        }
                    )
                }

                val showButtonPlayerAddToPlaylist by Preferences.PLAYER_ACTION_ADD_TO_PLAYLIST
                if (showButtonPlayerAddToPlaylist) {
                    val showPlaylistIndicator by Preferences.SHOW_PLAYLIST_INDICATOR
                    val colorPaletteName by Preferences.COLOR_PALETTE
                    val isSongMappedToPlaylist by remember( mediaItem.mediaId ) {
                        Database.songPlaylistMapTable.isMapped( mediaItem.mediaId )
                    }.collectAsState( false, Dispatchers.IO )
                    val iconColor = if ( isSongMappedToPlaylist && showPlaylistIndicator ) {
                        if ( colorPaletteName == ColorPaletteName.PureBlack )
                            Color.Black
                        else
                            colorPalette.text
                    } else
                        colorPalette.accent
                    val modifier = if( isSongMappedToPlaylist && showPlaylistIndicator )
                        Modifier.background( colorPalette.accent, CircleShape )
                    else
                        Modifier

                    ActionButton(
                        resource = Res.drawable.add_in_playlist,
                        tint = iconColor,
                        // TODO: localize
                        contentDescription = "Add to playlist",
                        modifier = modifier
                    ) {
                        menuState.display {
                            AddToPlaylistPlayerMenu(
                                navController = navController,
                                onDismiss = menuState::hide,
                                mediaItem = mediaItem,
                                onClosePlayer = onDismiss,
                            )
                        }
                    }
                }

                val showButtonPlayerLoop by Preferences.PLAYER_ACTION_LOOP
                if (showButtonPlayerLoop) {
                    var queueLoopType by queueLoopState
                    val effectRotationEnabled by Preferences.ROTATION_EFFECT

                    ActionButton(
                        resource = queueLoopType.iconId,
                        tint = colorPalette.accent,
                        // TODO: localize
                        contentDescription = "Repeat mode"
                    ) {
                        queueLoopType = queueLoopType.next()
                        if (effectRotationEnabled)
                            rotateState.value = !rotateState.value
                    }
                }

                val showButtonPlayerShuffle by Preferences.PLAYER_ACTION_SHUFFLE
                if (showButtonPlayerShuffle) {
                    val isEnabled by Preferences.PLAYER_SHUFFLE
                    val tint = if( isEnabled ) colorPalette.accent else Color.Gray

                    ActionButton(
                        resource = Res.drawable.shuffle,
                        tint = tint,
                        // TODO: localize
                        contentDescription = "Shuffle",
                        onLongClick = player::shuffleQueue,
                        onClick = player::toggleShuffleMode
                    )
                }

                val showButtonPlayerLyrics by Preferences.PLAYER_ACTION_SHOW_LYRICS
                if (showButtonPlayerLyrics) {
                    val tint = if ( isShowingLyrics ) colorPalette.accent else Color.Gray
                    ActionButton(
                        resource = Res.drawable.song_lyrics,
                        tint = tint,
                        // TODO: localize
                        contentDescription = "Show/hide lyrics"
                    ) {
                        if( isShowingVisualizer )
                            isShowingVisualizer = !isShowingVisualizer
                        isShowingLyrics = !isShowingLyrics
                    }
                }

                val playerType by Preferences.PLAYER_TYPE
                val showThumbnail by Preferences.PLAYER_SHOW_THUMBNAIL
                if (!isLandscape || ((playerType == PlayerType.Essential) && !showThumbnail)) {
                    val expandedPlayerToggle by Preferences.PLAYER_ACTION_TOGGLE_EXPAND
                    var expandedPlayer by expandPlayerState
                    val tint = if ( expandedPlayer ) colorPalette.accent else Color.Gray

                    if (expandedPlayerToggle && !showLyricsThumbnail)
                        ActionButton(
                            resource = Res.drawable.maximize,
                            tint = tint,
                            // TODO: localize
                            contentDescription = "Show/hide thumbnail"
                        ) {
                            expandedPlayer = !expandedPlayer
                        }
                }

                val visualizerEnabled by Preferences.PLAYER_VISUALIZER
                if (visualizerEnabled) {
                    val tint = if ( isShowingVisualizer ) colorPalette.text else colorPalette.textDisabled
                    ActionButton(
                        resource = Res.drawable.sound_effect,
                        tint = tint,
                        // TODO: localize
                        contentDescription = "Show/Hide visualizer"
                    ) {
                        if (isShowingLyrics)
                            isShowingLyrics = !isShowingLyrics
                        isShowingVisualizer = !isShowingVisualizer
                    }
                }

                val showButtonPlayerSleepTimer by Preferences.PLAYER_ACTION_SLEEP_TIMER
                if (showButtonPlayerSleepTimer) {
                    val sleepTimerMillisLeft: Long? by player.sleepTimerRemaining().collectAsState( null )
                    val tint = if (sleepTimerMillisLeft != null) colorPalette.accent else Color.Gray

                    ActionButton(
                        resource = Res.drawable.sleep,
                        tint = tint,
                        // TODO: localize
                        contentDescription = "Show/Hide sleep timer"
                    ) {
                        showSleepTimerState.value = true
                    }
                }

                val showButtonPlayerSystemEqualizer by Preferences.PLAYER_ACTION_OPEN_EQUALIZER
                if (showButtonPlayerSystemEqualizer) {
                    val activityResultLauncher =
                        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

                    ActionButton(
                        resource = Res.drawable.equalizer,
                        tint = colorPalette.accent,
                        // TODO: localize
                        contentDescription = "Equalizer"
                    ) {
                        try {
                            activityResultLauncher.launch(
                                Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                                    putExtra(
                                        AudioEffect.EXTRA_AUDIO_SESSION,
                                        player.audioSessionId
                                    )
                                    putExtra(
                                        AudioEffect.EXTRA_PACKAGE_NAME,
                                        context.packageName
                                    )
                                    putExtra(
                                        AudioEffect.EXTRA_CONTENT_TYPE,
                                        AudioEffect.CONTENT_TYPE_MUSIC
                                    )
                                }
                            )
                        } catch (e: ActivityNotFoundException) {
                            Toaster.e( R.string.info_not_find_application_audio )
                        }
                    }
                }

                val showButtonPlayerStartRadio by Preferences.PLAYER_ACTION_START_RADIO
                if (showButtonPlayerStartRadio)
                    ActionButton(
                        resource = Res.drawable.radio,
                        tint = colorPalette.accent,
                        // TODO: localize
                        contentDescription = "Radio"
                    ) {
                        player.startRadio( mediaItem )
                    }

                val showPlaybackSpeedButton by Preferences.AUDIO_SPEED
                if( showPlaybackSpeedButton ) {
                    val playbackSpeed = remember { PlaybackSpeed() }

                    playbackSpeed.Render()
                    playbackSpeed.ToolBarButton()
                }

                val showButtonPlayerArrow by Preferences.PLAYER_ACTION_OPEN_QUEUE_ARROW
                if (showButtonPlayerArrow)
                    ActionButton(
                        resource = Res.drawable.chevron_up,
                        tint = colorPalette.accent,
                        // TODO: localize
                        contentDescription = "Open queue"
                    ) {
                        showQueue = true
                    }

                val showButtonPlayerMenu by Preferences.PLAYER_ACTION_SHOW_MENU
                if( showButtonPlayerMenu || isLandscape ) {
                    val isInLandscape = isLandscape

                    ActionButton(
                        resource = Res.drawable.ellipsis_vertical,
                        tint = colorPalette.accent,
                        // TODO: localize
                        contentDescription = "Open menu",
                        modifier = Modifier.graphicsLayer {
                            rotationZ = if (isInLandscape) 90f else 0f
                        }
                    ) {
                        menuState.display {
                            PlayerMenu(
                                navController = navController,
                                onDismiss = menuState::hide,
                                mediaItem = mediaItem,
                                onClosePlayer = onDismiss
                            )
                        }
                    }
                }
            }
        }
    }
}