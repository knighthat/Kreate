package it.fast4x.rimusic.ui.components

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import app.kreate.android.Preferences
import it.fast4x.innertube.Innertube
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.AlbumSwipeAction
import it.fast4x.rimusic.enums.DownloadedStateMedia
import it.fast4x.rimusic.enums.PlaylistSwipeAction
import it.fast4x.rimusic.enums.QueueSwipeAction
import it.fast4x.rimusic.service.MyDownloadService
import it.fast4x.rimusic.service.modern.isLocal
import it.fast4x.rimusic.utils.downloadedStateMedia
import it.fast4x.rimusic.utils.getDownloadState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.bookmark
import kreate.resources.generated.resources.bookmark_filled
import kreate.resources.generated.resources.download_progress
import kreate.resources.generated.resources.favorite_filled
import kreate.resources.generated.resources.heart_dislike
import kreate.resources.generated.resources.heart_outline
import me.knighthat.sync.YouTubeSync
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource


@OptIn(UnstableApi::class)
private fun getStateIcon(
    action: QueueSwipeAction,
    likeState: Boolean?,
    downloadState: Int,
    downloadedStateMedia: DownloadedStateMedia
): DrawableResource =
    when( action ) {
        QueueSwipeAction.Download -> when( downloadedStateMedia ) {
            DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED -> when (downloadState) {
                Download.STATE_DOWNLOADING,
                Download.STATE_QUEUED,
                Download.STATE_RESTARTING -> Res.drawable.download_progress
                else -> downloadedStateMedia.iconId
            }
            else -> downloadedStateMedia.iconId
        }
        QueueSwipeAction.Favourite -> when( likeState ) {
            false -> Res.drawable.heart_dislike
            null  -> Res.drawable.heart_outline
            else  -> Res.drawable.favorite_filled
        }
        else -> action.iconId
    }

@OptIn(UnstableApi::class)
private fun getStateIcon(
    action: PlaylistSwipeAction,
    likeState: Boolean?,
    downloadState: Int,
    downloadedStateMedia: DownloadedStateMedia
): DrawableResource =
    when( action ) {
        PlaylistSwipeAction.Download -> when( downloadedStateMedia ) {
            DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED -> when (downloadState) {
                Download.STATE_DOWNLOADING,
                Download.STATE_QUEUED,
                Download.STATE_RESTARTING -> Res.drawable.download_progress
                else -> downloadedStateMedia.iconId
            }
            else -> downloadedStateMedia.iconId
        }
        PlaylistSwipeAction.Favourite -> when( likeState ) {
            false -> Res.drawable.heart_dislike
            null  -> Res.drawable.heart_outline
            else  -> Res.drawable.favorite_filled
        }
        else -> action.iconId
    }

private fun getStateIcon(
    action: AlbumSwipeAction,
    bookmarkedState: Long?
): DrawableResource =
        when ( action ) {
            AlbumSwipeAction.Bookmark -> when( bookmarkedState ) {
                null -> Res.drawable.bookmark
                else -> Res.drawable.bookmark_filled
            }
            else -> action.iconId
        }

