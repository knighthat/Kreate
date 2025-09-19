package it.fast4x.rimusic.ui.components.themed


import android.annotation.SuppressLint
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.rimusic.component.song.SongItem
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.MODIFIED_PREFIX
import it.fast4x.rimusic.MONTHLY_PREFIX
import it.fast4x.rimusic.PINNED_PREFIX
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.cleanPrefix
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.context
import it.fast4x.rimusic.enums.MenuStyle
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.models.Info
import it.fast4x.rimusic.models.Playlist
import it.fast4x.rimusic.models.PlaylistPreview
import it.fast4x.rimusic.models.Song
import it.fast4x.rimusic.service.MyDownloadHelper
import it.fast4x.rimusic.service.modern.isLocal
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.ui.styling.favoritesIcon
import it.fast4x.rimusic.ui.styling.px
import it.fast4x.rimusic.utils.DisposableListener
import it.fast4x.rimusic.utils.addNext
import it.fast4x.rimusic.utils.addSongToYtPlaylist
import it.fast4x.rimusic.utils.addToYtLikedSong
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.asSong
import it.fast4x.rimusic.utils.enqueue
import it.fast4x.rimusic.utils.formatAsDuration
import it.fast4x.rimusic.utils.getDownloadState
import it.fast4x.rimusic.utils.getLikeState
import it.fast4x.rimusic.utils.isDownloadedSong
import it.fast4x.rimusic.utils.isNetworkConnected
import it.fast4x.rimusic.utils.medium
import it.fast4x.rimusic.utils.positionAndDurationState
import it.fast4x.rimusic.utils.removeYTSongFromPlaylist
import it.fast4x.rimusic.utils.semiBold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import me.knighthat.sync.YouTubeSync
import me.knighthat.utils.Toaster
import java.time.LocalTime.now
import java.time.format.DateTimeFormatter

@ExperimentalTextApi
@ExperimentalAnimationApi
@androidx.media3.common.util.UnstableApi
@Composable
fun InHistoryMediaItemMenu(
    navController: NavController,
    onDismiss: () -> Unit,
    song: Song,
    onHideFromDatabase: (() -> Unit)? = {},
    onDeleteFromDatabase: (() -> Unit)? = {},
    modifier: Modifier = Modifier
) {

    NonQueuedMediaItemMenu(
        navController = navController,
        mediaItem = song.asMediaItem,
        onDismiss = onDismiss,
        onHideFromDatabase = onHideFromDatabase,
        onDeleteFromDatabase = onDeleteFromDatabase,
        onAddToPreferites = {
            if (!isNetworkConnected(context()) && isYouTubeSyncEnabled()){
                Toaster.noInternet()
            } else if (!isYouTubeSyncEnabled()){
                Database.asyncTransaction {
                    songTable.likeState( song.id, true )
                    MyDownloadHelper.autoDownloadWhenLiked(song.asMediaItem)
                }
            }
            else {
                CoroutineScope(Dispatchers.IO).launch {
                    addToYtLikedSong(song.asMediaItem)
                }
            }
        },
        modifier = modifier
    )
}

@ExperimentalTextApi
@UnstableApi
@ExperimentalAnimationApi
@Composable
fun InPlaylistMediaItemMenu(
    navController: NavController,
    onDismiss: () -> Unit,
    playlist: PlaylistPreview? = null,
    playlistId: Long,
    positionInPlaylist: Int,
    song: Song,
    onMatchingSong: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    NonQueuedMediaItemMenu(
        navController = navController,
        mediaItem = song.asMediaItem,
        onDismiss = onDismiss,
        onRemoveFromPlaylist = {
            if (!isNetworkConnected(context) && playlist?.playlist?.isYoutubePlaylist == true && playlist.playlist.isEditable && isYouTubeSyncEnabled()){
                Toaster.noInternet()
            } else if (playlist?.playlist?.isEditable == true) {

                Database.asyncTransaction {
                    CoroutineScope(Dispatchers.IO).launch {
                        playlist.playlist.browseId.let {
                            println("InPlaylistMediaItemMenu isYoutubePlaylist ${playlist.playlist.isYoutubePlaylist} isEditable ${playlist.playlist.isEditable} songId ${song.id} browseId ${playlist.playlist.browseId} playlistId $playlistId")
                            if (isYouTubeSyncEnabled() && playlist.playlist.isYoutubePlaylist && playlist.playlist.isEditable) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    if (removeYTSongFromPlaylist(song.id,playlist.playlist.browseId ?: "",playlistId))
                                        songPlaylistMapTable.deleteBySongId( song.id, playlistId )
                                }
                            } else {
                                songPlaylistMapTable.deleteBySongId( song.id, playlistId )
                            }
                        }
                    }
                }
            }else {
                Toaster.w( R.string.cannot_delete_from_online_playlists )
            }
        },
        onAddToPreferites = {
            if (!isNetworkConnected(context()) && isYouTubeSyncEnabled()){
                Toaster.noInternet()
            } else if (!isYouTubeSyncEnabled()){
                Database.asyncTransaction {
                    songTable.likeState( song.id, true )
                    MyDownloadHelper.autoDownloadWhenLiked(song.asMediaItem)
                }
            }
            else {
                CoroutineScope(Dispatchers.IO).launch {
                    addToYtLikedSong(song.asMediaItem)
                }
            }
        },
        onMatchingSong = { if (onMatchingSong != null) {onMatchingSong()}
            onDismiss() },
        modifier = modifier
    )
}

@ExperimentalTextApi
@UnstableApi
@ExperimentalAnimationApi
@Composable
fun NonQueuedMediaItemMenuLibrary(
    navController: NavController,
    onDismiss: () -> Unit,
    mediaItem: MediaItem,
    modifier: Modifier = Modifier,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onRemoveFromQuickPicks: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onMatchingSong: (() -> Unit)? = null
) {
    val binder = LocalPlayerServiceBinder.current
    val context = LocalContext.current

    var isHiding by remember {
        mutableStateOf(false)
    }

    if (isHiding) {
        ConfirmationDialog(
            text = stringResource(R.string.update_song),
            onDismiss = { isHiding = false },
            onConfirm = {
                onDismiss()
                if (binder != null) {
                    binder.cache.removeResource(mediaItem.mediaId)
                    binder.downloadCache.removeResource(mediaItem.mediaId)
                    Database.asyncTransaction {
                        songTable.updateTotalPlayTime( mediaItem.mediaId, 0 )
                    }
                }
            }
        )
    }

    val menuStyle by Preferences.MENU_STYLE

    if (menuStyle == MenuStyle.Grid) {

        BaseMediaItemGridMenu(
            navController = navController,
            mediaItem = mediaItem,
            onDismiss = onDismiss,
            onStartRadio = {
                binder?.startRadio( mediaItem )
            },
            onPlayNext = { binder?.player?.addNext(mediaItem, context) },
            onEnqueue = { binder?.player?.enqueue(mediaItem, context) },
            onDownload = onDownload,
            onRemoveFromPlaylist = onRemoveFromPlaylist,
            onHideFromDatabase = { isHiding = true },
            onRemoveFromQuickPicks = onRemoveFromQuickPicks,
            onAddToPreferites = {
                if (!isNetworkConnected(context()) && isYouTubeSyncEnabled()){
                    Toaster.noInternet()
                } else if (!isYouTubeSyncEnabled()){
                    Database.asyncTransaction {
                        songTable.likeState( mediaItem.mediaId, true )
                        MyDownloadHelper.autoDownloadWhenLiked(mediaItem)
                    }
                }
                else {
                    CoroutineScope(Dispatchers.IO).launch {
                        addToYtLikedSong(mediaItem)
                    }
                }
            },
            modifier = modifier
        )
    } else {

        BaseMediaItemMenu(
            navController = navController,
            mediaItem = mediaItem,
            onDismiss = onDismiss,
            onStartRadio = {
                binder?.startRadio( mediaItem )
            },
            onPlayNext = { binder?.player?.addNext(mediaItem, context) },
            onEnqueue = { binder?.player?.enqueue(mediaItem, context)},
            onDownload = onDownload,
            onRemoveFromPlaylist = onRemoveFromPlaylist,
            onHideFromDatabase = { isHiding = true },
            onRemoveFromQuickPicks = onRemoveFromQuickPicks,
            onAddToPreferites = {
                if (!isNetworkConnected(context()) && isYouTubeSyncEnabled()){
                    Toaster.noInternet()
                } else if (!isYouTubeSyncEnabled()){
                    Database.asyncTransaction {
                        songTable.likeState( mediaItem.mediaId, true )
                        MyDownloadHelper.autoDownloadWhenLiked(mediaItem)
                    }
                }
                else {
                    CoroutineScope(Dispatchers.IO).launch {
                        addToYtLikedSong(mediaItem)
                    }
                }
            },
            onMatchingSong = onMatchingSong,
            modifier = modifier
        )
    }
}

