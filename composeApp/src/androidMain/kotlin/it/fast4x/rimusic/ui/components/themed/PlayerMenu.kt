package it.fast4x.rimusic.ui.components.themed

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.navigation.NavController
import app.kreate.compose.R
import app.kreate.database.Database
import app.kreate.database.insertIgnore
import app.kreate.database.mapIgnore
import app.kreate.di.CacheType
import app.kreate.player.Player
import app.kreate.utils.Toaster
import it.fast4x.rimusic.enums.MenuStyle
import it.fast4x.rimusic.service.MyDownloadHelper
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import it.fast4x.rimusic.utils.asSong
import it.fast4x.rimusic.utils.isNetworkConnected
import it.fast4x.rimusic.utils.rememberEqualizerLauncher
import org.koin.compose.koinInject
import org.koin.java.KoinJavaComponent.inject


@ExperimentalTextApi
@ExperimentalAnimationApi
@UnstableApi
@Composable
fun PlayerMenu(
    navController: NavController,
    mediaItem: MediaItem,
    onDismiss: () -> Unit,
    onClosePlayer: () -> Unit,
    onMatchingSong: (() -> Unit)? = null
    ) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val player: Player = koinInject()
    val menuStyle by app.kreate.preferences.Preferences.MENU_STYLE.collectAsStateWithLifecycle()

    //val context = LocalContext.current

    val launchEqualizer by rememberEqualizerLauncher(audioSessionId = { player.audioSessionId })

    val activityResultLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    var isHiding by remember {
        mutableStateOf(false)
    }

    if (isHiding) {
        ConfirmationDialog(
            text = stringResource(R.string.update_song),
            onDismiss = { isHiding = false },
            onConfirm = {
                onDismiss()

                val cache: Cache by inject(Cache::class.java, CacheType.CACHE)
                val downloadCache: Cache by inject(Cache::class.java, CacheType.DOWNLOAD)

                cache.removeResource(mediaItem.mediaId)
                downloadCache.removeResource(mediaItem.mediaId)
                Database.asyncTransaction {
                    songTable.updateTotalPlayTime( mediaItem.mediaId, 0 )
                }
            }
        )
    }


    if (menuStyle == MenuStyle.Grid) {
        BaseMediaItemGridMenu(
            navController = navController,
            mediaItem = mediaItem,
            onDismiss = onDismiss,
            onStartRadio = {
                player.startRadio( mediaItem )

                menuState.hide()
            },
            onGoToEqualizer = launchEqualizer,
            /*
            onGoToEqualizer = {
                try {
                    activityResultLauncher.launch(
                        Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                        }
                    )
                } catch (e: ActivityNotFoundException) {
                    SmartMessage(context.resources.getString(R.string.info_not_find_application_audio), type = PopupType.Warning, context = context)
                }
            },
             */
            onHideFromDatabase = { isHiding = true },
            onClosePlayer = onClosePlayer
        )
    } else {
        BaseMediaItemMenu(
            navController = navController,
            mediaItem = mediaItem,
            onStartRadio = {
                player.startRadio( mediaItem )

                menuState.hide()
            },
            onGoToEqualizer = launchEqualizer,
            onShowSleepTimer = {},
            onHideFromDatabase = { isHiding = true },
            onDismiss = onDismiss,
            onAddToPreferites = {
                if (!isNetworkConnected(context) && isYouTubeSyncEnabled()){
                    Toaster.noInternet()
                } else if (!isYouTubeSyncEnabled()){
                    Database.asyncTransaction {
                        songTable.likeState( mediaItem.mediaId, true )
                        MyDownloadHelper.autoDownloadWhenLiked(mediaItem)
                    }
                }
            },
            onClosePlayer = onClosePlayer,
            onMatchingSong = onMatchingSong
        )
    }

}


@ExperimentalTextApi
@ExperimentalAnimationApi
@UnstableApi
@Composable
fun MiniPlayerMenu(
    navController: NavController,
    mediaItem: MediaItem,
    onDismiss: () -> Unit,
    onClosePlayer: () -> Unit
) {
    val context = LocalContext.current
    val menuStyle by app.kreate.preferences.Preferences.MENU_STYLE.collectAsStateWithLifecycle()

    if (menuStyle == MenuStyle.Grid) {
        MiniMediaItemGridMenu(
            navController = navController,
            mediaItem = mediaItem,
            onGoToPlaylist = {
                onClosePlayer()
            },
            onAddToPreferites = {
                if (!isNetworkConnected(context) && isYouTubeSyncEnabled()){
                    Toaster.noInternet()
                } else if (!isYouTubeSyncEnabled()){
                    Database.asyncTransaction {
                        songTable.likeState( mediaItem.mediaId, true )
                        MyDownloadHelper.autoDownloadWhenLiked(mediaItem)
                    }
                }
            },
            onDismiss = onDismiss
        )
    } else {
        MiniMediaItemMenu(
            navController = navController,
            mediaItem = mediaItem,
            onGoToPlaylist = {
                onClosePlayer()
            },
            onAddToPreferites = {
                if (!isNetworkConnected(context) && isYouTubeSyncEnabled()){
                    Toaster.noInternet()
                } else if (!isYouTubeSyncEnabled()){
                    Database.asyncTransaction {
                        songTable.likeState( mediaItem.mediaId, true )
                        MyDownloadHelper.autoDownloadWhenLiked(mediaItem)
                    }
                }
            },
            onDismiss = onDismiss
        )
    }

}

@ExperimentalTextApi
@ExperimentalAnimationApi
@UnstableApi
@Composable
fun AddToPlaylistPlayerMenu(
    navController: NavController,
    mediaItem: MediaItem,
    onDismiss: () -> Unit,
    onClosePlayer: () -> Unit,
) {
    AddToPlaylistItemMenu(
        navController = navController,
        mediaItem = mediaItem,
        onGoToPlaylist = {
            onClosePlayer()
        },
        onAddToPlaylist = { playlist, position ->
            if (!isYouTubeSyncEnabled() || !playlist.isYoutubePlaylist){
                Database.asyncTransaction {
                    insertIgnore( mediaItem )
                    mapIgnore( playlist, mediaItem.asSong )
                }
            }
        },
        onRemoveFromPlaylist = { playlist ->
            if(isYouTubeSyncEnabled() && playlist.isYoutubePlaylist && playlist.isEditable) {
                return@AddToPlaylistItemMenu
            } else
                Database.asyncTransaction {
                    songPlaylistMapTable.deleteBySongId( mediaItem.mediaId, playlist.id )
                }
        },
        onDismiss = onDismiss,
    )
}

@ExperimentalTextApi
@ExperimentalAnimationApi
@UnstableApi
@Composable
fun AddToPlaylistArtistSongs(
    navController: NavController,
    mediaItems: List<MediaItem>,
    onDismiss: () -> Unit,
    onClosePlayer: () -> Unit,
) {
    AddToPlaylistArtistSongsMenu(
        navController = navController,
        onGoToPlaylist = {
            onClosePlayer()
        },
        onAddToPlaylist = { playlistPreview ->
            Database.asyncTransaction {
                if ( !isYouTubeSyncEnabled() || !playlistPreview.playlist.isYoutubePlaylist )
                    mapIgnore( playlistPreview.playlist, *mediaItems.toTypedArray() )
            }

            onDismiss()
        },
        onDismiss = onDismiss,
    )
}