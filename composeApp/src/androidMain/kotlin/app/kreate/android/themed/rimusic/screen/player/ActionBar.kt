package app.kreate.android.themed.rimusic.screen.player

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.R
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.appRunningInBackground
import it.fast4x.rimusic.cleanPrefix
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.ColorPaletteMode
import it.fast4x.rimusic.enums.ColorPaletteName
import it.fast4x.rimusic.enums.PlayerBackgroundColors
import it.fast4x.rimusic.enums.PlayerType
import it.fast4x.rimusic.enums.QueueLoopType
import it.fast4x.rimusic.enums.SongsNumber
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.themed.AddToPlaylistPlayerMenu
import it.fast4x.rimusic.ui.components.themed.DownloadStateIconButton
import it.fast4x.rimusic.ui.components.themed.IconButton
import it.fast4x.rimusic.ui.components.themed.PlayerMenu
import it.fast4x.rimusic.ui.screens.player.LaunchedEffectScrollToPage
import it.fast4x.rimusic.utils.actionspacedevenlyKey
import it.fast4x.rimusic.utils.addNext
import it.fast4x.rimusic.utils.blackgradientKey
import it.fast4x.rimusic.utils.colorPaletteModeKey
import it.fast4x.rimusic.utils.colorPaletteNameKey
import it.fast4x.rimusic.utils.conditional
import it.fast4x.rimusic.utils.disableScrollingTextKey
import it.fast4x.rimusic.utils.effectRotationKey
import it.fast4x.rimusic.utils.expandedplayertoggleKey
import it.fast4x.rimusic.utils.getDownloadState
import it.fast4x.rimusic.utils.isDownloadedSong
import it.fast4x.rimusic.utils.isLandscape
import it.fast4x.rimusic.utils.manageDownload
import it.fast4x.rimusic.utils.mediaItems
import it.fast4x.rimusic.utils.miniQueueExpandedKey
import it.fast4x.rimusic.utils.playAtIndex
import it.fast4x.rimusic.utils.playerBackgroundColorsKey
import it.fast4x.rimusic.utils.playerTypeKey
import it.fast4x.rimusic.utils.playlistindicatorKey
import it.fast4x.rimusic.utils.rememberPreference
import it.fast4x.rimusic.utils.semiBold
import it.fast4x.rimusic.utils.showButtonPlayerAddToPlaylistKey
import it.fast4x.rimusic.utils.showButtonPlayerArrowKey
import it.fast4x.rimusic.utils.showButtonPlayerDiscoverKey
import it.fast4x.rimusic.utils.showButtonPlayerDownloadKey
import it.fast4x.rimusic.utils.showButtonPlayerLoopKey
import it.fast4x.rimusic.utils.showButtonPlayerLyricsKey
import it.fast4x.rimusic.utils.showButtonPlayerMenuKey
import it.fast4x.rimusic.utils.showButtonPlayerShuffleKey
import it.fast4x.rimusic.utils.showButtonPlayerSleepTimerKey
import it.fast4x.rimusic.utils.showButtonPlayerStartRadioKey
import it.fast4x.rimusic.utils.showButtonPlayerSystemEqualizerKey
import it.fast4x.rimusic.utils.showButtonPlayerVideoKey
import it.fast4x.rimusic.utils.showNextSongsInPlayerKey
import it.fast4x.rimusic.utils.showPlaybackSpeedButtonKey
import it.fast4x.rimusic.utils.showalbumcoverKey
import it.fast4x.rimusic.utils.showlyricsthumbnailKey
import it.fast4x.rimusic.utils.showsongsKey
import it.fast4x.rimusic.utils.showthumbnailKey
import it.fast4x.rimusic.utils.shuffleQueue
import it.fast4x.rimusic.utils.swipeUpQueueKey
import it.fast4x.rimusic.utils.tapqueueKey
import it.fast4x.rimusic.utils.textoutlineKey
import it.fast4x.rimusic.utils.transparentBackgroundPlayerActionBarKey
import it.fast4x.rimusic.utils.visualizerEnabledKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import me.knighthat.coil.ImageCacheFactory
import me.knighthat.component.player.PlaybackSpeed
import me.knighthat.utils.Toaster

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
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current

    val mediaItem = binder?.player?.currentMediaItem ?: return
    val mediaItems = binder.player.mediaItems

    val playerBackgroundColors by rememberPreference( playerBackgroundColorsKey, PlayerBackgroundColors.BlurredCoverColor )
    val blackGradient by rememberPreference( blackgradientKey, false )
    val showLyricsThumbnail by rememberPreference(showlyricsthumbnailKey, false)
    val showNextSongsInPlayer by rememberPreference( showNextSongsInPlayerKey, false )
    val miniQueueExpanded by rememberPreference( miniQueueExpandedKey, true )
    val tapQueue by rememberPreference( tapqueueKey, true )
    val transparentBackgroundActionBarPlayer by rememberPreference( transparentBackgroundPlayerActionBarKey, false )
    val swipeUpQueue by rememberPreference( swipeUpQueueKey, true )
    val showSongs by rememberPreference( showsongsKey, SongsNumber.`2` )
    val disableScrollingText by rememberPreference( disableScrollingTextKey, false )

    var showQueue by showQueueState
    var isShowingVisualizer by showVisualizerState
    var isShowingLyrics by showLyricsState

    Row(
        modifier = Modifier
            .align(if (isLandscape) Alignment.BottomEnd else Alignment.BottomCenter)
            .requiredHeight(if (showNextSongsInPlayer && (showLyricsThumbnail || (!isShowingLyrics || miniQueueExpanded))) 90.dp else 50.dp)
            .fillMaxWidth(if (isLandscape) 0.8f else 1f)
            .clickable( enabled = tapQueue ) {
                showQueue = true
            }
            .background(
                colorPalette().background2.copy(
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
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .background(
                            colorPalette().background2.copy(
                                alpha = if (transparentBackgroundActionBarPlayer) 0.0f else 0.3f
                            )
                        )
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth()
                ) {
                    val nextMediaItemIndex = binder.player.nextMediaItemIndex
                    val pagerStateQueue =
                        rememberPagerState(pageCount = { mediaItems.size })
                    val scope = rememberCoroutineScope()
                    val fling = PagerDefaults.flingBehavior(
                        state = pagerStateQueue,
                        snapPositionalThreshold = 0.15f,
                        pagerSnapDistance = PagerSnapDistance.atMost(showSongs.toInt())
                    )
                    pagerStateQueue.LaunchedEffectScrollToPage(binder.player.currentMediaItemIndex + 1)

                    Row(
                        modifier = Modifier
                            .padding(vertical = 7.5.dp)
                            .weight(0.07f)
                            .conditional(pagerStateQueue.currentPage == binder.player.currentMediaItemIndex) {
                                padding(
                                    horizontal = 3.dp
                                )
                            }
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (pagerStateQueue.currentPage > binder.player.currentMediaItemIndex) R.drawable.chevron_forward
                                else if (pagerStateQueue.currentPage == binder.player.currentMediaItemIndex) R.drawable.play
                                else R.drawable.chevron_back
                            ),
                            contentDescription = null,
                            modifier = Modifier
                                .size(25.dp)
                                .clip(CircleShape)
                                .clickable(
                                    indication = ripple(bounded = false),
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = {
                                        scope.launch {
                                            if (!appRunningInBackground) {
                                                pagerStateQueue.animateScrollToPage(
                                                    binder.player.currentMediaItemIndex + 1
                                                )
                                            } else {
                                                pagerStateQueue.scrollToPage(binder.player.currentMediaItemIndex + 1)
                                            }
                                        }
                                    }
                                ),
                            tint = colorPalette().accent
                        )
                    }

                    val threePagesPerViewport = object : PageSize {
                        override fun Density.calculateMainAxisPageSize(
                            availableSpace: Int,
                            pageSpacing: Int
                        ): Int {
                            return if (showSongs == SongsNumber.`1`) availableSpace else ((availableSpace - 2 * pageSpacing) / (showSongs.toInt()))
                        }
                    }

                    HorizontalPager(
                        state = pagerStateQueue,
                        pageSize = threePagesPerViewport,
                        pageSpacing = 10.dp,
                        flingBehavior = fling,
                        modifier = Modifier.weight(1f)
                    ) { index ->
                        val mediaItemAtIndex by remember {
                            derivedStateOf { binder.player.getMediaItemAt( pagerStateQueue.currentPage ) }
                        }

                        Row(
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .combinedClickable(
                                    onClick = {
                                        binder.player.playAtIndex(index)
                                    },
                                    onLongClick = {
                                        if (index < mediaItems.size) {
                                            binder.player.addNext( mediaItemAtIndex )
                                            scope.launch {
                                                if (!appRunningInBackground) {
                                                    pagerStateQueue.animateScrollToPage(
                                                        binder.player.currentMediaItemIndex + 1
                                                    )
                                                } else {
                                                    pagerStateQueue.scrollToPage(binder.player.currentMediaItemIndex + 1)
                                                }
                                            }
                                            Toaster.s( R.string.addednext )
                                        }
                                    }
                                )
                        ) {
                            val showAlbumCover by rememberPreference( showalbumcoverKey, true )
                            if ( showAlbumCover )
                                Box( Modifier.align(Alignment.CenterVertically) ) {
                                    ImageCacheFactory.Thumbnail(
                                        thumbnailUrl = binder.player
                                                             .getMediaItemAt( index )
                                                             .mediaMetadata
                                                             .artworkUri
                                                             .toString(),
                                        contentDescription = "song_pos_$index",
                                        modifier = Modifier.padding( end = 5.dp )
                                                           .clip( RoundedCornerShape(5.dp) )
                                                           .size( 30.dp )
                                    )
                                }

                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.height( 40.dp )
                                                   .fillMaxWidth()
                            ) {
                                val colorPaletteMode by rememberPreference( colorPaletteModeKey, ColorPaletteMode.Dark )
                                val textOutline by rememberPreference( textoutlineKey, false )

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
                                            color = colorPalette().text,
                                            fontSize = typography().xxxs.semiBold.fontSize,
                                        ),
                                        maxLines = 1,
                                        modifier = Modifier.conditional(!disableScrollingText) { basicMarquee() }
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
                                            fontSize = typography().xxxs.semiBold.fontSize,
                                        ),
                                        maxLines = 1,
                                        modifier = Modifier.conditional(!disableScrollingText) { basicMarquee() }
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
                                            color = colorPalette().text,
                                            fontSize = typography().xxxs.semiBold.fontSize,
                                        ),
                                        maxLines = 1,
                                        modifier = Modifier.conditional(!disableScrollingText) { basicMarquee() }
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
                                            fontSize = typography().xxxs.semiBold.fontSize,
                                        ),
                                        maxLines = 1,
                                        modifier = Modifier.conditional( !disableScrollingText ) { basicMarquee() }
                                    )
                                }
                                //</editor-fold>
                            }
                        }
                    }
                    if ( showSongs == SongsNumber.`1` )
                        IconButton(
                            icon = R.drawable.trash,
                            color = Color.White,
                            enabled = true,
                            onClick = {
                                binder.player.removeMediaItem( nextMediaItemIndex )
                            },
                            modifier = Modifier.weight( .07f )
                                               .size( 40.dp )
                                               .padding( vertical = 7.5.dp )
                        )
                }
            }

            val actionsSpaceEvenly by rememberPreference( actionspacedevenlyKey, false )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (actionsSpaceEvenly) Arrangement.SpaceEvenly else Arrangement.SpaceBetween,
                modifier = Modifier.padding( horizontal = 12.dp )
                                   .fillMaxWidth()
            ) {
                val showButtonPlayerVideo by rememberPreference( showButtonPlayerVideoKey, false )
                if (showButtonPlayerVideo)
                    IconButton(
                        icon = R.drawable.video,
                        color = colorPalette().accent,
                        onClick = {
                            binder.callPause {}
                            showSearchEntityState.value = true
                        },
                        modifier = Modifier.size( 24.dp )
                    )

                val showButtonPlayerDiscover by rememberPreference( showButtonPlayerDiscoverKey, false )
                if (showButtonPlayerDiscover) {
                    var discoverIsEnabled by discoverState

                    IconButton(
                        icon = R.drawable.star_brilliant,
                        color = if (discoverIsEnabled) colorPalette().text else colorPalette().textDisabled,
                        onClick = {},
                        modifier = Modifier.size( 24.dp )
                                           .combinedClickable(
                                               onClick = { discoverIsEnabled = !discoverIsEnabled },
                                               onLongClick = {
                                                   Toaster.i( R.string.discoverinfo )
                                               }
                                           )
                    )
                }

                val showButtonPlayerDownload by rememberPreference( showButtonPlayerDownloadKey, true )
                if (showButtonPlayerDownload) {
                    val isDownloaded = isDownloadedSong( mediaItem.mediaId )

                    DownloadStateIconButton(
                        icon = if (isDownloaded) R.drawable.downloaded else R.drawable.download,
                        color = if (isDownloaded) colorPalette().accent else Color.Gray,
                        downloadState = getDownloadState(mediaItem.mediaId),
                        onClick = {
                            manageDownload(
                                context = context,
                                mediaItem = mediaItem,
                                downloadState = isDownloaded
                            )
                        },
                        onCancelButtonClicked = {
                            manageDownload(
                                context = context,
                                mediaItem = mediaItem,
                                downloadState = true
                            )
                        },
                        modifier = Modifier.size( 24.dp )
                    )
                }

                val showButtonPlayerAddToPlaylist by rememberPreference( showButtonPlayerAddToPlaylistKey, true )
                if (showButtonPlayerAddToPlaylist) {
                    val showPlaylistIndicator by rememberPreference( playlistindicatorKey, false )
                    val colorPaletteName by rememberPreference( colorPaletteNameKey, ColorPaletteName.Dynamic )
                    val color = colorPalette()
                    val isSongMappedToPlaylist by remember( mediaItem.mediaId ) {
                        Database.songPlaylistMapTable.isMapped( mediaItem.mediaId )
                    }.collectAsState( false, Dispatchers.IO )
                    val iconColor by remember {
                        derivedStateOf {
                            if ( isSongMappedToPlaylist && showPlaylistIndicator )
                                if ( colorPaletteName == ColorPaletteName.PureBlack )
                                    Color.Black
                                else
                                    color.text
                            else
                                color.accent
                        }
                    }

                    IconButton(
                        icon = R.drawable.add_in_playlist,
                        color = iconColor,
                        onClick = {
                            menuState.display {
                                AddToPlaylistPlayerMenu(
                                    navController = navController,
                                    onDismiss = menuState::hide,
                                    mediaItem = mediaItem,
                                    binder = binder,
                                    onClosePlayer = onDismiss,
                                )
                            }
                        },
                        modifier = Modifier.size( 24.dp )
                                           .conditional( isSongMappedToPlaylist && showPlaylistIndicator ) {
                                               background( color.accent, CircleShape ).padding( all = 5.dp )
                                           }
                    )
                }

                val showButtonPlayerLoop by rememberPreference( showButtonPlayerLoopKey, true )
                if (showButtonPlayerLoop) {
                    var queueLoopType by queueLoopState
                    val effectRotationEnabled by rememberPreference( effectRotationKey, true )

                    IconButton(
                        icon = queueLoopType.iconId,
                        color = colorPalette().accent,
                        onClick = {
                            queueLoopType = queueLoopType.next()
                            if (effectRotationEnabled)
                                rotateState.value = !rotateState.value
                        },
                        modifier = Modifier.size( 24.dp )
                    )
                }

                val showButtonPlayerShuffle by rememberPreference( showButtonPlayerShuffleKey, true )
                if (showButtonPlayerShuffle)
                    IconButton(
                        icon = R.drawable.shuffle,
                        color = colorPalette().accent,
                        onClick = binder.player::shuffleQueue,
                        modifier = Modifier.size( 24.dp )
                    )

                val showButtonPlayerLyrics by rememberPreference( showButtonPlayerLyricsKey, true )
                if (showButtonPlayerLyrics)
                    IconButton(
                        icon = R.drawable.song_lyrics,
                        color = if ( isShowingLyrics ) colorPalette().accent else Color.Gray,
                        enabled = true,
                        onClick = {
                            if( isShowingVisualizer )
                                isShowingVisualizer = !isShowingVisualizer
                            isShowingLyrics = !isShowingLyrics
                        },
                        modifier = Modifier.size( 24.dp )
                    )

                val playerType by rememberPreference( playerTypeKey, PlayerType.Essential )
                val showThumbnail by rememberPreference( showthumbnailKey, true )
                if (!isLandscape || ((playerType == PlayerType.Essential) && !showThumbnail)) {
                    val expandedPlayerToggle by rememberPreference( expandedplayertoggleKey, true )
                    var expandedPlayer by expandPlayerState

                    if (expandedPlayerToggle && !showLyricsThumbnail)
                        IconButton(
                            icon = R.drawable.maximize,
                            color = if ( expandedPlayer ) colorPalette().accent else Color.Gray,
                            onClick = {
                                expandedPlayer = !expandedPlayer
                            },
                            modifier = Modifier.size( 20.dp )
                        )
                }

                val visualizerEnabled by rememberPreference( visualizerEnabledKey, false )
                if (visualizerEnabled)
                    IconButton(
                        icon = R.drawable.sound_effect,
                        color = if ( isShowingVisualizer ) colorPalette().text else colorPalette().textDisabled,
                        onClick = {
                            if (isShowingLyrics)
                                isShowingLyrics = !isShowingLyrics
                            isShowingVisualizer = !isShowingVisualizer
                        },
                        modifier = Modifier.size( 24.dp )
                    )


                val showButtonPlayerSleepTimer by rememberPreference( showButtonPlayerSleepTimerKey, false )
                if (showButtonPlayerSleepTimer) {
                    val sleepTimerMillisLeft: Long? by
                        (binder.sleepTimerMillisLeft ?: flowOf(null)).collectAsState( null )

                    IconButton(
                        icon = R.drawable.sleep,
                        color = if (sleepTimerMillisLeft != null) colorPalette().accent else Color.Gray,
                        onClick = {
                            showSleepTimerState.value = true
                        },
                        modifier = Modifier.size( 24.dp )
                    )
                }

                val showButtonPlayerSystemEqualizer by rememberPreference( showButtonPlayerSystemEqualizerKey, false )
                if (showButtonPlayerSystemEqualizer) {
                    val activityResultLauncher =
                        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

                    IconButton(
                        icon = R.drawable.equalizer,
                        color = colorPalette().accent,
                        onClick = {
                            try {
                                activityResultLauncher.launch(
                                    Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                                        putExtra(
                                            AudioEffect.EXTRA_AUDIO_SESSION,
                                            binder.player.audioSessionId
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
                        },
                        modifier = Modifier.size( 20.dp )
                    )
                }

                val showButtonPlayerStartRadio by rememberPreference( showButtonPlayerStartRadioKey, false )
                if (showButtonPlayerStartRadio)
                    IconButton(
                        icon = R.drawable.radio,
                        color = colorPalette().accent,
                        onClick = {
                            binder.startRadio( mediaItem )
                        },
                        modifier = Modifier.size( 24.dp )
                    )

                val showPlaybackSpeedButton by rememberPreference( showPlaybackSpeedButtonKey, false )
                if( showPlaybackSpeedButton ) {
                    val playbackSpeed = remember { PlaybackSpeed() }

                    playbackSpeed.Render()
                    playbackSpeed.ToolBarButton()
                }

                val showButtonPlayerArrow by rememberPreference( showButtonPlayerArrowKey, true )
                if (showButtonPlayerArrow)
                    IconButton(
                        icon = R.drawable.chevron_up,
                        color = colorPalette().accent,
                        enabled = true,
                        onClick = {
                            showQueue = true
                        },
                        modifier = Modifier
                            //.padding(end = 12.dp)
                            .size(24.dp),
                    )

                val showButtonPlayerMenu by rememberPreference( showButtonPlayerMenuKey, false )
                if( showButtonPlayerMenu || isLandscape ) {
                    val isInLandscape = isLandscape

                    IconButton(
                        icon = R.drawable.ellipsis_vertical,
                        color = colorPalette().accent,
                        onClick = {
                            menuState.display {
                                PlayerMenu(
                                    navController = navController,
                                    onDismiss = menuState::hide,
                                    mediaItem = mediaItem,
                                    binder = binder,
                                    onClosePlayer = onDismiss,
                                    disableScrollingText = disableScrollingText
                                )
                            }
                        },
                        modifier = Modifier.size( 24.dp )
                                           .graphicsLayer {
                                               rotationZ = if ( isInLandscape ) 90f else 0f
                                           }
                    )
                }
            }
        }
    }
}