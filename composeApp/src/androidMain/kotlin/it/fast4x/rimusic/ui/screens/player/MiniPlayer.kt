package it.fast4x.rimusic.ui.screens.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.Preferences
import app.kreate.android.R
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.cleanPrefix
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.BackgroundProgress
import it.fast4x.rimusic.enums.MiniPlayerType
import it.fast4x.rimusic.service.modern.PlayerServiceModern
import it.fast4x.rimusic.thumbnailShape
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.themed.NowPlayingSongIndicator
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.favoritesIcon
import it.fast4x.rimusic.ui.styling.favoritesOverlay
import it.fast4x.rimusic.utils.DisposableListener
import it.fast4x.rimusic.utils.conditional
import it.fast4x.rimusic.utils.getLikedIcon
import it.fast4x.rimusic.utils.getUnlikedIcon
import it.fast4x.rimusic.utils.intent
import it.fast4x.rimusic.utils.isExplicit
import it.fast4x.rimusic.utils.playNext
import it.fast4x.rimusic.utils.playPrevious
import it.fast4x.rimusic.utils.positionAndDurationState
import it.fast4x.rimusic.utils.semiBold
import it.fast4x.rimusic.utils.shouldBePlaying
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.knighthat.coil.ImageCacheFactory
import me.knighthat.sync.YouTubeSync
import me.knighthat.utils.Toaster
import kotlin.math.absoluteValue

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalFoundationApi::class,
    ExperimentalFoundationApi::class, ExperimentalFoundationApi::class,
    ExperimentalFoundationApi::class)