@ExperimentalTextApi
@UnstableApi
@ExperimentalAnimationApi
@Composable
fun NonQueuedMediaItemMenu(
    navController: NavController,
    onDismiss: () -> Unit,
    mediaItem: MediaItem,
    modifier: Modifier = Modifier,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onHideFromDatabase: (() -> Unit)? = null,
    onDeleteFromDatabase: (() -> Unit)? = null,
    onRemoveFromQuickPicks: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onAddToPreferites: (() -> Unit)? = null,
    onMatchingSong: (() -> Unit)? = null
) {
    val binder = LocalPlayerServiceBinder.current
    val context = LocalContext.current

    val menuStyle by Preferences.MENU_STYLE

    //println("mediaItem in NonQueuedMediaItemMenu albumId ${mediaItem.mediaMetadata.extras?.getString("albumId")}")

    if (menuStyle == MenuStyle.Grid) {
        BaseMediaItemGridMenu(
            navController = navController,
            mediaItem = mediaItem,
            onDismiss = onDismiss,
            onStartRadio = {
                binder?.startRadio( mediaItem )
            },
            onPlayNext = { binder?.player?.addNext(mediaItem, context) },
            onEnqueue = { binder?.player?.enqueue(mediaItem, context) },
            onDownload = onDownload,
            onRemoveFromPlaylist = onRemoveFromPlaylist,
            onHideFromDatabase = onHideFromDatabase,
            onDeleteFromDatabase = onDeleteFromDatabase,
            onRemoveFromQuickPicks = onRemoveFromQuickPicks,
            onAddToPreferites = onAddToPreferites,
            onMatchingSong =  onMatchingSong,
            modifier = modifier
        )
    } else {

        BaseMediaItemMenu(
            navController = navController,
            mediaItem = mediaItem,
            onDismiss = onDismiss,
            onStartRadio = {
                binder?.startRadio( mediaItem )
            },
            onPlayNext = { binder?.player?.addNext(mediaItem, context) },
            onEnqueue = { binder?.player?.enqueue(mediaItem, context) },
            onDownload = onDownload,
            onRemoveFromPlaylist = onRemoveFromPlaylist,
            onHideFromDatabase = onHideFromDatabase,
            onDeleteFromDatabase = onDeleteFromDatabase,
            onRemoveFromQuickPicks = onRemoveFromQuickPicks,
            onAddToPreferites = onAddToPreferites,
            onMatchingSong =  onMatchingSong,
            modifier = modifier
        )
    }
}

@ExperimentalTextApi
@UnstableApi
@ExperimentalAnimationApi
@Composable
fun QueuedMediaItemMenu(
    navController: NavController,
    onDismiss: () -> Unit,
    onDownload: (() -> Unit)?,
    onMatchingSong: (() -> Unit)? = null,
    mediaItem: MediaItem,
    indexInQueue: Int?,
    modifier: Modifier = Modifier
) {
    val binder = LocalPlayerServiceBinder.current
    val context = LocalContext.current

    val menuStyle by Preferences.MENU_STYLE

    if (menuStyle == MenuStyle.Grid) {
        BaseMediaItemGridMenu(
            navController = navController,
            mediaItem = mediaItem,
            onDismiss = onDismiss,
            onDownload = onDownload,
            onRemoveFromQueue = if (indexInQueue != null) ({
                binder?.player?.removeMediaItem(indexInQueue)
            }) else null,
            onPlayNext = { binder?.player?.addNext(mediaItem, context) },
            onStartRadio = {
                binder?.startRadio( mediaItem )
            },
            modifier = modifier,
            onGoToPlaylist = {
                NavRoutes.localPlaylist.navigateHere( navController, it )
            },
            onAddToPreferites = {
                if (!isNetworkConnected(context()) && isYouTubeSyncEnabled()){
                    Toaster.noInternet()
                } else if (!isYouTubeSyncEnabled()){
                    Database.asyncTransaction {
                        songTable.likeState( mediaItem.mediaId, true )
                        MyDownloadHelper.autoDownloadWhenLiked(mediaItem)
                    }
                }
                else {
                    CoroutineScope(Dispatchers.IO).launch {
                        addToYtLikedSong(mediaItem)
                    }
                }
            }
        )
    } else {
        BaseMediaItemMenu(
            navController = navController,
            mediaItem = mediaItem,
            onDismiss = onDismiss,
            onDownload = onDownload,
            onRemoveFromQueue = if (indexInQueue != null) ({
                binder?.player?.removeMediaItem(indexInQueue)
            }) else null,
            onPlayNext = { binder?.player?.addNext(mediaItem, context) },
            onStartRadio = {
                binder?.startRadio( mediaItem )
            },
            modifier = modifier,
            onGoToPlaylist = {
                NavRoutes.YT_PLAYLIST.navigateHere( navController, it.toString() )
            },
            onAddToPreferites = {
                if (!isNetworkConnected(context()) && isYouTubeSyncEnabled()){
                    Toaster.noInternet()
                } else if (!isYouTubeSyncEnabled()){
                    Database.asyncTransaction {
                        songTable.likeState( mediaItem.mediaId, true )
                        MyDownloadHelper.autoDownloadWhenLiked(mediaItem)
                    }
                }
                else {
                    CoroutineScope(Dispatchers.IO).launch {
                        addToYtLikedSong(mediaItem)
                    }
                }
            },
            onMatchingSong = onMatchingSong
        )
    }
}

