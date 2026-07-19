package it.fast4x.rimusic.utils

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.compose.rememberNavController
import app.kreate.android.themed.rimusic.component.song.SongItem
import app.kreate.android.utils.innertube.toMediaItem
import app.kreate.android.utils.shallowCompare
import app.kreate.android.viewmodel.SearchResultViewModel
import app.kreate.compose.R
import app.kreate.gateway.innertube.SearchFilter
import app.kreate.gateway.innertube.models.InnertubeItem
import app.kreate.gateway.innertube.models.InnertubeVideo
import app.kreate.player.Player
import app.kreate.preferences.Preferences
import app.kreate.utils.Toaster
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import it.fast4x.rimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import it.fast4x.rimusic.ui.components.themed.NonQueuedMediaItemMenu
import it.fast4x.rimusic.ui.components.themed.Title
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel


@ExperimentalAnimationApi
@ExperimentalTextApi
@ExperimentalFoundationApi
@UnstableApi
@Composable
fun SearchYoutubeEntity (
    onDismiss: () -> Unit,
    query: String,
    viewModel: SearchResultViewModel = koinViewModel()
) {
    val player: Player = koinInject()
    val menuState = LocalMenuState.current
    val hapticFeedback = LocalHapticFeedback.current
    val appearance = LocalAppearance.current
    val lazyListState = rememberLazyListState()
    val isVideoEnabled by Preferences.PLAYER_ACTION_TOGGLE_VIDEO.collectAsStateWithLifecycle()

    val items by viewModel.searchResults.collectAsStateWithLifecycle()
    val isFetching by viewModel.isFetching.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle(false)

    Box(
        Modifier.background( colorPalette().background0 )
                .fillMaxSize()
    ) {
        Column(
            Modifier.padding( top = 16.dp ).padding( horizontal = 16.dp )
        ) {
            val currentMediaItem by player.currentMediaItemState.collectAsState()
            val songItemValues = remember( appearance ) {
                SongItem.Values.from( appearance )
            }

            Box(
                modifier = Modifier
                    .background(colorPalette().background0)
                    .fillMaxHeight()
                    .fillMaxWidth(
                        if( NavigationBarPosition.Right.isCurrent() )
                            Dimensions.contentWidthRightBar
                        else
                            1f
                    )
            ) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Dimensions.bottomSpacer)
                ) {
                    item(
                        key = "header",
                        contentType = "header",
                    ) {
                        Title(
                            title = stringResource(id = R.string.videos),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    items(
                        items = items.mapNotNull { it as? InnertubeVideo },
                        key = InnertubeItem::id
                    ) { video ->
                        val mediaItem = video.toMediaItem

                        SwipeablePlaylistItem(
                            mediaItem = mediaItem,
                            onPlayNext = {
                                player.addNext( mediaItem )
                            },
                            onDownload = {
                                Toaster.w( R.string.downloading_videos_not_supported )
                            },
                            onEnqueue = {
                                player.enqueue( mediaItem )
                            }
                        ) {
                            SongItem.Render(
                                innertubeSong = video,
                                hapticFeedback = hapticFeedback,
                                isPlaying = video.shallowCompare( currentMediaItem ),
                                values = songItemValues,
                                onClick = {
                                    if ( isVideoEnabled )
                                        player.playVideo( mediaItem )
                                    else
                                        player.play( mediaItem )

                                    onDismiss()
                                },
                                onLongClick = {
                                    menuState.display {
                                        NonQueuedMediaItemMenu(
                                            navController = rememberNavController(),
                                            mediaItem = mediaItem,
                                            onDismiss = menuState::hide
                                        )
                                    };
                                    hapticFeedback.performHapticFeedback(
                                        HapticFeedbackType.LongPress
                                    )
                                }
                            )
                        }
                    }

                    if ( !isFetching && items.isEmpty() )
                        item(key = "empty") {
                            BasicText(
                                text = stringResource( R.string.no_results_found ),
                                style = typography().xs.secondary.center,
                                modifier = Modifier.padding( horizontal = 16.dp, vertical = 32.dp ).fillMaxWidth()
                            )
                        }

                    if( items.isNotEmpty() && hasMore && !isFetching )
                        item( contentType = SearchResultViewModel.GetMore ) {
                            SongItem.Placeholder()
                        }
                }
                FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)
            }

            LaunchedEffect( lazyListState ) {
                snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo }
                    .map { visibleItems ->
                        visibleItems.any { it.contentType === SearchResultViewModel.GetMore }
                    }
                    .distinctUntilChanged()
                    .collectLatest {
                        if( it ) viewModel.onGetMore()
                    }
            }
            LaunchedEffect( Unit ) {
                viewModel.onFilterChanged( query, SearchFilter.VIDEOS )
            }
        }
    }
}