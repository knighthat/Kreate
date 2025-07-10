package it.fast4x.rimusic.ui.screens.statistics

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import app.kreate.android.Preferences
import app.kreate.android.R
import coil.compose.AsyncImage
import coil.request.ImageRequest
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.LocalPlayerAwareWindowInsets
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.enums.StatisticsCategory
import it.fast4x.rimusic.enums.StatisticsType
import it.fast4x.rimusic.models.Song
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.ButtonsRow
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import it.fast4x.rimusic.ui.components.themed.NonQueuedMediaItemMenu
import it.fast4x.rimusic.ui.items.AlbumItem
import it.fast4x.rimusic.ui.items.ArtistItem
import it.fast4x.rimusic.ui.items.PlaylistItem
import it.fast4x.rimusic.ui.items.SongItem
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.px
import it.fast4x.rimusic.ui.styling.shimmer
import it.fast4x.rimusic.utils.UpdateYoutubeAlbum
import it.fast4x.rimusic.utils.UpdateYoutubeArtist
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.center
import it.fast4x.rimusic.utils.color
import it.fast4x.rimusic.utils.forcePlayAtIndex
import it.fast4x.rimusic.utils.formatAsTime
import it.fast4x.rimusic.utils.getDownloadState
import it.fast4x.rimusic.utils.isDownloadedSong
import it.fast4x.rimusic.utils.isNowPlaying
import it.fast4x.rimusic.utils.manageDownload
import it.fast4x.rimusic.utils.semiBold
import it.fast4x.rimusic.utils.thumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

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
    val binder = LocalPlayerServiceBinder.current
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
    val disableScrollingText by Preferences.SCROLLING_TEXT_DISABLED

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
    var totalPlayTimes by remember { mutableLongStateOf(0L) }
    val songs by remember {
        Database.eventTable
                .findSongsMostPlayedBetween(
                    from = from,
                    limit = maxStatisticsItems.toInt()
                )
                .distinctUntilChanged()
                .onEach {
                    totalPlayTimes = it.sumOf( Song::totalPlayTimeMs )
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
                        iconId = statisticsType.iconId,
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
                                "${formatAsTime(totalPlayTimes)} ${context.getString( R.string.statistics_of_time_taken )}"
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


                    items(
                        count = songs.count(),
                    ) {

                        downloadState = getDownloadState(songs.get(it).asMediaItem.mediaId)
                        val isDownloaded = isDownloadedSong(songs.get(it).asMediaItem.mediaId)
                        var forceRecompose by remember { mutableStateOf(false) }
                        SongItem(
                            song = songs.get(it).asMediaItem,
                            onDownloadClick = {
                                binder?.cache?.removeResource(songs.get(it).asMediaItem.mediaId)
                                Database.asyncTransaction {
                                    formatTable.deleteBySongId( songs[it].id )
                                }
                                manageDownload(
                                    context = context,
                                    mediaItem = songs.get(it).asMediaItem,
                                    downloadState = isDownloaded
                                )
                            },
                            downloadState = downloadState,
                            thumbnailSizeDp = thumbnailSizeDp,
                            thumbnailSizePx = thumbnailSize,
                            onThumbnailContent = {
                                BasicText(
                                    text = "${it + 1}",
                                    style = typography().s.semiBold.center.color(colorPalette().text),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .width(thumbnailSizeDp)
                                        .align(Alignment.Center)
                                )
                            },
                            modifier = Modifier
                                .combinedClickable(
                                    onLongClick = {
                                        menuState.display {
                                            NonQueuedMediaItemMenu(
                                                navController = navController,
                                                mediaItem = songs.get(it).asMediaItem,
                                                onDismiss = {
                                                    menuState.hide()
                                                    forceRecompose = true
                                                },
                                                disableScrollingText = disableScrollingText
                                            )
                                        }
                                    },
                                    onClick = {
                                        binder?.stopRadio()
                                        binder?.player?.forcePlayAtIndex(
                                            songs.map(Song::asMediaItem),
                                            it
                                        )
                                    }
                                )
                                .fillMaxWidth(),
                            disableScrollingText = disableScrollingText,
                            isNowPlaying = binder?.player?.isNowPlaying(songs.get(it).id) ?: false,
                            forceRecompose = forceRecompose
                        )
                    }
                }

                if (statisticsCategory == StatisticsCategory.Artists)
                    items(
                        count = artists.count()
                    ) {

                        if (artists[it].thumbnailUrl.toString() == "null")
                            UpdateYoutubeArtist(artists[it].id)

                        ArtistItem(
                            thumbnailUrl = artists[it].thumbnailUrl,
                            name = "${it+1}. ${artists[it].name}",
                            showName = true,
                            subscribersCount = null,
                            thumbnailSizePx = artistThumbnailSizePx,
                            thumbnailSizeDp = artistThumbnailSizeDp,
                            alternative = true,
                            modifier = Modifier.clickable {
                                if ( artists[it].id.isNotBlank() )
                                    NavRoutes.YT_ARTIST.navigateHere( navController, artists[it].id )
                            },
                            disableScrollingText = disableScrollingText
                        )
                    }

                if (statisticsCategory == StatisticsCategory.Albums)
                    items(
                        count = albums.count()
                    ) {

                        if (albums[it].thumbnailUrl.toString() == "null")
                            UpdateYoutubeAlbum(albums[it].id)

                        AlbumItem(
                            thumbnailUrl = albums[it].thumbnailUrl,
                            title = "${it+1}. ${albums[it].title}",
                            authors = albums[it].authorsText,
                            year = albums[it].year,
                            thumbnailSizePx = albumThumbnailSizePx,
                            thumbnailSizeDp = albumThumbnailSizeDp,
                            alternative = true,
                            modifier = Modifier
                                .clickable(onClick = {
                                    if (albums[it].id != "")
                                        NavRoutes.YT_ALBUM.navigateHere( navController, albums[it].id )
                                }),
                            disableScrollingText = disableScrollingText
                        )
                    }

                if (statisticsCategory == StatisticsCategory.Playlists) {
                    items(
                        count = playlists.count()
                    ) {
                        val thumbnails by remember {
                            Database.songPlaylistMapTable
                                    .sortSongsByPlayTime( playlists[it].playlist.id )
                                    .distinctUntilChanged()
                                    .map { list ->
                                        list.takeLast( 4 ).map { song ->
                                            song.thumbnailUrl.thumbnail( playlistThumbnailSizePx / 2 )
                                        }
                                    }
                        }.collectAsState( emptyList(), Dispatchers.IO )

                        PlaylistItem(
                            thumbnailContent = {
                                if (thumbnails.toSet().size == 1) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(thumbnails.first())
                                            .setHeader("User-Agent", "Mozilla/5.0")
                                            .build(), //thumbnails.first().thumbnail(thumbnailSizePx),
                                        onError = {error ->
                                            Timber.e("Failed AsyncImage in PlaylistItem ${error.result.throwable.stackTraceToString()}")
                                        },
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                    ) {
                                        listOf(
                                            Alignment.TopStart,
                                            Alignment.TopEnd,
                                            Alignment.BottomStart,
                                            Alignment.BottomEnd
                                        ).forEachIndexed { index, alignment ->
                                            val thumbnail = thumbnails.getOrNull(index)
                                            if (thumbnail != null)
                                                AsyncImage(
                                                    model = ImageRequest.Builder(LocalContext.current)
                                                        .data(thumbnail)
                                                        .setHeader("User-Agent", "Mozilla/5.0")
                                                        .build(),
                                                    onError = {error ->
                                                        Timber.e("Failed AsyncImage 1 in PlaylistItem ${error.result.throwable.stackTraceToString()}")
                                                    },
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .align(alignment)
                                                        .size(playlistThumbnailSizeDp /2)
                                                )
                                        }
                                    }
                                }
                            },
                            songCount = playlists[it].songCount,
                            name = "${it+1}. ${playlists[it].playlist.name}",
                            channelName = null,
                            thumbnailSizeDp = playlistThumbnailSizeDp,
                            alternative = true,
                            modifier = Modifier.clickable {
                                val playlist = playlists[it].playlist
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
                            },
                            disableScrollingText = disableScrollingText
                        )
                    }
                }


            }

            Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))

        }
}