@Composable
fun MiniPlayer(
    showPlayer: () -> Unit,
    hidePlayer: () -> Unit,
    navController: NavController? = null,
) {
    val binder = LocalPlayerServiceBinder.current
    binder?.player ?: return

    val context = LocalContext.current

    var nullableMediaItem by remember {
        mutableStateOf(
            binder.player.currentMediaItem,
            neverEqualPolicy()
        )
    }
    var shouldBePlaying by remember { mutableStateOf(binder.player.shouldBePlaying) }
    val hapticFeedback = LocalHapticFeedback.current

    var playerError by remember {
        mutableStateOf<PlaybackException?>(binder.player.playerError)
    }

    binder.player.DisposableListener {
        object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                nullableMediaItem = mediaItem
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                shouldBePlaying = if (playerError == null) binder.player.shouldBePlaying else false
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                playerError = binder.player.playerError
                shouldBePlaying = if (playerError == null) binder.player.shouldBePlaying else false
            }

            override fun onPlayerError(playbackException: PlaybackException) {
                playerError = playbackException
            }
        }
    }

    val mediaItem = nullableMediaItem ?: return

    playerError?.let { PlayerError(error = it) }

    val isSongLiked by remember( mediaItem.mediaId ) {
        Database.songTable
                .isLiked( mediaItem.mediaId )
                .distinctUntilChanged()
    }.collectAsState( false, Dispatchers.IO )

    var miniPlayerType by Preferences.MINI_PLAYER_TYPE

    fun toggleLike() {
        CoroutineScope( Dispatchers.IO ).launch {
            YouTubeSync.toggleSongLike( context, mediaItem )
        }
    }

    val positionAndDuration by binder.player.positionAndDurationState()

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd)
                if (miniPlayerType == MiniPlayerType.Essential)
                    toggleLike()
                else
                    binder.player.seekToPrevious()
            else
                if (value == SwipeToDismissBoxValue.EndToStart)
                    binder.player.seekToNext()

            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

            return@rememberSwipeToDismissBoxState false
        }
    )
    val backgroundProgress by Preferences.MINI_PLAYER_PROGRESS_BAR
    val effectRotationEnabled by Preferences.ROTATION_EFFECT
    val shouldBePlayingTransition = updateTransition(shouldBePlaying, label = "shouldBePlaying")
    val playPauseRoundness by shouldBePlayingTransition.animateDp(
        transitionSpec = { tween(durationMillis = 100, easing = LinearEasing) },
        label = "playPauseRoundness",
        targetValueByState = { if (it) 24.dp else 12.dp }
    )

    var isRotated by rememberSaveable { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isRotated) 360F else 0f,
        animationSpec = tween(durationMillis = 200), label = ""
    )
    val disableClosingPlayerSwipingDown by Preferences.MINI_DISABLE_SWIPE_DOWN_TO_DISMISS

    val disableScrollingText by Preferences.SCROLLING_TEXT_DISABLED

    SwipeToDismissBox(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp)),
        state = dismissState,
        backgroundContent = {
            /*
            val color by animateColorAsState(
                targetValue = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.primaryContainer
                    SwipeToDismissBoxValue.Settled -> Color.Transparent
                },
                label = "background"
            )
             */

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorPalette().background1)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.StartToEnd -> Arrangement.Start
                    SwipeToDismissBoxValue.EndToStart -> Arrangement.End
                    SwipeToDismissBoxValue.Settled -> Arrangement.Center
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (dismissState.targetValue) {
                        SwipeToDismissBoxValue.StartToEnd -> {
                            if (miniPlayerType == MiniPlayerType.Modern)
                                ImageVector.vectorResource(R.drawable.play_skip_back)
                            else if ( isSongLiked )
                                ImageVector.vectorResource(R.drawable.heart)
                            else
                                ImageVector.vectorResource(R.drawable.heart_outline)
                        }
                        SwipeToDismissBoxValue.EndToStart ->  ImageVector.vectorResource(R.drawable.play_skip_forward)
                        SwipeToDismissBoxValue.Settled ->  ImageVector.vectorResource(R.drawable.play)
                    },
                    contentDescription = null,
                    tint = colorPalette().iconButtonPlayer,
                )
            }
        }
    ) {
        val colorPalette = colorPalette()
        /***** */
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .combinedClickable(
                    onLongClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onClick = {
                        //if (showPlayer != null)
                        showPlayer()
                        //else
                        //    navController?.navigate("player")
                    }
                )
                //.clickable(onClick = showPlayer)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount ->
                            if (dragAmount < 0) showPlayer()
                            else if (dragAmount > 20) {
                                if (!disableClosingPlayerSwipingDown) {
                                    binder.stopRadio()
                                    binder.player.clearMediaItems()
                                    hidePlayer()
                                    runCatching {
                                        context.stopService(context.intent<PlayerServiceModern>())
                                    }
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                } else
                                    Toaster.i( R.string.player_swiping_down_is_disabled )
                            }
                        }
                    )
                }
                .background(colorPalette().background2)
                .fillMaxWidth()
                .drawBehind {
                    if (backgroundProgress == BackgroundProgress.Both || backgroundProgress == BackgroundProgress.MiniPlayer) {
                        drawRect(
                            color = colorPalette.favoritesOverlay,
                            topLeft = Offset.Zero,
                            size = Size(
                                width = positionAndDuration.first.toFloat() /
                                        positionAndDuration.second.absoluteValue * size.width,
                                height = size.maxDimension
                            )
                        )
                    }
                }
        ) {

            Spacer(
                modifier = Modifier
                    .width(2.dp)
            )

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.height( Dimensions.miniPlayerHeight )
            ) {
                ImageCacheFactory.Thumbnail(
                    thumbnailUrl = mediaItem.mediaMetadata.artworkUri?.toString(),
                    contentScale = ContentScale.FillHeight,
                    modifier = Modifier.clip( thumbnailShape() )
                                       .size( 48.dp )
                )
                NowPlayingSongIndicator(mediaItem.mediaId, binder.player)
            }

            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .height(Dimensions.miniPlayerHeight)
                    .weight(1f)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if ( mediaItem.isExplicit )
                        it.fast4x.rimusic.ui.components.themed.IconButton(
                            icon = R.drawable.explicit,
                            color = colorPalette().text,
                            enabled = true,
                            onClick = {},
                            modifier = Modifier
                                .size(14.dp)
                        )
                    BasicText(
                        text = cleanPrefix( mediaItem.mediaMetadata.title.toString() ),
                        style = typography().xxs.semiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                    )
                }

                BasicText(
                    text = cleanPrefix( mediaItem.mediaMetadata.artist.toString() ),
                    style = typography().xxs.semiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .conditional(!disableScrollingText) { basicMarquee(iterations = Int.MAX_VALUE) }
                )
            }

            Spacer(
                modifier = Modifier
                    .width(2.dp)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(Dimensions.miniPlayerHeight)
            ) {
               if (miniPlayerType == MiniPlayerType.Essential)
                it.fast4x.rimusic.ui.components.themed.IconButton(
                    icon = R.drawable.play_skip_back,
                    color = colorPalette().iconButtonPlayer,
                    onClick = {
                        binder.player.playPrevious()
                        if (effectRotationEnabled) isRotated = !isRotated
                    },
                    modifier = Modifier
                        .rotate(rotationAngle)
                        .padding(horizontal = 2.dp, vertical = 8.dp)
                        .size(24.dp)
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(playPauseRoundness))
                        .clickable {
                            if (shouldBePlaying) {
                                binder.gracefulPause()
                            } else {
                                binder.gracefulPlay()
                            }
                            if (effectRotationEnabled) isRotated = !isRotated
                        }
                        .background(colorPalette().background2)
                        .size(42.dp)
                ) {
                    Image(
                        painter = painterResource(if (shouldBePlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette().iconButtonPlayer),
                        modifier = Modifier
                            .rotate(rotationAngle)
                            .align(Alignment.Center)
                            .size(24.dp)
                    )
                }
               if (miniPlayerType == MiniPlayerType.Essential)
                it.fast4x.rimusic.ui.components.themed.IconButton(
                    icon = R.drawable.play_skip_forward,
                    color = colorPalette().iconButtonPlayer,
                    onClick = {
                        binder.player.playNext()
                        if (effectRotationEnabled) isRotated = !isRotated
                    },
                    modifier = Modifier
                        .rotate(rotationAngle)
                        .padding(horizontal = 2.dp, vertical = 8.dp)
                        .size(24.dp)
                )
                if (miniPlayerType == MiniPlayerType.Modern)
                 it.fast4x.rimusic.ui.components.themed.IconButton(
                     icon = if( isSongLiked ) getLikedIcon() else getUnlikedIcon(),
                     color = colorPalette().favoritesIcon,
                     onClick = ::toggleLike,
                     modifier = Modifier
                         .rotate(rotationAngle)
                         .padding(horizontal = 2.dp, vertical = 8.dp)
                         .size(24.dp)
                 )

            }

            Spacer(
                modifier = Modifier
                    .width(2.dp)
            )
        }
        /*****  */

    }
}