@Composable
fun SwipeableContent(
    swipeToLeftIcon: DrawableResource,
    swipeToRightIcon: DrawableResource,
    onSwipeToLeft: () -> Unit,
    onSwipeToRight: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance: Float -> distance * 0.25f },
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {onSwipeToRight();hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)}
            else if (value == SwipeToDismissBoxValue.EndToStart) {onSwipeToLeft();hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)}

            return@rememberSwipeToDismissBoxState false
        }
    )
    val isSwipeToActionEnabled by app.kreate.preferences.Preferences.ENABLE_SWIPE_ACTION.collectAsStateWithLifecycle()

    val current = LocalViewConfiguration.current
    CompositionLocalProvider(LocalViewConfiguration provides object : ViewConfiguration by current{
        override val touchSlop: Float
            get() = current.touchSlop * 5f
    }) {
        SwipeToDismissBox(
            gesturesEnabled = isSwipeToActionEnabled,
            modifier = modifier,
            //.padding(horizontal = 16.dp)
            //.clip(RoundedCornerShape(12.dp)),
            state = dismissState,
            backgroundContent = {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        //.background(colorPalette.background1)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = when (dismissState.targetValue) {
                        SwipeToDismissBoxValue.StartToEnd -> Arrangement.Start
                        SwipeToDismissBoxValue.EndToStart -> Arrangement.End
                        SwipeToDismissBoxValue.Settled -> Arrangement.Center
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = when (dismissState.targetValue) {
                        SwipeToDismissBoxValue.StartToEnd -> swipeToRightIcon
                        SwipeToDismissBoxValue.EndToStart -> swipeToLeftIcon
                        SwipeToDismissBoxValue.Settled -> null
                    }
                    if (icon != null)
                        Icon(
                            painter = painterResource( icon ),
                            contentDescription = null,
                            tint = colorPalette().accent,
                        )
                }
            }
        ) {
            content()
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun SwipeableQueueItem(
    mediaItem: MediaItem,
    onPlayNext: (() -> Unit) = {},
    onDownload: (() -> Unit) = {},
    onRemoveFromQueue: (() -> Unit) = {},
    onEnqueue: (() -> Unit) = {},
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val downloadState = getDownloadState(mediaItem.mediaId)
    var downloadedStateMedia by remember { mutableStateOf(DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED) }
    downloadedStateMedia = if (!mediaItem.isLocal) downloadedStateMedia(mediaItem.mediaId)
    else DownloadedStateMedia.DOWNLOADED

    val onDownloadButtonClick: () -> Unit = {
        if (
            (
                    (downloadState == Download.STATE_DOWNLOADING
                    || downloadState == Download.STATE_QUEUED
                    || downloadState == Download.STATE_RESTARTING
                    )  && downloadedStateMedia == DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED
            ) ||
            (
                    downloadedStateMedia == DownloadedStateMedia.DOWNLOADED
                   || downloadedStateMedia == DownloadedStateMedia.CACHED_AND_DOWNLOADED
            )
        ) {
            DownloadService.sendRemoveDownload(
                context,
                MyDownloadService::class.java,
                mediaItem.mediaId,
                false
            )
        } else {
            onDownload()
        }
    }

    val songLikeState by remember {
        Database.songTable
                .likeState( mediaItem.mediaId )
                .distinctUntilChanged()
    }.collectAsState( null, Dispatchers.IO )

    val onFavourite: () -> Unit = {
        CoroutineScope( Dispatchers.IO ).launch {
            YouTubeSync.toggleSongLike( context, mediaItem )
        }
    }

    val queueSwipeLeftAction by Preferences.QUEUE_SWIPE_LEFT_ACTION
    val queueSwipeRightAction by Preferences.QUEUE_SWIPE_RIGHT_ACTION

    fun getActionCallback(actionName: QueueSwipeAction): () -> Unit {
        return when (actionName) {
            QueueSwipeAction.PlayNext -> onPlayNext
            QueueSwipeAction.Download -> onDownloadButtonClick
            QueueSwipeAction.Favourite -> onFavourite
            QueueSwipeAction.RemoveFromQueue -> onRemoveFromQueue
            QueueSwipeAction.Enqueue -> onEnqueue
            else -> ({})
        }
    }
    val swipeLeftCallback = getActionCallback(queueSwipeLeftAction)
    val swipeRighCallback = getActionCallback(queueSwipeRightAction)

    SwipeableContent(
        swipeToLeftIcon = getStateIcon(
            queueSwipeLeftAction,
            songLikeState,
            downloadState,
            downloadedStateMedia
        ),
        swipeToRightIcon = getStateIcon(
            queueSwipeRightAction,
            songLikeState,
            downloadState,
            downloadedStateMedia
        ),
        onSwipeToLeft = swipeLeftCallback,
        onSwipeToRight = swipeRighCallback,
        modifier = modifier
    ) {
        content()
    }

}

@OptIn(UnstableApi::class)
@Composable
fun SwipeablePlaylistItem(
    mediaItem: MediaItem,
    onPlayNext: (() -> Unit) = {},
    onDownload: (() -> Unit) = {},
    onEnqueue: (() -> Unit) = {},
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val downloadState = getDownloadState(mediaItem.mediaId)
    var downloadedStateMedia by remember { mutableStateOf(DownloadedStateMedia.NOT_CACHED_OR_DOWNLOADED) }
    downloadedStateMedia = if (!mediaItem.isLocal) downloadedStateMedia(mediaItem.mediaId)
    else DownloadedStateMedia.DOWNLOADED

    val songLikeState by remember {
        Database.songTable
            .likeState( mediaItem.mediaId )
            .distinctUntilChanged()
    }.collectAsState( null, Dispatchers.IO )

    val onFavourite: () -> Unit = {
        CoroutineScope( Dispatchers.IO ).launch {
            YouTubeSync.toggleSongLike( context, mediaItem )
        }
    }

    val playlistSwipeLeftAction by Preferences.PLAYLIST_SWIPE_LEFT_ACTION
    val playlistSwipeRightAction by Preferences.PLAYLIST_SWIPE_RIGHT_ACTION

    fun getActionCallback(actionName: PlaylistSwipeAction): () -> Unit {
        return when (actionName) {
            PlaylistSwipeAction.PlayNext -> onPlayNext
            PlaylistSwipeAction.Download -> onDownload
            PlaylistSwipeAction.Favourite -> onFavourite
            PlaylistSwipeAction.Enqueue -> onEnqueue
            else -> ({})
        }
    }
    val swipeLeftCallback = getActionCallback(playlistSwipeLeftAction)
    val swipeRighCallback = getActionCallback(playlistSwipeRightAction)

    SwipeableContent(
        swipeToLeftIcon =  getStateIcon(
            playlistSwipeLeftAction,
            songLikeState,
            downloadState,
            downloadedStateMedia
        ),
        swipeToRightIcon =  getStateIcon(
            playlistSwipeRightAction,
            songLikeState,
            downloadState,
            downloadedStateMedia
        ),
        onSwipeToLeft = swipeLeftCallback,
        onSwipeToRight = swipeRighCallback
    ) {
        content()
    }

}

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun SwipeableAlbumItem(
    albumItem: Innertube.AlbumItem,
    onPlayNext: () -> Unit,
    onEnqueue: () -> Unit,
    onBookmark: () -> Unit,
    content: @Composable () -> Unit
) {
    val album by remember( albumItem.key ) {
        Database.albumTable
                .findById( albumItem.key )
    }.collectAsState( null, Dispatchers.IO )

    val albumSwipeLeftAction by Preferences.ALBUM_SWIPE_LEFT_ACTION
    val albumSwipeRightAction by Preferences.ALBUM_SWIPE_RIGHT_ACTION

    fun getActionCallback(actionName: AlbumSwipeAction): () -> Unit {
        return when (actionName) {
            AlbumSwipeAction.PlayNext -> onPlayNext
            AlbumSwipeAction.Bookmark -> onBookmark
            AlbumSwipeAction.Enqueue -> onEnqueue
            else -> ({})
        }
    }

    SwipeableContent(
        swipeToLeftIcon =  getStateIcon( albumSwipeLeftAction, album?.bookmarkedAt ),
        swipeToRightIcon =  getStateIcon( albumSwipeRightAction, album?.bookmarkedAt ),
        onSwipeToLeft = getActionCallback( albumSwipeLeftAction ),
        onSwipeToRight = getActionCallback( albumSwipeRightAction )
    ) {
        content()
    }

}