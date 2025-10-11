package it.fast4x.rimusic.ui.screens.player

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.Preferences
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.enums.ButtonState
import it.fast4x.rimusic.enums.PlayerControlsType
import it.fast4x.rimusic.enums.PlayerInfoType
import it.fast4x.rimusic.enums.PlayerPlayButtonType
import it.fast4x.rimusic.enums.PlayerType
import it.fast4x.rimusic.models.Info
import it.fast4x.rimusic.ui.screens.player.components.controls.InfoAlbumAndArtistEssential
import it.fast4x.rimusic.ui.screens.player.components.controls.InfoAlbumAndArtistModern
import it.fast4x.rimusic.utils.GetControls
import it.fast4x.rimusic.utils.GetSeekBar
import it.fast4x.rimusic.utils.conditional
import it.fast4x.rimusic.utils.isLandscape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onEach


@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@UnstableApi
@Composable
fun Controls(
    navController: NavController,
    mediaItem: MediaItem,
    onCollapse: () -> Unit,
    onBlurScaleChange: (Float) -> Unit,
    expandedPlayer: Boolean,
    titleExpanded: Boolean,
    timelineExpanded: Boolean,
    controlsExpanded: Boolean,
    isShowingLyrics: Boolean,
    artistIds: List<Info>?,
    albumId: String?,
    shouldBePlaying: Boolean,
    positionAndDuration: Pair<Long, Long>,
    modifier: Modifier = Modifier
) {
    val binder = LocalPlayerServiceBinder.current
    binder?.player ?: return

    val currentSong by remember( mediaItem.mediaId ) {
        Database.songTable
                .findById( mediaItem.mediaId )
                .onEach {
                    println("Controls currentSong: ${it?.title}")
                }
                .distinctUntilChanged()
    }.collectAsState( null, Dispatchers.IO )

    val (position, duration) = positionAndDuration

    var playerTimelineSize by Preferences.PLAYER_TIMELINE_SIZE
    val playerInfoType by Preferences.PLAYER_INFO_TYPE
    var playerSwapControlsWithTimeline by Preferences.PLAYER_IS_CONTROL_AND_TIMELINE_SWAPPED
    var showlyricsthumbnail by Preferences.LYRICS_SHOW_THUMBNAIL
    var transparentBackgroundActionBarPlayer by Preferences.PLAYER_TRANSPARENT_ACTIONS_BAR
    var playerControlsType by Preferences.PLAYER_CONTROLS_TYPE
    var playerPlayButtonType by Preferences.PLAYER_PLAY_BUTTON_TYPE
    var showthumbnail by Preferences.PLAYER_SHOW_THUMBNAIL
    var playerType by Preferences.PLAYER_TYPE
    val expandedlandscape = (isLandscape && playerType == PlayerType.Modern) || (expandedPlayer && !showthumbnail)

    Box(
        modifier = Modifier
            .animateContentSize()
    ) {
        if ((!isLandscape) and ((expandedPlayer || isShowingLyrics) && !showlyricsthumbnail))
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.Bottom,
                modifier = Modifier
                    .padding(horizontal = playerTimelineSize.size.dp)
            ) {
                if (!isShowingLyrics || titleExpanded) {
                    if (playerInfoType == PlayerInfoType.Modern)
                        InfoAlbumAndArtistModern(
                            binder = binder,
                            navController = navController,
                            mediaItem = mediaItem,
                            albumId = albumId,
                            onCollapse = onCollapse,
                            artistIds = artistIds
                        )

                    if (playerInfoType == PlayerInfoType.Essential)
                        InfoAlbumAndArtistEssential(
                            binder = binder,
                            navController = navController,
                            mediaItem = mediaItem,
                            albumId = albumId,
                            onCollapse = onCollapse,
                            artistIds = artistIds,
                        )
                    Spacer(
                        modifier = Modifier
                            .height(10.dp)
                    )
                }
                if (!isShowingLyrics || timelineExpanded) {
                    GetSeekBar( mediaItem, positionAndDuration )
                    Spacer(
                        modifier = Modifier
                            .height(if (playerPlayButtonType != PlayerPlayButtonType.Disabled) 10.dp else 5.dp)
                    )
                }
                if (!isShowingLyrics || controlsExpanded) {
                    GetControls(
                        binder = binder,
                        position = position,
                        shouldBePlaying = shouldBePlaying,
                        likedAt = currentSong?.likedAt,
                        mediaId = mediaItem.mediaId,
                        onBlurScaleChange = onBlurScaleChange
                    )
                    Spacer(
                        modifier = Modifier
                            .height(5.dp)
                    )
                }
                if (((playerControlsType == PlayerControlsType.Modern) || (!transparentBackgroundActionBarPlayer)) && (playerPlayButtonType != PlayerPlayButtonType.Disabled)) {
                    Spacer(
                        modifier = Modifier
                            .height(10.dp)
                    )
                }
            }
        else if (!isLandscape)
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = modifier
                    .fillMaxWidth()
                    .padding(horizontal = playerTimelineSize.size.dp)
                    //.fillMaxHeight(0.40f)
            ) {

                if (playerInfoType == PlayerInfoType.Modern)
                    InfoAlbumAndArtistModern(
                        binder = binder,
                        navController = navController,
                        mediaItem = mediaItem,
                        albumId = albumId,
                        onCollapse = onCollapse,
                        artistIds = artistIds
                    )

                if (playerInfoType == PlayerInfoType.Essential)
                    InfoAlbumAndArtistEssential(
                        binder = binder,
                        navController = navController,
                        mediaItem = mediaItem,
                        albumId = albumId,
                        onCollapse = onCollapse,
                        artistIds = artistIds
                    )

                Spacer(
                    modifier = Modifier
                        .height(25.dp)
                )

                if (!playerSwapControlsWithTimeline) {
                    GetSeekBar( mediaItem, positionAndDuration )
                    Spacer(
                        modifier = Modifier
                            .weight(0.4f)
                    )
                    GetControls(
                        binder = binder,
                        position = position,
                        shouldBePlaying = shouldBePlaying,
                        likedAt = currentSong?.likedAt,
                        mediaId = mediaItem.mediaId,
                        onBlurScaleChange = onBlurScaleChange
                    )
                    Spacer(
                        modifier = Modifier
                            .weight(0.5f)
                    )
                } else {
                    GetControls(
                        binder = binder,
                        position = position,
                        shouldBePlaying = shouldBePlaying,
                        likedAt = currentSong?.likedAt,
                        mediaId = mediaItem.mediaId,
                        onBlurScaleChange = onBlurScaleChange
                    )
                    Spacer(
                        modifier = Modifier
                            .weight(0.5f)
                    )
                    GetSeekBar( mediaItem, positionAndDuration )
                    Spacer(
                        modifier = Modifier
                            .weight(0.4f)
                    )
                }

            }

    }
    if (isLandscape)
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Bottom,
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = playerTimelineSize.size.dp)
        ) {

            if (playerInfoType == PlayerInfoType.Modern)
                InfoAlbumAndArtistModern(
                    binder = binder,
                    navController = navController,
                    mediaItem = mediaItem,
                    albumId = albumId,
                    onCollapse = onCollapse,
                    artistIds = artistIds
                )

            if (playerInfoType == PlayerInfoType.Essential)
                InfoAlbumAndArtistEssential(
                    binder = binder,
                    navController = navController,
                    mediaItem = mediaItem,
                    albumId = albumId,
                    onCollapse = onCollapse,
                    artistIds = artistIds
                )

            Spacer(
                modifier = Modifier
                    .height(if (expandedlandscape) 10.dp else 25.dp)
            )

            if (!playerSwapControlsWithTimeline) {
                GetSeekBar( mediaItem, positionAndDuration )
                Spacer(
                    modifier = Modifier
                        .animateContentSize()
                        .conditional(!expandedlandscape) { weight(0.4f) }
                        .conditional(expandedlandscape) { height(15.dp) }
                )
                GetControls(
                    binder = binder,
                    position = position,
                    shouldBePlaying = shouldBePlaying,
                    likedAt = currentSong?.likedAt,
                    mediaId = mediaItem.mediaId,
                    onBlurScaleChange = onBlurScaleChange
                )
                Spacer(
                    modifier = Modifier
                        .animateContentSize()
                        .conditional(!expandedlandscape) { weight(0.5f) }
                        .conditional(expandedlandscape) { height(15.dp) }
                )
            } else {
                GetControls(
                    binder = binder,
                    position = position,
                    shouldBePlaying = shouldBePlaying,
                    likedAt = currentSong?.likedAt,
                    mediaId = mediaItem.mediaId,
                    onBlurScaleChange = onBlurScaleChange
                )
                Spacer(
                    modifier = Modifier
                        .animateContentSize()
                        .conditional(!expandedlandscape) { weight(0.5f) }
                        .conditional(expandedlandscape) { height(15.dp) }
                )
                GetSeekBar( mediaItem, positionAndDuration )
                Spacer(
                    modifier = Modifier
                        .animateContentSize()
                        .conditional(!expandedlandscape) { weight(0.4f) }
                        .conditional(expandedlandscape) { height(15.dp) }
                )
            }
        }
}

