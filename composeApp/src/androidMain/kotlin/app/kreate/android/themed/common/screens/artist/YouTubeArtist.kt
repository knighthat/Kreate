package app.kreate.android.themed.common.screens.artist

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastFlatMap
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMapNotNull
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.utils.innertube.toAlbum
import app.kreate.android.utils.innertube.toMediaItem
import app.kreate.android.utils.innertube.toOldInnertubeArtist
import app.kreate.android.utils.innertube.toOldInnertubePlaylist
import app.kreate.android.utils.innertube.toSong
import app.kreate.android.utils.renderDescription
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.models.Artist
import it.fast4x.rimusic.models.Song
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.Skeleton
import it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import it.fast4x.rimusic.ui.components.themed.AutoResizeText
import it.fast4x.rimusic.ui.components.themed.Enqueue
import it.fast4x.rimusic.ui.components.themed.FontSizeRange
import it.fast4x.rimusic.ui.components.themed.PlayNext
import it.fast4x.rimusic.ui.items.AlbumItem
import it.fast4x.rimusic.ui.items.AlbumPlaceholder
import it.fast4x.rimusic.ui.items.ArtistItem
import it.fast4x.rimusic.ui.items.PlaylistItem
import it.fast4x.rimusic.ui.items.SongItemPlaceholder
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.px
import it.fast4x.rimusic.utils.addNext
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.conditional
import it.fast4x.rimusic.utils.enqueue
import it.fast4x.rimusic.utils.fadingEdge
import it.fast4x.rimusic.utils.forcePlayAtIndex
import it.fast4x.rimusic.utils.isLandscape
import it.fast4x.rimusic.utils.medium
import it.fast4x.rimusic.utils.semiBold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.knighthat.coil.ImageCacheFactory
import me.knighthat.component.SongItem
import me.knighthat.component.artist.FollowButton
import me.knighthat.component.tab.DeleteAllDownloadedSongsDialog
import me.knighthat.component.tab.DownloadAllSongsDialog
import me.knighthat.component.tab.Radio
import me.knighthat.component.tab.SongShuffler
import me.knighthat.component.ui.screens.DynamicOrientationLayout
import me.knighthat.innertube.Constants
import me.knighthat.innertube.Innertube
import me.knighthat.innertube.model.InnertubeAlbum
import me.knighthat.innertube.model.InnertubeArtist
import me.knighthat.innertube.model.InnertubeItem
import me.knighthat.innertube.model.InnertubePlaylist
import me.knighthat.innertube.model.InnertubeSong
import me.knighthat.innertube.request.Localization
import me.knighthat.utils.PropUtils
import me.knighthat.utils.Toaster

private fun updateArtistInDatabase( innertubeArtist: InnertubeArtist ) {
    CoroutineScope( Dispatchers.IO ).launch {
        val dbArtist = Database.artistTable.findById( innertubeArtist.id ).first()
        val onlineArtist = Artist(
            id = innertubeArtist.id,
            name = PropUtils.retainIfModified( dbArtist?.name, innertubeArtist.name ),
            thumbnailUrl = PropUtils.retainIfModified( dbArtist?.thumbnailUrl, innertubeArtist.thumbnails.firstOrNull()?.url ),
            timestamp = dbArtist?.timestamp ?: System.currentTimeMillis(),
            bookmarkedAt = dbArtist?.bookmarkedAt,
            isYoutubeArtist = true
        )

        Database.asyncTransaction {
            artistTable.upsert( onlineArtist )
        }
    }
}