@ExperimentalTextApi
@UnstableApi
@ExperimentalAnimationApi
@Composable
fun BaseMediaItemMenu(
    navController: NavController,
    onDismiss: () -> Unit,
    mediaItem: MediaItem,
    modifier: Modifier = Modifier,
    onGoToEqualizer: (() -> Unit)? = null,
    onShowSleepTimer: (() -> Unit)? = null,
    onStartRadio: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onEnqueue: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onRemoveFromQueue: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onHideFromDatabase: (() -> Unit)? = null,
    onDeleteFromDatabase: (() -> Unit)? = null,
    onRemoveFromQuickPicks: (() -> Unit)? = null,
    onClosePlayer: (() -> Unit)? = null,
    onGoToPlaylist: ((Long) -> Unit)? = null,
    onAddToPreferites: (() -> Unit)?,
    onMatchingSong: (() -> Unit)?
) {
    val context = LocalContext.current

    MediaItemMenu(
        navController = navController,
        mediaItem = mediaItem,
        onDismiss = onDismiss,
        onGoToEqualizer = onGoToEqualizer,
        onShowSleepTimer = onShowSleepTimer,
        onStartRadio = onStartRadio,
        onPlayNext = onPlayNext,
        onEnqueue = onEnqueue,
        onDownload = onDownload,
        onAddToPreferites = onAddToPreferites,
        onMatchingSong =  onMatchingSong,
        onAddToPlaylist = { playlist, position ->
            if (!isYouTubeSyncEnabled() || !playlist.isYoutubePlaylist){
                Database.asyncTransaction {
                    insertIgnore( mediaItem )
                    mapIgnore( playlist, mediaItem.asSong )
                }
            } else {
                CoroutineScope(Dispatchers.IO).launch {
                    addSongToYtPlaylist(playlist.id, position, playlist.browseId ?: "", mediaItem)
                }
            }
        },
        onHideFromDatabase = onHideFromDatabase,
        onDeleteFromDatabase = onDeleteFromDatabase,
        onRemoveFromPlaylist = onRemoveFromPlaylist,
        onRemoveFromQueue = onRemoveFromQueue,
        onGoToAlbum = {
            NavRoutes.YT_ALBUM.navigateHere( navController, it )
            if (onClosePlayer != null) {
                onClosePlayer()
            }
        },
        onGoToArtist = {
            NavRoutes.YT_ARTIST.navigateHere( navController, it )

            if (onClosePlayer != null) {
                onClosePlayer()
            }
        },
        onShare = {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TEXT,
                    "https://music.youtube.com/watch?v=${mediaItem.mediaId}"
                )
            }

            context.startActivity(Intent.createChooser(sendIntent, null))
        },
        onRemoveFromQuickPicks = onRemoveFromQuickPicks,
        onGoToPlaylist = {
            NavRoutes.localPlaylist.navigateHere( navController, it )
        },
        modifier = modifier
    )
}

@ExperimentalTextApi
@UnstableApi
@ExperimentalAnimationApi
@Composable
fun MiniMediaItemMenu(
    navController: NavController,
    onDismiss: () -> Unit,
    mediaItem: MediaItem,
    onGoToPlaylist: ((Long) -> Unit)? = null,
    onAddToPreferites: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    MediaItemMenu(
        navController = navController,
        mediaItem = mediaItem,
        onDismiss = onDismiss,
        onAddToPlaylist = { playlist, position ->
            if (!isYouTubeSyncEnabled() || !playlist.isYoutubePlaylist){
                Database.asyncTransaction {
                    insertIgnore( mediaItem )
                    mapIgnore( playlist, mediaItem.asSong )
                }
            } else {
                CoroutineScope(Dispatchers.IO).launch {
                    addSongToYtPlaylist(playlist.id, position, playlist.browseId ?: "", mediaItem)
                }
            }
            onDismiss()
        },
        onGoToPlaylist = {
            NavRoutes.localPlaylist.navigateHere( navController, it )
            if (onGoToPlaylist != null) {
                onGoToPlaylist(it)
            }
        },
        onShare = {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TEXT,
                    "https://music.youtube.com/watch?v=${mediaItem.mediaId}"
                )
            }

            context.startActivity(Intent.createChooser(sendIntent, null))
        },
        onAddToPreferites = onAddToPreferites,
        modifier = modifier
    )
}