fun Modifier.bounceClick() = composed {
    var buttonState by remember { mutableStateOf(ButtonState.Idle) }
    var buttonzoomout by Preferences.ZOOM_OUT_ANIMATION
    val scale by animateFloatAsState(if ((buttonState == ButtonState.Pressed) && (buttonzoomout)) 0.8f else 1f)

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(buttonState) {
            awaitPointerEventScope {
                buttonState = if (buttonState == ButtonState.Pressed) {
                    waitForUpOrCancellation()
                    ButtonState.Idle
                } else {
                    awaitFirstDown(false)
                    ButtonState.Pressed
                }
            }
        }
}


/*
@ExperimentalTextApi
@ExperimentalAnimationApi
@UnstableApi
@Composable
private fun PlayerMenu(
    binder: PlayerService.Binder,
    mediaItem: MediaItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val activityResultLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    BaseMediaItemMenu(
        mediaItem = mediaItem,
        onStartRadio = {
            binder.stopRadio()
            binder.player.seamlessPlay(mediaItem)
            binder.setupRadio(NavigationEndpoint.Endpoint.Watch(videoId = mediaItem.mediaId))
        },
        onGoToEqualizer = {
            try {
                activityResultLauncher.launch(
                    Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                        putExtra(AudioEffect.EXTRA_AUDIO_SESSION, binder.player.audioSessionId)
                        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                        putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                    }
                )
            } catch (e: ActivityNotFoundException) {
                context.toast("Couldn't find an application to equalize audio")
            }
        },
        onShowSleepTimer = {},
        onDismiss = onDismiss
    )
}

@Composable
private fun Duration(
    position: Float,
    duration: Long,
) {
    val typography = LocalAppearance.current.typography
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        BasicText(
            text = formatAsDuration(position.toLong()),
            style = typography.xxs.semiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (duration != C.TIME_UNSET) {
            BasicText(
                text = formatAsDuration(duration),
                style = typography.xxs.semiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
*/