private fun LazyListScope.renderSection(
    navController: NavController,
    sections: List<InnertubeArtist.Section>,
    sectionTextModifier: Modifier,
    itemContent: LazyListScope.(String, String?, List<InnertubeItem>) -> Unit
) = sections.forEach { section ->
    stickyHeader( "${section.title}Header" ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = sectionTextModifier.fillMaxWidth()
        ) {
            Text(
                text = section.title,
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

    itemContent( section.title, section.params, section.contents )
}

private fun LazyListScope.renderSection(
    navController: NavController,
    title: String,
    params: String?,
    items: List<InnertubeItem>,
    albumThumbnailSizePx: Int,
    albumThumbnailSizeDp: Dp,
    disableScrollingText: Boolean
) = item( title ) {
    LazyRow {
        this@LazyRow.items(
            items = items,
            key = InnertubeItem::id
        ) { item ->
            when (item) {
                is InnertubeAlbum ->
                    AlbumItem(
                        album = item.toAlbum,
                        alternative = true,
                        thumbnailSizePx = albumThumbnailSizePx,
                        thumbnailSizeDp = albumThumbnailSizeDp,
                        disableScrollingText = disableScrollingText,
                        isYoutubeAlbum = true,
                        modifier = Modifier.clickable {
                            NavRoutes.YT_ALBUM.navigateHere(
                                navController,
                                "${item.id}?params=$params"
                            )
                        }
                    )

                is InnertubePlaylist ->
                    PlaylistItem(
                        playlist = item.toOldInnertubePlaylist,
                        alternative = true,
                        thumbnailSizePx = albumThumbnailSizePx,
                        thumbnailSizeDp = albumThumbnailSizeDp,
                        disableScrollingText = disableScrollingText,
                        modifier = Modifier.clickable {
                            NavRoutes.YT_PLAYLIST.navigateHere(navController, item.id)
                        }
                    )

                is InnertubeArtist ->
                    ArtistItem(
                        artist = item.toOldInnertubeArtist,
                        alternative = true,
                        thumbnailSizePx = albumThumbnailSizePx,
                        thumbnailSizeDp = albumThumbnailSizeDp,
                        disableScrollingText = disableScrollingText,
                        modifier = Modifier.clickable {
                            NavRoutes.YT_ARTIST.navigateHere( navController, item.id )
                        }
                    )
            }
        }
    }
}

@ExperimentalMaterial3Api
@ExperimentalFoundationApi
@UnstableApi
@Composable
fun YouTubeArtist(
    navController: NavController,
    browseId: String,
    params: String?,
    miniPlayer: @Composable () -> Unit = {}
) {
    Skeleton(
        navController = navController,
        miniPlayer = miniPlayer,
        navBarContent = { item ->
            item(0, stringResource(R.string.songs), R.drawable.musical_notes)
        }
    ) {
        //<editor-fold desc="Essentials">
        val context = LocalContext.current
        val binder = LocalPlayerServiceBinder.current ?: return@Skeleton
        val lazyListState = rememberLazyListState()
        //</editor-fold>
        val disableScrollingText by Preferences.SCROLLING_TEXT_DISABLED

        var artistPage: InnertubeArtist? by remember { mutableStateOf( null ) }
        var isRefreshing by remember { mutableStateOf( false ) }
        val sectionTextModifier = remember {
            Modifier.padding( 16.dp, 24.dp, 16.dp, 8.dp )
        }
        val albumThumbnailSizeDp = 108.dp
        val albumThumbnailSizePx = albumThumbnailSizeDp.px

        //<editor-fold defaultstate="collapsed" desc="Buttons">
        fun getSongs(): List<Song> = artistPage?.sections
                                               ?.fastFlatMap { it.contents }
                                               ?.fastMapNotNull { it as? InnertubeSong }
                                               ?.fastMap( InnertubeSong::toSong )
                                               .orEmpty()
        fun getMediaItems() = getSongs().map( Song::asMediaItem )

        val dbArtist by remember {
            Database.artistTable.findById( browseId )
        }.collectAsState( null, Dispatchers.IO )
        val followButton = FollowButton { dbArtist }
        val shuffler = SongShuffler( ::getSongs )
        val downloadAllDialog = DownloadAllSongsDialog( ::getSongs )
        val deleteAllDownloadsDialog = DeleteAllDownloadedSongsDialog( ::getSongs )
        val radio = Radio(::getSongs)
        val playNext = PlayNext {
            getMediaItems().let {
                binder.player.addNext( it, appContext() )
            }
        }
        val enqueue = Enqueue {
            getMediaItems().let {
                binder.player.enqueue( it, appContext() )
            }
        }

        downloadAllDialog.Render()
        deleteAllDownloadsDialog.Render()
        //</editor-fold>

        fun onRefresh() = CoroutineScope( Dispatchers.IO ).launch {
            Innertube.browseArtist( browseId, Localization.EN_US, params )
                     .onSuccess {
                         artistPage = it
                         it.also( ::updateArtistInDatabase )
                     }
                     .onFailure {
                         it.printStackTrace()
                         it.message?.also( Toaster::e )
                     }

            isRefreshing = false
        }
        LaunchedEffect( Unit ) { onRefresh() }

        val thumbnailPainter = ImageCacheFactory.Painter( artistPage?.thumbnails?.firstOrNull()?.url )
        DynamicOrientationLayout( thumbnailPainter ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    onRefresh()
                },
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    state = lazyListState,
                    userScrollEnabled = artistPage != null,
                    contentPadding = PaddingValues(bottom = Dimensions.bottomSpacer),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxSize()
                ) {
                    item( "header" ) {
                        Box( Modifier.fillMaxWidth() ) {
                            if (!isLandscape)
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

                            Icon(
                                painter = painterResource( R.drawable.share_social ),
                                // TODO: Make a separate string for this (i.e. Share to...)
                                contentDescription = stringResource( R.string.listen_on_youtube_music ),
                                tint = colorPalette().text.copy( .5f ),
                                modifier = Modifier.padding( all = 5.dp )
                                                   .size( 40.dp )
                                                   .align( Alignment.TopEnd )
                                                   .clickable {
                                                       artistPage?.shareUrl( Constants.YOUTUBE_MUSIC_URL )?.also { url ->
                                                           val sendIntent = Intent().apply {
                                                               action = Intent.ACTION_SEND
                                                               type = "text/plain"
                                                               putExtra(Intent.EXTRA_TEXT, url)
                                                           }

                                                           context.startActivity(
                                                               Intent.createChooser( sendIntent, null )
                                                           )
                                                       }
                                                   }
                            )

                            AutoResizeText(
                                text = artistPage?.name.orEmpty(),
                                style = typography().l.semiBold,
                                fontSizeRange = FontSizeRange( 32.sp, 38.sp ),
                                fontWeight = typography().l.semiBold.fontWeight,
                                fontFamily = typography().l.semiBold.fontFamily,
                                color = typography().l.semiBold.color,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.align( Alignment.BottomCenter )
                                                   .padding( horizontal = 30.dp )
                                                   .conditional( !Preferences.SCROLLING_TEXT_DISABLED.value ) {
                                                       basicMarquee( iterations = Int.MAX_VALUE )
                                                   }
                            )
                        }
                    }

                    item( "monthlyListeners" ) {
                        BasicText(
                            text = artistPage?.shortNumMonthlyAudience.orEmpty(),
                            style = typography().xs.medium,
                            maxLines = 1
                        )
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

                    if( artistPage == null ) {
                        items( 5 ) { SongItemPlaceholder() }

                        items( 2 ) {
                            LazyRow {
                                this@LazyRow.items( 10 ) { AlbumPlaceholder() }
                            }
                        }
                    }

                    renderSection( navController, artistPage?.sections.orEmpty(), sectionTextModifier ) { title, params, items ->
                         /*
                             Use mapNotNull with `as?` to avoid unnecessary checking (double traversal)
                             Double traversal example:

                             if( items.fastAll { it is InnertubeSong } )
                                items.fastMap { it as InnertubeSong }
                          */
                        items.fastMapNotNull { it as? InnertubeSong }
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
                                         SongItem(
                                             song = song.toSong,
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

                        if( !items.fastAll { it is InnertubeSong } )
                            renderSection(
                                navController,
                                title,
                                params,
                                items,
                                albumThumbnailSizePx,
                                albumThumbnailSizeDp,
                                disableScrollingText
                            )
                    }

                    artistPage?.description?.also( this::renderDescription )
                }
            }
        }
    }
}