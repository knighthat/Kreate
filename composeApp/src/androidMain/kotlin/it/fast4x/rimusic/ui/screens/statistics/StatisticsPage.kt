package it.fast4x.rimusic.ui.screens.statistics

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.themed.rimusic.component.album.AlbumItem
import app.kreate.android.themed.rimusic.component.artist.ArtistItem
import app.kreate.android.themed.rimusic.component.playlist.PlaylistItem
import app.kreate.android.themed.rimusic.component.song.SongItem
import app.kreate.database.models.Song
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.LocalPlayerAwareWindowInsets
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.enums.StatisticsCategory
import it.fast4x.rimusic.enums.StatisticsType
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.ButtonsRow
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import it.fast4x.rimusic.ui.components.themed.NonQueuedMediaItemMenu
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.ui.styling.px
import it.fast4x.rimusic.ui.styling.shimmer
import it.fast4x.rimusic.utils.DisposableListener
import it.fast4x.rimusic.utils.UpdateYoutubeAlbum
import it.fast4x.rimusic.utils.UpdateYoutubeArtist
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.center
import it.fast4x.rimusic.utils.color
import it.fast4x.rimusic.utils.forcePlayAtIndex
import it.fast4x.rimusic.utils.semiBold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@OptIn(ExperimentalCoroutinesApi::class)
@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun StatisticsPage(
    navController: NavController,
    statisticsType: StatisticsType
) {
    val binder = LocalPlayerServiceBinder.current ?: return
    val (colorPalette, typography) = LocalAppearance.current
    val hapticFeedback = LocalHapticFeedback.current
    val menuState = LocalMenuState.current
    val windowInsets = LocalPlayerAwareWindowInsets.current

    val albumThumbnailSizeDp = 108.dp
    val albumThumbnailSizePx = albumThumbnailSizeDp.px
    val artistThumbnailSizeDp = 92.dp
    val artistThumbnailSizePx = artistThumbnailSizeDp.px
    val playlistThumbnailSizeDp = 108.dp
    val playlistThumbnailSizePx = playlistThumbnailSizeDp.px

    val endPaddingValues = windowInsets.only(WindowInsetsSides.End).asPaddingValues()

    val thumbnailRoundness by Preferences.THUMBNAIL_BORDER_RADIUS

    val showStatsListeningTime by Preferences.SHOW_LISTENING_STATS

    val context = LocalContext.current

    val thumbnailSizeDp = Dimensions.thumbnails.song
    val thumbnailSize = thumbnailSizeDp.px

    val maxStatisticsItems by Preferences.MAX_NUMBER_OF_STATISTIC_ITEMS
    val from = remember( statisticsType ) { statisticsType.timeStampInMillis() }

    val artists by remember {
        Database.eventTable
                .findArtistsMostPlayedBetween(
                    from = from,
                    limit = maxStatisticsItems.toInt()
                )
                .distinctUntilChanged()
    }.collectAsState( emptyList(), Dispatchers.IO )
    val albums by remember {
        Database.eventTable
                .findAlbumsMostPlayedBetween(
                    from = from,
                    limit = maxStatisticsItems.toInt()
                )
                .distinctUntilChanged()
    }.collectAsState( emptyList(), Dispatchers.IO )
    val playlists by remember {
        Database.eventTable
                .findPlaylistMostPlayedBetweenAsPreview(
                    from = from,
                    limit = maxStatisticsItems.toInt()
                )
                .distinctUntilChanged()
    }.collectAsState( emptyList(), Dispatchers.IO )
    var totalDuration by remember { mutableStateOf(Duration.ZERO) }
    val songs by remember {
        Database.eventTable
                .findSongsMostPlayedBetween(
                    from = from,
                    limit = maxStatisticsItems.toInt()
                )
                .distinctUntilChanged()
                .onEach {
                    totalDuration = it.sumOf( Song::totalPlayTimeMs ).toDuration( DurationUnit.MILLISECONDS )
                }
                .map { it.take( maxStatisticsItems.toInt() ) }
    }.collectAsState( emptyList(), Dispatchers.IO )

    var downloadState by remember {
        mutableStateOf(Download.STATE_STOPPED)
    }

    val navigationBarPosition by Preferences.NAVIGATION_BAR_POSITION

    var statisticsCategory by Preferences.STATISTIC_PAGE_CATEGORY
    val buttonsList = listOf(
        StatisticsCategory.Songs to StatisticsCategory.Songs.text,
        StatisticsCategory.Artists to StatisticsCategory.Artists.text,
        StatisticsCategory.Albums to StatisticsCategory.Albums.text,
        StatisticsCategory.Playlists to StatisticsCategory.Playlists.text
    )

    val playlistItemValues = remember( colorPalette, typography ) {
        PlaylistItem.Values.from( colorPalette, typography )
    }

    Box(
        modifier = Modifier
            .background(colorPalette().background0)
            //.fillMaxSize()
            .fillMaxHeight()
            .fillMaxWidth(
                if (navigationBarPosition == NavigationBarPosition.Left ||
                    navigationBarPosition == NavigationBarPosition.Top ||
                    navigationBarPosition == NavigationBarPosition.Bottom
                ) 1f
                else Dimensions.contentWidthRightBar
            )
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
        val albumItemValues = remember( colorPalette, typography ) {
            AlbumItem.Values.from( colorPalette, typography )
        }
        val artistItemValues = remember( colorPalette, typography ) {
            ArtistItem.Values.from( colorPalette, typography )
        }

            val lazyGridState = rememberLazyGridState()
            LazyVerticalGrid(
                state = lazyGridState,
                columns = GridCells.Adaptive(
                    if(statisticsCategory == StatisticsCategory.Songs) 200.dp else playlistThumbnailSizeDp
                ),
                modifier = Modifier
                    .background(colorPalette().background0)
                    .fillMaxSize()
            ) {

                item(
                    key = "header",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    HeaderWithIcon(
                        title = statisticsType.text,
                        iconId = statisticsType.androidIconId,
                        enabled = true,
                        showIcon = true,
                        modifier = Modifier,
                        onClick = {}
                    )
                }

                item(
                    key = "header_tabs",
                    span = { GridItemSpan(maxLineSpan) }
                ) {

                    ButtonsRow(
                        chips = buttonsList,
                        currentValue = statisticsCategory,
                        onValueUpdate = { statisticsCategory = it },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                }

                if (statisticsCategory == StatisticsCategory.Songs) {

                    if (showStatsListeningTime)
                        item(
                            key = "headerListeningTime",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            val title by remember { derivedStateOf {
                                "${songs.size} ${context.getString( R.string.statistics_songs_heard )}"
                            }}
                            val subtitle by remember { derivedStateOf {
                                "$totalDuration ${context.getString( R.string.statistics_of_time_taken )}"
                            }}

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(
                                                       start = 16.dp,
                                                       end = 16.dp,
                                                       bottom = 8.dp
                                                   )
                                                   .fillMaxWidth()
                                                   .background(
                                                       color = colorPalette().background4,
                                                       shape = thumbnailRoundness.shape
                                                   )
                                                   .padding( all =  12.dp )
                            ) {
                                Column( Modifier.weight( 1f ) ) {
                                    BasicText(
                                        text = title,
                                        maxLines = 1,
                                        style = typography().xs
                                                            .semiBold
                                                            .copy( colorPalette().text ),
                                        modifier = Modifier.padding( bottom = 4.dp )
                                    )

                                    BasicText(
                                        text = subtitle,
                                        maxLines = 2,
                                        style = typography().xs
                                                            .semiBold
                                                            .copy( colorPalette().textSecondary )
                                    )
                                }

                                Icon(
                                    painter = painterResource( R.drawable.musical_notes ),
                                    contentDescription = null,
                                    tint = colorPalette().shimmer,
                                    modifier = Modifier.size( 34.dp )
                                )
                            }
                        }

                    itemsIndexed(
                        items = songs,
                        key = { i, s -> "${System.identityHashCode(s)}-$i"}
                    ) { index, song ->
                        SongItem.Render(
                            song = song,
                            context = context,
                            binder = binder,
                            hapticFeedback = hapticFeedback,
                            values = songItemValues,
                            isPlaying = song.id == currentlyPlaying,
                            navController = navController,
                            thumbnailOverlay = {
                                BasicText(
                                    text = "${index + 1}",
                                    style = typography().s.semiBold.center.color(colorPalette().text),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.width( thumbnailSizeDp )
                                                       .align( Alignment.Center )
                                )
                            },
                            onLongClick = {
                                menuState.display {
                                    NonQueuedMediaItemMenu(
                                        navController = navController,
                                        mediaItem = song.asMediaItem,
                                        onDismiss = menuState::hide
                                    )
                                }
                            },
                            onClick = {
                                binder.stopRadio()
                                binder.player.forcePlayAtIndex( songs, index )
                            }
                        )
                    }
                }

                if (statisticsCategory == StatisticsCategory.Artists)
                    items(
                        items = artists,
                        key = System::identityHashCode
                    ) { artist ->
                        artist.thumbnailUrl ?: UpdateYoutubeArtist( artist.id )

                        ArtistItem.Render(
                            artist = artist,
                            widthDp = artistThumbnailSizeDp,
                            values = artistItemValues,
                            navController = navController
                        )
                    }

                if (statisticsCategory == StatisticsCategory.Albums)
                    items(
                        items = albums,
                        key = System::identityHashCode
                    ) { album ->
                        album.thumbnailUrl ?: UpdateYoutubeAlbum( album.id )

                        AlbumItem.Vertical(
                            album = album,
                            widthDp = albumThumbnailSizeDp,
                            values = albumItemValues,
                            showArtists = false,
                            showYear = false,
                            navController = navController
                        )
                    }

                if (statisticsCategory == StatisticsCategory.Playlists)
                    items(
                        items = playlists,
                        key = { p -> p.playlist.id }
                    ) { preview ->
                        PlaylistItem.Vertical(
                            playlist = preview.playlist,
                            widthDp = playlistThumbnailSizeDp,
                            values = playlistItemValues,
                            songCount = preview.songCount,
                            navController = null,
                            onClick = {
                                val playlist = preview.playlist
                                val route: NavRoutes
                                val path: String

                                if( !playlist.browseId.isNullOrBlank() ) {
                                    route = NavRoutes.YT_PLAYLIST
                                    path = playlist.browseId
                                } else {
                                    route = NavRoutes.localPlaylist
                                    path = playlist.id.toString()
                                }

                                route.navigateHere( navController, path )
                            }
                        )
                    }
            }

            Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))

        }
}
