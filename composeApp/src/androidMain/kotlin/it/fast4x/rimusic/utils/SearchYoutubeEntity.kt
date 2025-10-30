package it.fast4x.rimusic.utils

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.rimusic.component.song.SongItem
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.bodies.ContinuationBody
import it.fast4x.innertube.models.bodies.SearchBody
import it.fast4x.innertube.requests.searchPage
import it.fast4x.innertube.utils.from
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import it.fast4x.rimusic.ui.components.themed.NonQueuedMediaItemMenu
import it.fast4x.rimusic.ui.components.themed.Title
import it.fast4x.rimusic.ui.screens.searchresult.ItemsPage
import it.fast4x.rimusic.ui.styling.LocalAppearance
import me.knighthat.utils.Toaster

@ExperimentalAnimationApi
@ExperimentalTextApi
@ExperimentalFoundationApi
@UnstableApi
@Composable
fun SearchYoutubeEntity (
    navController: NavController,
    onDismiss: () -> Unit,
    query: String,
    filter: Innertube.SearchFilter = Innertube.SearchFilter.Video
) {
    val binder = LocalPlayerServiceBinder.current
    val menuState = LocalMenuState.current
    val hapticFeedback = LocalHapticFeedback.current
    val appearance = LocalAppearance.current
    val thumbnailHeightDp = 72.dp
    val thumbnailWidthDp = 128.dp
    val emptyItemsText = stringResource(R.string.no_results_found)
    val headerContent: @Composable (textButton: (@Composable () -> Unit)?) -> Unit = {
        Title(
            title = stringResource(id = R.string.videos),
            modifier = Modifier.padding(bottom = 12.dp)
        )
    }
    val isVideoEnabled by Preferences.PLAYER_ACTION_TOGGLE_VIDEO

    Box(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .padding(top = 16.dp)
                .padding(horizontal = 16.dp)
        ) {
            var currentlyPlaying by remember { mutableStateOf(binder?.player?.currentMediaItem?.mediaId) }
            binder?.player?.DisposableListener {
                object : Player.Listener {
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int ) {
                        currentlyPlaying = mediaItem?.mediaId
                    }
                }
            }
            val songItemValues = remember( appearance ) {
                SongItem.Values.from( appearance )
            }

            ItemsPage(
                tag = "searchYTEntity/$query/videos",
                itemsPageProvider = { continuation ->
                    if (continuation == null) {
                        Innertube.searchPage(
                            body = SearchBody(
                                query = query,
                                params = Innertube.SearchFilter.Video.value
                            ),
                            fromMusicShelfRendererContent = Innertube.VideoItem::from
                        )
                    } else {
                        Innertube.searchPage(
                            body = ContinuationBody(continuation = continuation),
                            fromMusicShelfRendererContent = Innertube.VideoItem::from
                        )
                    }
                },
                emptyItemsText = emptyItemsText,
                headerContent = headerContent,
                itemContent = { video ->
                    SwipeablePlaylistItem(
                        mediaItem = video.asMediaItem,
                        onPlayNext = { binder?.player?.addNext( video ) },
                        onDownload = {
                            Toaster.w( R.string.downloading_videos_not_supported )
                        },
                        onEnqueue = { binder?.player?.enqueue( video ) }
                    ) {
                        SongItem.Render(
                            innertubeVideo = video,
                            hapticFeedback = hapticFeedback,
                            isPlaying = currentlyPlaying == video.key,
                            values = songItemValues,
                            thumbnailSizeDp = DpSize(thumbnailWidthDp, thumbnailHeightDp),
                            onClick = {
                                binder?.stopRadio()
                                if ( isVideoEnabled )
                                    binder?.player?.playVideo( video.asMediaItem )
                                else
                                    binder?.player?.forcePlay( video )

                                onDismiss()
                            },
                            onLongClick = {
                                menuState.display {
                                    NonQueuedMediaItemMenu(
                                        navController = rememberNavController(),
                                        mediaItem = video.asMediaItem,
                                        onDismiss = menuState::hide
                                    )
                                };
                                hapticFeedback.performHapticFeedback(
                                    HapticFeedbackType.LongPress
                                )
                            }
                        )
                    }
                },
                itemPlaceholderContent = {
                    SongItem.Placeholder(
                        DpSize(thumbnailWidthDp, thumbnailHeightDp)
                    )
                }
            )
        }
    }
}