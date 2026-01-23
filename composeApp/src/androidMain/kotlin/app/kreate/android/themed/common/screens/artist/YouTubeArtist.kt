package app.kreate.android.themed.common.screens.artist

import android.content.Context
import android.content.Intent
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastMapNotNull
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.R
import app.kreate.android.coil3.ImageFactory
import app.kreate.android.themed.common.component.tab.DeleteAllDownloadedDialog
import app.kreate.android.themed.common.component.tab.DownloadAllDialog
import app.kreate.android.themed.rimusic.component.album.AlbumItem
import app.kreate.android.themed.rimusic.component.song.SongItem
import app.kreate.android.utils.ItemUtils
import app.kreate.android.utils.innertube.toMediaItem
import app.kreate.android.utils.innertube.toSong
import app.kreate.android.utils.renderDescription
import app.kreate.android.utils.scrollingText
import app.kreate.android.utils.shallowCompare
import app.kreate.android.viewmodel.YoutubeArtistViewModel
import app.kreate.database.models.Song
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.service.modern.PlayerServiceModern
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.Skeleton
import it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import it.fast4x.rimusic.ui.components.themed.AutoResizeText
import it.fast4x.rimusic.ui.components.themed.Enqueue
import it.fast4x.rimusic.ui.components.themed.FontSizeRange
import it.fast4x.rimusic.ui.components.themed.PlayNext
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.addNext
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.enqueue
import it.fast4x.rimusic.utils.fadingEdge
import it.fast4x.rimusic.utils.forcePlayAtIndex
import it.fast4x.rimusic.utils.isLandscape
import it.fast4x.rimusic.utils.isNetworkConnected
import it.fast4x.rimusic.utils.medium
import it.fast4x.rimusic.utils.semiBold
import me.knighthat.component.artist.FollowButton
import me.knighthat.component.tab.Radio
import me.knighthat.component.tab.SongShuffler
import me.knighthat.component.ui.screens.DynamicOrientationLayout
import me.knighthat.innertube.Constants
import me.knighthat.innertube.model.InnertubeAlbum
import me.knighthat.innertube.model.InnertubeArtist
import me.knighthat.innertube.model.InnertubeSong


@OptIn(UnstableApi::class)
private fun LazyListScope.renderSections(
    navController: NavController,
    context: Context,
    binder: PlayerServiceModern.Binder,
    hapticFeedback: HapticFeedback,
    currentMedia: MediaItem?,
    songItemValues: SongItem.Values,
    artistPage: InnertubeArtist,
    sectionTextModifier: Modifier
) = artistPage.sections.forEach { section ->
    // Don't show section if the title or contents is null or blank
    if( section.title.isNullOrBlank() || section.contents.isEmpty() ) return@forEach

    item( "${section.title}Header" ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = sectionTextModifier.fillMaxWidth()
        ) {
            Text(
                text = section.title!!,
                style = typography().m.semiBold,
                modifier = Modifier.weight( 1f )
            )

            // TODO: Add support for playlists
            if( section.browseId != null && section.contents.fastAll { it is InnertubeSong || it is InnertubeAlbum} )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colorPalette().textSecondary,
                    modifier = Modifier.clickable {
                        val path = "${section.browseId}?params=${section.params}"

                        val route: NavRoutes = if( section.contents.fastAll { it is InnertubeSong } )
                            NavRoutes.YT_PLAYLIST
                        else if( section.contents.fastAll { it is InnertubeAlbum } )
                            NavRoutes.artistAlbums
                        else
                            return@clickable

                        route.navigateHere( navController, path )
                    }
                )
        }
    }

     /*
         Use mapNotNull with `as?` to avoid unnecessary checking (double traversal)
         Double traversal example:

         if( items.fastAll { it is InnertubeSong } )
            items.fastMap { it as InnertubeSong }
      */
    section.contents
           .fastMapNotNull { it as? InnertubeSong }
           .also { songs ->
               itemsIndexed(
                   items = songs,
                   key = { i, s -> "${System.identityHashCode( s )} - $i" }
               ) { index, song ->
                   SwipeablePlaylistItem(
                       mediaItem = song.toMediaItem,
                       onPlayNext = {
                           binder.player.addNext( song.toMediaItem )
                       }
                   ) {
                       SongItem.Render(
                           song = song.toSong,
                           context = context,
                           binder = binder,
                           hapticFeedback = hapticFeedback,
                           isPlaying = song.shallowCompare( currentMedia ),
                           values = songItemValues,
                           navController = navController,
                           showThumbnail = true,
                           onClick = {
                               binder.stopRadio()
                               binder.player.forcePlayAtIndex(
                                   songs.map( InnertubeSong::toMediaItem ),
                                   index
                               )
                           }
                       )
                   }
               }
           }

    section.contents
           .filterNot { it is InnertubeSong }
           .also { items ->
               item {
                   ItemUtils.LazyRowItem(
                       navController = navController,
                       innertubeItems = items,
                       currentlyPlaying = null
                   )
               }
           }
}