@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@UnstableApi
@ExperimentalAnimationApi
@Composable
fun MediaItemMenu(
    navController: NavController,
    onDismiss: () -> Unit,
    mediaItem: MediaItem,
    modifier: Modifier = Modifier,
    onGoToEqualizer: (() -> Unit)? = null,
    onShowSleepTimer: (() -> Unit)? = null,
    onStartRadio: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onEnqueue: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onHideFromDatabase: (() -> Unit)? = null,
    onDeleteFromDatabase: (() -> Unit)? = null,
    onRemoveFromQueue: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onAddToPreferites: (() -> Unit)?,
    onAddToPlaylist: ((Playlist, Int) -> Unit)? = null,
    onGoToAlbum: ((String) -> Unit)? = null,
    onGoToArtist: ((String) -> Unit)? = null,
    onRemoveFromQuickPicks: (() -> Unit)? = null,
    onShare: () -> Unit,
    onGoToPlaylist: ((Long) -> Unit)? = null,
    onMatchingSong: (() -> Unit)? = null
) {
    val density = LocalDensity.current

    val binder = LocalPlayerServiceBinder.current ?: return
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val (colorPalette, typography) = LocalAppearance.current
    val hapticFeedback = LocalHapticFeedback.current

    val isLocal by remember { derivedStateOf { mediaItem.isLocal } }

    var isViewingPlaylists by remember {
        mutableStateOf(false)
    }

    var showSelectDialogListenOn by remember {
        mutableStateOf(false)
    }

    var height by remember {
        mutableStateOf(0.dp)
    }

    var downloadState by remember {
        mutableStateOf(Download.STATE_STOPPED)
    }

    downloadState = getDownloadState(mediaItem.mediaId)
    val isDownloaded = if (!isLocal) isDownloadedSong(mediaItem.mediaId) else true

    val album by remember {
        Database.albumTable
                .findBySongId( mediaItem.mediaId )
    }.collectAsState( null, Dispatchers.IO )
    val artists by remember {
        Database.artistTable
                .findBySongId( mediaItem.mediaId )
    }.collectAsState( emptyList(), Dispatchers.IO )

    var showCircularSlider by remember {
        mutableStateOf(false)
    }

    var showDialogChangeSongTitle by remember {
        mutableStateOf(false)
    }

    var showDialogChangeSongArtist by remember {
        mutableStateOf(false)
    }

    val isSongExist by remember( mediaItem.mediaId ) {
        Database.songTable.exists( mediaItem.mediaId )
    }.collectAsState( false, Dispatchers.IO )

    if (showDialogChangeSongTitle)
        InputTextDialog(
            onDismiss = { showDialogChangeSongTitle = false },
            title = stringResource(R.string.update_title),
            value = mediaItem.mediaMetadata.title.toString(),
            placeholder = stringResource(R.string.title),
            setValue = {
                if (it.isNotEmpty()) {
                    Database.asyncTransaction {
                        songTable.updateTitle( mediaItem.mediaId, it )
                    }
                }
            },
            prefix = MODIFIED_PREFIX
        )

    if (showDialogChangeSongArtist)
        InputTextDialog(
            onDismiss = { showDialogChangeSongArtist = false },
            title = stringResource(R.string.update_authors),
            value = mediaItem.mediaMetadata.artist.toString(),
            placeholder = stringResource(R.string.authors),
            setValue = {
                if (it.isNotEmpty()) {
                    Database.asyncTransaction {
                        songTable.updateArtists( mediaItem.mediaId, it )
                    }
                }
            }
        )

    AnimatedContent(
        targetState = isViewingPlaylists,
        transitionSpec = {
            val animationSpec = tween<IntOffset>(400)
            val slideDirection = if (targetState) AnimatedContentTransitionScope.SlideDirection.Left
            else AnimatedContentTransitionScope.SlideDirection.Right

            slideIntoContainer(slideDirection, animationSpec) togetherWith
                    slideOutOfContainer(slideDirection, animationSpec)
        }, label = ""
    ) { currentIsViewingPlaylists ->
        if (currentIsViewingPlaylists) {
            val playlistPreviews by remember {
                Database.playlistTable.sortPreviewsByName()
            }.collectAsState( emptyList(), Dispatchers.IO )

            val playlistIds by remember {
                Database.songPlaylistMapTable.mappedTo( mediaItem.mediaId )
            }.collectAsState( emptyList(), Dispatchers.IO )

            val pinnedPlaylists = playlistPreviews.filter {
                it.playlist.name.startsWith(PINNED_PREFIX, 0, true)
                        && if (isNetworkConnected(context)) !(it.playlist.isYoutubePlaylist && !it.playlist.isEditable) else !it.playlist.isYoutubePlaylist
            }
            val youtubePlaylists = playlistPreviews.filter { it.playlist.isEditable && it.playlist.isYoutubePlaylist && !it.playlist.name.startsWith(PINNED_PREFIX) }

            val unpinnedPlaylists = playlistPreviews.filter {
                !it.playlist.name.startsWith(PINNED_PREFIX, 0, true) &&
                !it.playlist.name.startsWith(MONTHLY_PREFIX, 0, true) &&
                        !it.playlist.isYoutubePlaylist //&&
                //!it.playlist.name.startsWith(PIPED_PREFIX, 0, true)
            }

            var isCreatingNewPlaylist by rememberSaveable {
                mutableStateOf(false)
            }

            if (isCreatingNewPlaylist && onAddToPlaylist != null) {
                InputTextDialog(
                    onDismiss = { isCreatingNewPlaylist = false },
                    title = stringResource(R.string.enter_the_playlist_name),
                    value = "",
                    placeholder = stringResource(R.string.enter_the_playlist_name),
                    setValue = { text ->
                        onDismiss()
                        onAddToPlaylist(Playlist(name = text), 0)
                    }
                )
                /*
                TextFieldDialog(
                    hintText = "Enter the playlist name",
                    onDismiss = { isCreatingNewPlaylist = false },
                    onDone = { text ->
                        onDismiss()
                        onAddToPlaylist(Playlist(name = text), 0)
                    }
                )
                 */
            }

            BackHandler {
                isViewingPlaylists = false
            }

            Menu(
                modifier = modifier
                    .requiredHeight(height)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { isViewingPlaylists = false },
                        icon = R.drawable.chevron_back,
                        color = colorPalette().textSecondary,
                        modifier = Modifier
                            .padding(all = 4.dp)
                            .size(20.dp)
                    )

                    if (onAddToPlaylist != null) {
                        SecondaryTextButton(
                            text = stringResource(R.string.new_playlist),
                            onClick = { isCreatingNewPlaylist = true },
                            alternative = true
                        )
                    }
                }

                if (pinnedPlaylists.isNotEmpty()) {
                    BasicText(
                        text = stringResource(R.string.pinned_playlists),
                        style = typography().m.semiBold,
                        modifier = modifier.padding(start = 20.dp, top = 5.dp)
                    )

                    onAddToPlaylist?.let { onAddToPlaylist ->
                        pinnedPlaylists.forEach { playlistPreview ->
                            MenuEntry(
                                icon = if (playlistIds.contains(playlistPreview.playlist.id)) R.drawable.checkmark else R.drawable.add_in_playlist,
                                text = cleanPrefix(playlistPreview.playlist.name),
                                secondaryText = "${playlistPreview.songCount} " + stringResource(R.string.songs),
                                onClick = {
                                    onDismiss()
                                    onAddToPlaylist(playlistPreview.playlist, playlistPreview.songCount)
                                },
                                trailingContent = {
                                    if (playlistPreview.playlist.isYoutubePlaylist) {
                                        Image(
                                            painter = painterResource(R.drawable.ytmusic),
                                            contentDescription = null,
                                            colorFilter = ColorFilter.tint(
                                                Color.Red.copy(0.75f).compositeOver(Color.White)
                                            ),
                                            modifier = Modifier
                                                .size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        icon = R.drawable.open,
                                        color = colorPalette().text,
                                        onClick = {
                                            if (onGoToPlaylist != null) {
                                                onGoToPlaylist(playlistPreview.playlist.id)
                                                onDismiss()
                                            }
                                            NavRoutes.localPlaylist.navigateHere( navController, playlistPreview.playlist.id )
                                        },
                                        modifier = Modifier
                                            .size(24.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                if (youtubePlaylists.isNotEmpty() && isNetworkConnected(context)) {
                    BasicText(
                        text = stringResource(R.string.ytm_playlists),
                        style = typography().m.semiBold,
                        modifier = Modifier.padding(start = 20.dp, top = 5.dp)
                    )

                    onAddToPlaylist?.let { onAddToPlaylist ->
                        youtubePlaylists.forEach { playlistPreview ->
                            MenuEntry(
                                icon = if (playlistIds.contains(playlistPreview.playlist.id)) R.drawable.checkmark else R.drawable.add_in_playlist,
                                text = cleanPrefix(playlistPreview.playlist.name),
                                secondaryText = "${playlistPreview.songCount} " + stringResource(R.string.songs),
                                onClick = {
                                    onDismiss()
                                    onAddToPlaylist(playlistPreview.playlist, playlistPreview.songCount)
                                },
                                trailingContent = {
                                    IconButton(
                                        icon = R.drawable.open,
                                        color = colorPalette().text,
                                        onClick = {
                                            if (onGoToPlaylist != null) {
                                                onGoToPlaylist(playlistPreview.playlist.id)
                                                onDismiss()
                                            }
                                            NavRoutes.localPlaylist.navigateHere( navController, playlistPreview.playlist.id )
                                        },
                                        modifier = Modifier
                                            .size(24.dp)
                                    )
                                }
                            )
                        }
                    }
                }

                if (unpinnedPlaylists.isNotEmpty()) {
                    BasicText(
                        text = stringResource(R.string.playlists),
                        style = typography().m.semiBold,
                        modifier = modifier.padding(start = 20.dp, top = 5.dp)
                    )

                    onAddToPlaylist?.let { onAddToPlaylist ->
                        unpinnedPlaylists.forEach { playlistPreview ->
                            MenuEntry(
                                icon = if (playlistIds.contains(playlistPreview.playlist.id)) R.drawable.checkmark else R.drawable.add_in_playlist,
                                text = cleanPrefix(playlistPreview.playlist.name),
                                secondaryText = "${playlistPreview.songCount} " + stringResource(R.string.songs),
                                onClick = {
                                    onDismiss()
                                    onAddToPlaylist(playlistPreview.playlist, playlistPreview.songCount)
                                },
                                trailingContent = {
                                    IconButton(
                                        icon = R.drawable.open,
                                        color = colorPalette().text,
                                        onClick = {
                                            if (onGoToPlaylist != null) {
                                                onGoToPlaylist(playlistPreview.playlist.id)
                                                onDismiss()
                                            }
                                            NavRoutes.localPlaylist.navigateHere( navController, playlistPreview.playlist.id )
                                        },
                                        modifier = Modifier
                                            .size(24.dp)
                                    )

                                }
                            )
                        }
                    }
                }
            }
        } else {
            Menu(
                modifier = modifier
                    .onPlaced { height = with(density) { it.size.height.toDp() } }
            ) {
                val thumbnailSizeDp = Dimensions.thumbnails.song + 20.dp
                val thumbnailSizePx = thumbnailSizeDp.px
                val thumbnailArtistSizeDp = Dimensions.thumbnails.song + 10.dp
                val thumbnailArtistSizePx = thumbnailArtistSizeDp.px

                Box(
                    modifier = Modifier
                        .fillMaxSize()

                ) {
                    Image(
                        painter = painterResource(R.drawable.chevron_down),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette().text),
                        modifier = Modifier
                            .absoluteOffset(0.dp, -10.dp)
                            .align(Alignment.TopCenter)
                            .size(30.dp)
                            .clickable { onDismiss() }
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(end = 12.dp)
                ) {
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

                    SongItem.Render(
                        mediaItem = mediaItem,
                        context = context,
                        binder = binder,
                        hapticFeedback = hapticFeedback,
                        isPlaying = mediaItem.mediaId == currentlyPlaying,
                        values = songItemValues,
                        navController = navController
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            //icon = if (likedAt == null) R.drawable.heart_outline else R.drawable.heart,
                            icon = getLikeState(mediaItem.mediaId),
                            //icon = R.drawable.heart,
                            color = colorPalette().favoritesIcon,
                            //color = if (likedAt == null) colorPalette().textDisabled else colorPalette().text,
                            onClick = {
                                CoroutineScope( Dispatchers.IO ).launch {
                                    YouTubeSync.toggleSongLike( appContext(), mediaItem )
                                }
                            },
                            modifier = Modifier
                                .padding(all = 4.dp)
                                .size(24.dp)
                        )

                        if (!isLocal) IconButton(
                            icon = R.drawable.share_social,
                            color = colorPalette().text,
                            onClick = onShare,
                            modifier = Modifier
                                .padding(all = 4.dp)
                                .size(24.dp)
                        )

                    }

                }
/*
                if (artistsList.isNotEmpty())
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(start = 12.dp, end = 12.dp)
                            .fillMaxWidth()
                            //.border(BorderStroke(1.dp, Color.Red))
                            .background(colorPalette().background1)
                    ) {
                        artistsList.forEach { artist ->
                            if (artist != null) {
                                ArtistItem(
                                    artist = artist,
                                    showName = false,
                                    thumbnailSizePx = thumbnailArtistSizePx,
                                    thumbnailSizeDp = thumbnailArtistSizeDp,
                                    alternative = true,
                                    modifier = Modifier
                                        .clickable(onClick = {
                                            if (onGoToArtist != null) {
                                                onDismiss()
                                                onGoToArtist(artist.id)
                                            }
                                        })
                                )
                            }
                        }
                    }



                Spacer(
                    modifier = Modifier
                        .height(8.dp)
                )

                Spacer(
                    modifier = Modifier
                        .alpha(0.5f)
                        .align(Alignment.CenterHorizontally)
                        .background(colorPalette().textDisabled)
                        .height(1.dp)
                        .fillMaxWidth(1f)
                )
*/
                Spacer(
                    modifier = Modifier
                        .height(8.dp)
                )

                if ( !isLocal && isSongExist ) {
                    MenuEntry(
                        icon = R.drawable.title_edit,
                        text = stringResource(R.string.update_title),
                        onClick = {
                            showDialogChangeSongTitle = true
                        }
                    )
                    MenuEntry(
                        icon = R.drawable.title_edit,
                        text = stringResource(R.string.update_authors),
                        onClick = {
                            showDialogChangeSongArtist = true
                        }
                    )
                }

                if (!isLocal) onStartRadio?.let { onStartRadio ->
                    MenuEntry(
                        icon = R.drawable.radio,
                        text = stringResource(R.string.start_radio),
                        onClick = {
                            onDismiss()
                            onStartRadio()
                        }
                    )
                }

                onPlayNext?.let { onPlayNext ->
                    MenuEntry(
                        icon = R.drawable.play_skip_forward,
                        text = stringResource(R.string.play_next),
                        onClick = {
                            onDismiss()
                            onPlayNext()
                        }
                    )
                }

                onEnqueue?.let { onEnqueue ->
                    MenuEntry(
                        icon = R.drawable.enqueue,
                        text = stringResource(R.string.enqueue),
                        onClick = {
                            onDismiss()
                            onEnqueue()
                        }
                    )
                }

                if (!isDownloaded)
                    onDownload?.let { onDownload ->
                        MenuEntry(
                            icon = R.drawable.download,
                            text = stringResource(R.string.download),
                            onClick = {
                                onDismiss()
                                onDownload()
                            }
                        )
                    }


                onGoToEqualizer?.let { onGoToEqualizer ->
                    MenuEntry(
                        icon = R.drawable.equalizer,
                        text = stringResource(R.string.equalizer),
                        onClick = {
                            onDismiss()
                            onGoToEqualizer()
                        }
                    )
                }

                // TODO: find solution to this shit
                onShowSleepTimer?.let {
                    val binder = LocalPlayerServiceBinder.current
                    var isShowingSleepTimerDialog by remember {
                        mutableStateOf(false)
                    }

                    val sleepTimerMillisLeft by (binder?.sleepTimerMillisLeft
                        ?: flowOf(null))
                        .collectAsState(initial = null)

                    val positionAndDuration = binder?.player?.positionAndDurationState()

                    var timeRemaining by remember { mutableIntStateOf(0) }

                    if (positionAndDuration != null) {
                        timeRemaining = positionAndDuration.value.second.toInt() - positionAndDuration.value.first.toInt()
                    }

                    //val timeToStop = System.currentTimeMillis()

                    if (isShowingSleepTimerDialog) {
                        if (sleepTimerMillisLeft != null) {
                            ConfirmationDialog(
                                text = stringResource(R.string.stop_sleep_timer),
                                cancelText = stringResource(R.string.no),
                                confirmText = stringResource(R.string.stop),
                                onDismiss = { isShowingSleepTimerDialog = false },
                                onConfirm = {
                                    binder?.cancelSleepTimer()
                                    onDismiss()
                                }
                            )
                        } else {
                            DefaultDialog(
                                onDismiss = { isShowingSleepTimerDialog = false }
                            ) {
                                var amount by remember {
                                    mutableStateOf(1)
                                }

                                BasicText(
                                    text = stringResource(R.string.set_sleep_timer),
                                    style = typography().s.semiBold,
                                    modifier = Modifier
                                        .padding(vertical = 8.dp, horizontal = 24.dp)
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(
                                        space = 16.dp,
                                        alignment = Alignment.CenterHorizontally
                                    ),
                                    modifier = Modifier
                                        .padding(vertical = 10.dp)
                                ) {
                                    if (!showCircularSlider) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .alpha(if (amount <= 1) 0.5f else 1f)
                                                .clip(CircleShape)
                                                .clickable(enabled = amount > 1) { amount-- }
                                                .size(48.dp)
                                                .background(colorPalette().background0)
                                        ) {
                                            BasicText(
                                                text = "-",
                                                style = typography().xs.semiBold
                                            )
                                        }

                                        Box(contentAlignment = Alignment.Center) {
                                            BasicText(
                                                text = stringResource(
                                                    R.string.left,
                                                    formatAsDuration(amount * 5 * 60 * 1000L)
                                                ),
                                                style = typography().s.semiBold,
                                                modifier = Modifier
                                                    .clickable {
                                                        showCircularSlider = !showCircularSlider
                                                    }
                                            )
                                        }

                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .alpha(if (amount >= 60) 0.5f else 1f)
                                                .clip(CircleShape)
                                                .clickable(enabled = amount < 60) { amount++ }
                                                .size(48.dp)
                                                .background(colorPalette().background0)
                                        ) {
                                            BasicText(
                                                text = "+",
                                                style = typography().xs.semiBold
                                            )
                                        }

                                    } else {
                                        CircularSlider(
                                            stroke = 40f,
                                            thumbColor = colorPalette().accent,
                                            text = formatAsDuration(amount * 5 * 60 * 1000L),
                                            modifier = Modifier
                                                .size(300.dp),
                                            onChange = {
                                                amount = (it * 120).toInt()
                                            }
                                        )
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    modifier = Modifier
                                        .padding(bottom = 20.dp)
                                        .fillMaxWidth()
                                ) {
                                    SecondaryTextButton(
                                        text = stringResource(R.string.set_to) + " "
                                                + formatAsDuration(timeRemaining.toLong())
                                                + " " + stringResource(R.string.end_of_song),
                                        onClick = {
                                            binder?.startSleepTimer(timeRemaining.toLong())
                                            isShowingSleepTimerDialog = false
                                        }
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                ) {

                                    IconButton(
                                        onClick = { showCircularSlider = !showCircularSlider },
                                        icon = R.drawable.time,
                                        color = colorPalette().text
                                    )
                                    IconButton(
                                        onClick = { isShowingSleepTimerDialog = false },
                                        icon = R.drawable.close,
                                        color = colorPalette().text
                                    )
                                    IconButton(
                                        enabled = amount > 0,
                                        onClick = {
                                            binder?.startSleepTimer(amount * 5 * 60 * 1000L)
                                            isShowingSleepTimerDialog = false
                                        },
                                        icon = R.drawable.checkmark,
                                        color = colorPalette().accent
                                    )
                                }
                            }
                        }
                    }

                    MenuEntry(
                        icon = R.drawable.sleep,
                        text = stringResource(R.string.sleep_timer),
                        onClick = { isShowingSleepTimerDialog = true },
                        trailingContent = sleepTimerMillisLeft?.let {
                            {
                                BasicText(
                                    text = stringResource(
                                        R.string.left,
                                        formatAsDuration(it)
                                    ) + " / " +
                                            now()
                                                .plusSeconds(it / 1000)
                                                .format(DateTimeFormatter.ofPattern("HH:mm:ss")) + " " +
                                            stringResource(R.string.sleeptimer_stop),
                                    style = typography().xxs.medium,
                                    modifier = modifier
                                        .background(
                                            color = colorPalette().background0,
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                        .animateContentSize()
                                )
                            }
                        }
                    )
                }

                if (onAddToPreferites != null)
                    MenuEntry(
                        icon = R.drawable.heart,
                        text = stringResource(R.string.add_to_favorites),
                        onClick = onAddToPreferites
                    )

                if (onMatchingSong != null)
                    MenuEntry(
                        icon = R.drawable.random,
                        text = stringResource(R.string.match_song),
                        onClick = { onMatchingSong() }
                    )

                if (onAddToPlaylist != null) {
                    MenuEntry(
                        icon = R.drawable.add_in_playlist,
                        text = stringResource(R.string.add_to_playlist),
                        onClick = { isViewingPlaylists = true },
                        trailingContent = {
                            Image(
                                painter = painterResource(R.drawable.chevron_forward),
                                contentDescription = null,
                                colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(
                                    colorPalette().textSecondary
                                ),
                                modifier = Modifier
                                    .size(16.dp)
                            )
                        }
                    )
                }
                /*
                onGoToAlbum?.let { onGoToAlbum ->
                    albumInfo?.let { (albumId) ->
                        MenuEntry(
                            icon = R.drawable.disc,
                            text = stringResource(R.string.go_to_album),
                            onClick = {
                                onDismiss()
                                onGoToAlbum(albumId)
                            }
                        )
                    }
                }
                 */

                //println("mediaItem in MediaItemMenu onGoToAlbum  ALBUMiD ${mediaItem.mediaMetadata.extras?.getString("albumId")}")
                //println("mediaItem in MediaItemMenu onGoToAlbum  albumInfo ${albumInfo?.id}")

                if (!isLocal) onGoToAlbum?.let { onGoToAlbum ->
                    album?.id?.let { albumId ->
                        MenuEntry(
                            icon = R.drawable.album,
                            text = stringResource(R.string.go_to_album),
                            onClick = {
                                onDismiss()
                                onGoToAlbum(albumId)
                            }
                        )
                    }
                }

                if (!isLocal) onGoToArtist?.let { onGoToArtist ->
                    artists.forEach { artist ->
                        MenuEntry(
                            icon = R.drawable.people,
                            text = stringResource(R.string.more_of) + " ${artist.name}",
                            onClick = {
                                onDismiss()
                                onGoToArtist(artist.id)
                            }
                        )
                    }
                }

                if (!isLocal) MenuEntry(
                    icon = R.drawable.play,
                    text = stringResource(R.string.listen_on),
                    onClick = { showSelectDialogListenOn = true }
                )

                if (showSelectDialogListenOn)
                    SelectorDialog(
                        title = stringResource(R.string.listen_on),
                        onDismiss = { showSelectDialogListenOn = false },
                        values = listOf(
                            Info(
                                "https://youtube.com/watch?v=${mediaItem.mediaId}",
                                stringResource(R.string.listen_on_youtube)
                            ),
                            Info(
                                "https://music.youtube.com/watch?v=${mediaItem.mediaId}",
                                stringResource(R.string.listen_on_youtube_music)
                            ),
                            Info(
                                "https://piped.kavin.rocks/watch?v=${mediaItem.mediaId}&playerAutoPlay=true",
                                stringResource(R.string.listen_on_piped)
                            ),
                            Info(
                                "https://yewtu.be/watch?v=${mediaItem.mediaId}&autoplay=1",
                                stringResource(R.string.listen_on_invidious)
                            )
                        ),
                        onValueSelected = {
                            binder?.player?.pause()
                            showSelectDialogListenOn = false
                            uriHandler.openUri(it)
                        }
                    )
                /*
                                if (!isLocal) MenuEntry(
                                    icon = R.drawable.play,
                                    text = stringResource(R.string.listen_on_youtube),
                                    onClick = {
                                        onDismiss()
                                        binder?.player?.pause()
                                        uriHandler.openUri("https://youtube.com/watch?v=${mediaItem.mediaId}")
                                    }
                                )

                                val ytNonInstalled = stringResource(R.string.it_seems_that_youtube_music_is_not_installed)
                                if (!isLocal) MenuEntry(
                                    icon = R.drawable.musical_notes,
                                    text = stringResource(R.string.listen_on_youtube_music),
                                    onClick = {
                                        onDismiss()
                                        binder?.player?.pause()
                                        if (!launchYouTubeMusic(context, "watch?v=${mediaItem.mediaId}"))
                                            context.toast(ytNonInstalled)
                                    }
                                )


                                if (!isLocal) MenuEntry(
                                    icon = R.drawable.play,
                                    text = stringResource(R.string.listen_on_piped),
                                    onClick = {
                                        onDismiss()
                                        binder?.player?.pause()
                                        uriHandler.openUri("https://piped.kavin.rocks/watch?v=${mediaItem.mediaId}&playerAutoPlay=true&minimizeDescription=true")
                                    }
                                )
                                if (!isLocal) MenuEntry(
                                    icon = R.drawable.play,
                                    text = stringResource(R.string.listen_on_invidious),
                                    onClick = {
                                        onDismiss()
                                        binder?.player?.pause()
                                        uriHandler.openUri("https://yewtu.be/watch?v=${mediaItem.mediaId}&autoplay=1")
                                    }
                                )

                */

                onRemoveFromQueue?.let { onRemoveFromQueue ->
                    MenuEntry(
                        icon = R.drawable.trash,
                        text = stringResource(R.string.remove_from_queue),
                        onClick = {
                            onDismiss()
                            onRemoveFromQueue()
                        }
                    )
                }

                onRemoveFromPlaylist?.let { onRemoveFromPlaylist ->
                    MenuEntry(
                        icon = R.drawable.trash,
                        text = stringResource(R.string.remove_from_playlist),
                        onClick = {
                            onDismiss()
                            onRemoveFromPlaylist()
                        }
                    )
                }

                if (!isLocal) onHideFromDatabase?.let { onHideFromDatabase ->
                    MenuEntry(
                        icon = R.drawable.update,
                        text = stringResource(R.string.update),
                        onClick = onHideFromDatabase
                    )
                }

                onDeleteFromDatabase?.let { onDeleteFromDatabase ->
                    MenuEntry(
                        icon = R.drawable.trash,
                        text = stringResource(R.string.delete),
                        onClick = onDeleteFromDatabase
                    )
                }

                if (!isLocal) onRemoveFromQuickPicks?.let {
                    MenuEntry(
                        icon = R.drawable.trash,
                        text = stringResource(R.string.hide_from_quick_picks),
                        onClick = {
                            onDismiss()
                            onRemoveFromQuickPicks()
                        }
                    )
                }
            }
        }
    }
}

@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@UnstableApi
@ExperimentalAnimationApi
@Composable
fun AddToPlaylistItemMenu(
    navController: NavController,
    onDismiss: () -> Unit,
    onAddToPlaylist: ((Playlist, Int) -> Unit),
    onRemoveFromPlaylist: ((Playlist) -> Unit),
    mediaItem: MediaItem,
    onGoToPlaylist: ((Long) -> Unit)? = null,
) {
    var isCreatingNewPlaylist by rememberSaveable {
        mutableStateOf(false)
    }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val screenHeight = configuration.screenHeightDp.dp

    if (isCreatingNewPlaylist) {
        InputTextDialog(
            onDismiss = { isCreatingNewPlaylist = false },
            title = stringResource(R.string.enter_the_playlist_name),
            value = "",
            placeholder = stringResource(R.string.enter_the_playlist_name),
            setValue = { text ->
                onDismiss()
                onAddToPlaylist(Playlist(name = text), 0)
            }
        )
    }
    val playlistPreviews by remember {
        Database.playlistTable.sortPreviewsByName()
    }.collectAsState( emptyList(), Dispatchers.IO )

    val playlistIds by remember {
        Database.songPlaylistMapTable.mappedTo( mediaItem.mediaId )
    }.collectAsState( emptyList(), Dispatchers.IO )

    val pinnedPlaylists = playlistPreviews.filter {
        it.playlist.name.startsWith(PINNED_PREFIX, 0, true)
                && if (isNetworkConnected(context)) !(it.playlist.isYoutubePlaylist && !it.playlist.isEditable) else !it.playlist.isYoutubePlaylist
    }

    val youtubePlaylists = playlistPreviews.filter { it.playlist.isEditable && it.playlist.isYoutubePlaylist && !it.playlist.name.startsWith(PINNED_PREFIX) }

    val unpinnedPlaylists = playlistPreviews.filter {
        !it.playlist.name.startsWith(PINNED_PREFIX, 0, true) &&
                !it.playlist.name.startsWith(MONTHLY_PREFIX, 0, true) &&
                !it.playlist.isYoutubePlaylist
    }

    Menu(
        modifier = Modifier
            .requiredHeight(0.75*screenHeight)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
        ) {
            IconButton(
                onClick = onDismiss,
                icon = R.drawable.chevron_back,
                color = colorPalette().textSecondary,
                modifier = Modifier
                    .padding(all = 4.dp)
                    .size(20.dp)
            )

            SecondaryTextButton(
                text = stringResource(R.string.new_playlist),
                onClick = { isCreatingNewPlaylist = true },
                alternative = true
            )
        }

        if (pinnedPlaylists.isNotEmpty()) {
            BasicText(
                text = stringResource(R.string.pinned_playlists),
                style = typography().m.semiBold,
                modifier = Modifier.padding(start = 20.dp, top = 5.dp)
            )

            onAddToPlaylist.let { onAddToPlaylist ->
                pinnedPlaylists.forEach { playlistPreview ->
                    MenuEntry(
                        icon = if (playlistIds.contains(playlistPreview.playlist.id)) R.drawable.checkmark else R.drawable.add_in_playlist,
                        text = cleanPrefix(playlistPreview.playlist.name),
                        secondaryText = "${playlistPreview.songCount} " + stringResource(R.string.songs),
                        onClick = {
                            if (playlistIds.contains(playlistPreview.playlist.id)){
                                onRemoveFromPlaylist(playlistPreview.playlist)
                            } else onAddToPlaylist(playlistPreview.playlist, playlistPreview.songCount)
                        },
                        trailingContent = {
                            if (playlistPreview.playlist.isYoutubePlaylist) {
                                Image(
                                    painter = painterResource(R.drawable.ytmusic),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(
                                        Color.Red.copy(0.75f).compositeOver(Color.White)
                                    ),
                                    modifier = Modifier
                                        .size(18.dp)
                                )
                            }
                            IconButton(
                                icon = R.drawable.open,
                                color = colorPalette().text,
                                onClick = {
                                    if (onGoToPlaylist != null) {
                                        onGoToPlaylist(playlistPreview.playlist.id)
                                        onDismiss()
                                    }
                                    NavRoutes.localPlaylist.navigateHere( navController, playlistPreview.playlist.id )
                                },
                                modifier = Modifier
                                    .size(24.dp)
                            )
                        }
                    )
                }
            }
        }

        if (youtubePlaylists.isNotEmpty() && isNetworkConnected(context)) {
            BasicText(
                text = stringResource(R.string.ytm_playlists),
                style = typography().m.semiBold,
                modifier = Modifier.padding(start = 20.dp, top = 5.dp)
            )

            onAddToPlaylist.let { onAddToPlaylist ->
                youtubePlaylists.forEach { playlistPreview ->
                    MenuEntry(
                        icon = if (playlistIds.contains(playlistPreview.playlist.id)) R.drawable.checkmark else R.drawable.add_in_playlist,
                        text = cleanPrefix(playlistPreview.playlist.name),
                        secondaryText = "${playlistPreview.songCount} " + stringResource(R.string.songs),
                        onClick = {
                            if (playlistIds.contains(playlistPreview.playlist.id)){
                                onRemoveFromPlaylist(playlistPreview.playlist)
                            } else onAddToPlaylist(playlistPreview.playlist, playlistPreview.songCount)
                        },
                        trailingContent = {
                            IconButton(
                                icon = R.drawable.open,
                                color = colorPalette().text,
                                onClick = {
                                    if (onGoToPlaylist != null) {
                                        onGoToPlaylist(playlistPreview.playlist.id)
                                        onDismiss()
                                    }
                                    NavRoutes.localPlaylist.navigateHere( navController, playlistPreview.playlist.id )
                                },
                                modifier = Modifier
                                    .size(24.dp)
                            )
                        }
                    )
                }
            }
        }

        if (unpinnedPlaylists.isNotEmpty()) {
            BasicText(
                text = stringResource(R.string.playlists),
                style = typography().m.semiBold,
                modifier = Modifier.padding(start = 20.dp, top = 5.dp)
            )

            onAddToPlaylist.let { onAddToPlaylist ->
                unpinnedPlaylists.forEach { playlistPreview ->
                    MenuEntry(
                        icon = if (playlistIds.contains(playlistPreview.playlist.id)) R.drawable.checkmark else R.drawable.add_in_playlist,
                        text = cleanPrefix(playlistPreview.playlist.name),
                        secondaryText = "${playlistPreview.songCount} " + stringResource(R.string.songs),
                        onClick = {
                            if (playlistIds.contains(playlistPreview.playlist.id)){
                                onRemoveFromPlaylist(playlistPreview.playlist)
                            } else onAddToPlaylist(playlistPreview.playlist, playlistPreview.songCount)
                        },
                        trailingContent = {
                            IconButton(
                                icon = R.drawable.open,
                                color = colorPalette().text,
                                onClick = {
                                    if (onGoToPlaylist != null) {
                                        onGoToPlaylist(playlistPreview.playlist.id)
                                        onDismiss()
                                    }
                                    NavRoutes.localPlaylist.navigateHere( navController, playlistPreview.playlist.id )
                                },
                                modifier = Modifier
                                    .size(24.dp)
                            )

                        }
                    )
                }
            }
        }
    }
}

@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@UnstableApi
@ExperimentalAnimationApi
@Composable
fun AddToPlaylistArtistSongsMenu(
    navController: NavController,
    onDismiss: () -> Unit,
    onAddToPlaylist: ((PlaylistPreview) -> Unit),
    onGoToPlaylist: ((Long) -> Unit)? = null,
) {
    var isCreatingNewPlaylist by rememberSaveable {
        mutableStateOf(false)
    }
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val screenHeight = configuration.screenHeightDp.dp

    if (isCreatingNewPlaylist) {
        InputTextDialog(
            onDismiss = { isCreatingNewPlaylist = false },
            title = stringResource(R.string.enter_the_playlist_name),
            value = "",
            placeholder = stringResource(R.string.enter_the_playlist_name),
            setValue = { text ->
                onDismiss()
                Database.asyncTransaction {
                    val pId = playlistTable.insert( Playlist(name = text) )
                    onAddToPlaylist(
                        PlaylistPreview(
                            Playlist(
                                id = pId,
                                name = text
                            ),
                            0
                        )
                    )
                }
            }
        )
    }
    val playlistPreviews by remember {
        Database.playlistTable.sortPreviewsByName()
    }.collectAsState( emptyList(), Dispatchers.IO )

    val pinnedPlaylists = playlistPreviews.filter {
        it.playlist.name.startsWith(PINNED_PREFIX, 0, true)
                && if (isNetworkConnected(context)) !(it.playlist.isYoutubePlaylist && !it.playlist.isEditable) else !it.playlist.isYoutubePlaylist
    }

    val youtubePlaylists = playlistPreviews.filter { it.playlist.isEditable && it.playlist.isYoutubePlaylist && !it.playlist.name.startsWith(PINNED_PREFIX) }

    val unpinnedPlaylists = playlistPreviews.filter {
        !it.playlist.name.startsWith(PINNED_PREFIX, 0, true) &&
                !it.playlist.name.startsWith(MONTHLY_PREFIX, 0, true) &&
                !it.playlist.isYoutubePlaylist
    }

    Menu(
        modifier = Modifier
            .requiredHeight(0.75*screenHeight)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
        ) {
            IconButton(
                onClick = onDismiss,
                icon = R.drawable.chevron_back,
                color = colorPalette().textSecondary,
                modifier = Modifier
                    .padding(all = 4.dp)
                    .size(20.dp)
            )

            SecondaryTextButton(
                text = stringResource(R.string.new_playlist),
                onClick = { isCreatingNewPlaylist = true },
                alternative = true
            )
        }

        if (pinnedPlaylists.isNotEmpty()) {
            BasicText(
                text = stringResource(R.string.pinned_playlists),
                style = typography().m.semiBold,
                modifier = Modifier.padding(start = 20.dp, top = 5.dp)
            )

            onAddToPlaylist.let { onAddToPlaylist ->
                pinnedPlaylists.forEach { playlistPreview ->
                    MenuEntry(
                        icon = R.drawable.add_in_playlist,
                        text = cleanPrefix(playlistPreview.playlist.name),
                        secondaryText = "${playlistPreview.songCount} " + stringResource(R.string.songs),
                        onClick = {
                            onDismiss()
                            onAddToPlaylist(
                                PlaylistPreview(
                                    playlistPreview.playlist,
                                    playlistPreview.songCount
                                )
                            )
                        },
                        trailingContent = {
                            if (playlistPreview.playlist.isYoutubePlaylist) {
                                Image(
                                    painter = painterResource(R.drawable.ytmusic),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(
                                        Color.Red.copy(0.75f).compositeOver(Color.White)
                                    ),
                                    modifier = Modifier
                                        .size(18.dp)
                                )
                            }
                            IconButton(
                                icon = R.drawable.open,
                                color = colorPalette().text,
                                onClick = {
                                    if (onGoToPlaylist != null) {
                                        onGoToPlaylist(playlistPreview.playlist.id)
                                        onDismiss()
                                    }
                                    NavRoutes.localPlaylist.navigateHere( navController, playlistPreview.playlist.id )
                                },
                                modifier = Modifier
                                    .size(24.dp)
                            )
                        }
                    )
                }
            }
        }

        if (youtubePlaylists.isNotEmpty() && isNetworkConnected(context)) {
            BasicText(
                text = stringResource(R.string.ytm_playlists),
                style = typography().m.semiBold,
                modifier = Modifier.padding(start = 20.dp, top = 5.dp)
            )

            onAddToPlaylist.let { onAddToPlaylist ->
                youtubePlaylists.forEach { playlistPreview ->
                    MenuEntry(
                        icon = R.drawable.add_in_playlist,
                        text = cleanPrefix(playlistPreview.playlist.name),
                        secondaryText = "${playlistPreview.songCount} " + stringResource(R.string.songs),
                        onClick = {
                            onDismiss()
                            onAddToPlaylist(
                                PlaylistPreview(
                                    playlistPreview.playlist,
                                    playlistPreview.songCount
                                )
                            )
                        },
                        trailingContent = {
                            IconButton(
                                icon = R.drawable.open,
                                color = colorPalette().text,
                                onClick = {
                                    if (onGoToPlaylist != null) {
                                        onGoToPlaylist(playlistPreview.playlist.id)
                                        onDismiss()
                                    }
                                    NavRoutes.localPlaylist.navigateHere( navController, playlistPreview.playlist.id )
                                },
                                modifier = Modifier
                                    .size(24.dp)
                            )
                        }
                    )
                }
            }
        }

        if (unpinnedPlaylists.isNotEmpty()) {
            BasicText(
                text = stringResource(R.string.playlists),
                style = typography().m.semiBold,
                modifier = Modifier.padding(start = 20.dp, top = 5.dp)
            )

            onAddToPlaylist.let { onAddToPlaylist ->
                unpinnedPlaylists.forEach { playlistPreview ->
                    MenuEntry(
                        icon = R.drawable.add_in_playlist,
                        text = cleanPrefix(playlistPreview.playlist.name),
                        secondaryText = "${playlistPreview.songCount} " + stringResource(R.string.songs),
                        onClick = {
                            onDismiss()
                            onAddToPlaylist(
                                PlaylistPreview(
                                    playlistPreview.playlist,
                                    playlistPreview.songCount
                                )
                            )
                        },
                        trailingContent = {
                            IconButton(
                                icon = R.drawable.open,
                                color = colorPalette().text,
                                onClick = {
                                    if (onGoToPlaylist != null) {
                                        onGoToPlaylist(playlistPreview.playlist.id)
                                        onDismiss()
                                    }
                                    NavRoutes.localPlaylist.navigateHere( navController, playlistPreview.playlist.id )
                                },
                                modifier = Modifier
                                    .size(24.dp)
                            )

                        }
                    )
                }
            }
        }
    }
}