@OptIn(UnstableApi::class)
private fun LazyListScope.renderLibrarySongs(
    navController: NavController,
    context: Context,
    binder: PlayerServiceModern.Binder,
    hapticFeedback: HapticFeedback,
    currentMedia: MediaItem?,
    songItemValues: SongItem.Values,
    sectionTextModifier: Modifier,
    songs: List<Song>
) {
    item( "songs" ) {
        Text(
            text = stringResource( R.string.songs ),
            style = typography().m.semiBold,
            modifier = sectionTextModifier.fillMaxWidth()
        )
    }

    itemsIndexed(
        items = songs,
        key = { i, s -> "${System.identityHashCode( s )} - $i" }
    ) { index, song ->
        SwipeablePlaylistItem(
            mediaItem = song.asMediaItem,
            onPlayNext = {
                binder.player.addNext( song.asMediaItem )
            }
        ) {
            SongItem.Render(
                song = song,
                context = context,
                binder = binder,
                hapticFeedback = hapticFeedback,
                isPlaying = song.shallowCompare( currentMedia ),
                values = songItemValues,
                navController = navController,
                showThumbnail = true,
                onClick = {
                    binder.stopRadio()
                    binder.player.forcePlayAtIndex(
                        songs.map( Song::asMediaItem ),
                        index
                    )
                }
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun YouTubeArtist(
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: YoutubeArtistViewModel = hiltViewModel(),
    miniPlayer: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current ?: return
    val (colorPalette, typography) = LocalAppearance.current
    val hapticFeedback = LocalHapticFeedback.current
    val saveableStateHolder = rememberSaveableStateHolder()
    val dbArtist by viewModel.dbArtist.collectAsStateWithLifecycle()
    val artistPage by viewModel.artistPage.collectAsStateWithLifecycle()
    val (tabIndex, onTabChanged) = remember { mutableIntStateOf(
            if( isNetworkConnected( context ) ) 0 else 1
    ) }
    val currentMedia by binder.player.currentMediaItemState.collectAsStateWithLifecycle()
    val artistLibrarySongs by viewModel.artistLibrarySongs.collectAsStateWithLifecycle()
    val songItemValues = remember( colorPalette, typography ) {
        SongItem.Values.from( colorPalette, typography )
    }
    //<editor-fold desc="Thumbnail URL">
    val thumbnailUrl = remember( dbArtist, artistPage ) {
        if( dbArtist == null )
            artistPage?.thumbnails?.firstOrNull()?.url
        else
            dbArtist?.thumbnailUrl
    }
    val thumbnailPainter = ImageFactory.rememberAsyncImagePainter( thumbnailUrl )
    //</editor-fold>
    //<editor-fold defaultstate="collapsed" desc="Buttons">
    val followButton = FollowButton { dbArtist }
    val shuffler = SongShuffler( viewModel::getSongs )
    val downloadAllDialog = remember {
        DownloadAllDialog( binder, context, viewModel::getSongs )
    }
    val deleteAllDownloadsDialog = remember {
        DeleteAllDownloadedDialog( binder, context, viewModel::getSongs )
    }
    val radio = Radio(viewModel::getSongs)
    val playNext = PlayNext {
        viewModel.getMediaItems().let {
            binder.player.addNext( it, appContext() )
        }
    }
    val enqueue = Enqueue {
        viewModel.getMediaItems().let {
            binder.player.enqueue( it, appContext() )
        }
    }

    downloadAllDialog.Render()
    deleteAllDownloadsDialog.Render()
    //</editor-fold>

    DynamicOrientationLayout( thumbnailPainter ) {
        PullToRefreshBox(
            isRefreshing = viewModel.isRefreshing,
            onRefresh = {
                viewModel.isRefreshing = true
                viewModel.onRefresh()
            },
            modifier = modifier.fillMaxSize()
        ) {
            Skeleton(
                navController = navController,
                tabIndex = tabIndex,
                onTabChanged = onTabChanged,
                miniPlayer = miniPlayer,
                navBarContent = { item ->
                    item(0, stringResource(R.string.songs), R.drawable.musical_notes)
                    item(1, stringResource(R.string.library), R.drawable.library)
                }
            ) { currentTabIndex ->
                saveableStateHolder.SaveableStateProvider(key = currentTabIndex) {
                    LazyColumn(
                        state = viewModel.listState,
                        contentPadding = PaddingValues(bottom = Dimensions.bottomSpacer),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item( "header" ) {
                            Box( Modifier.fillMaxWidth() ) {
                                if ( !isLandscape )
                                    Image(
                                        painter = thumbnailPainter,
                                        contentDescription = null,
                                        contentScale = ContentScale.FillWidth,
                                        modifier = Modifier
                                            .aspectRatio(4f / 3)      // Limit height
                                                           .fillMaxWidth()
                                            .align(Alignment.Center)
                                            .fadingEdge(
                                                top = WindowInsets.systemBars
                                                    .asPaddingValues()
                                                    .calculateTopPadding() + Dimensions.fadeSpacingTop,
                                                bottom = Dimensions.fadeSpacingBottom
                                            )
                                    )

                                artistPage?.shareUrl( Constants.YOUTUBE_MUSIC_URL )?.also { shareUrl ->
                                    Icon(
                                        painter = painterResource( R.drawable.share_social ),
                                        // TODO: Make a separate string for this (i.e. Share to...)
                                        contentDescription = stringResource( R.string.listen_on_youtube_music ),
                                        tint = colorPalette.text.copy( .5f ),
                                        modifier = Modifier
                                            .padding(all = 5.dp)
                                            .size(40.dp)
                                            .align(Alignment.TopEnd)
                                            .clickable {
                                                val sendIntent = Intent().apply {
                                                    action = Intent.ACTION_SEND
                                                    type = "text/plain"
                                                    putExtra(Intent.EXTRA_TEXT, shareUrl)
                                                }

                                                context.startActivity(
                                                    Intent.createChooser(sendIntent, null)
                                                )
                                            }
                                    )
                                }

                                AutoResizeText(
                                    // Use local artist name (custom name), otherwise, use online name
                                    text = (dbArtist?.cleanName() ?: artistPage?.name).orEmpty(),
                                    style = typography.l.semiBold,
                                    fontSizeRange = FontSizeRange( 32.sp, 38.sp ),
                                    fontWeight = typography.l.semiBold.fontWeight,
                                    fontFamily = typography.l.semiBold.fontFamily,
                                    color = typography.l.semiBold.color,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(horizontal = 30.dp)
                                        .scrollingText()
                                )
                            }
                        }

                        artistPage?.shortNumMonthlyAudience?.also { monthlyAudience ->
                            item( "monthlyListeners" ) {
                                BasicText(
                                    text = monthlyAudience,
                                    style = typography().xs.medium,
                                    maxLines = 1
                                )
                            }
                        }

                        item( "action_buttons") {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                followButton.ToolBarButton()

                                Spacer( Modifier.width( 5.dp ) )

                                TabToolBar.Buttons(
                                    shuffler,
                                    playNext,
                                    enqueue,
                                    radio,
                                    downloadAllDialog,
                                    deleteAllDownloadsDialog,
                                    modifier = Modifier.fillMaxWidth( .8f )
                                )
                            }
                        }

                        if( artistPage == null && viewModel.isRefreshing ) {
                            items( 5 ) { SongItem.Placeholder() }

                            items( 2 ) {
                                ItemUtils.PlaceholderRowItem {
                                    AlbumItem.VerticalPlaceholder()
                                }
                            }
                        } else if( artistPage != null && currentTabIndex == 0 )
                            renderSections(
                                navController = navController,
                                context = context,
                                binder = binder,
                                hapticFeedback = hapticFeedback,
                                currentMedia = currentMedia,
                                songItemValues = songItemValues,
                                artistPage = artistPage!!,
                                sectionTextModifier = viewModel.sectionTextModifier
                            )
                        else if( currentTabIndex == 1 )
                            renderLibrarySongs(
                                navController = navController,
                                context = context,
                                binder = binder,
                                hapticFeedback = hapticFeedback,
                                currentMedia = currentMedia,
                                songItemValues = songItemValues,
                                sectionTextModifier = viewModel.sectionTextModifier,
                                songs = artistLibrarySongs
                            )

                        artistPage?.description?.also( this::renderDescription )
                    }
                }
            }
        }
    }

    // Run once on start
    LaunchedEffect( Unit ) { viewModel.onRefresh() }
}