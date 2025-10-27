package app.kreate.android.themed.common.screens.album

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastJoinToString
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMapNotNull
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.R
import app.kreate.android.coil3.ImageFactory
import app.kreate.android.themed.common.component.tab.DeleteAllDownloadedDialog
import app.kreate.android.themed.common.component.tab.DownloadAllDialog
import app.kreate.android.themed.rimusic.component.ItemSelector
import app.kreate.android.themed.rimusic.component.album.AlbumItem
import app.kreate.android.themed.rimusic.component.album.Bookmark
import app.kreate.android.themed.rimusic.component.song.SongItem
import app.kreate.android.utils.innertube.CURRENT_LOCALE
import app.kreate.android.utils.innertube.toMediaItem
import app.kreate.android.utils.renderDescription
import app.kreate.android.utils.scrollingText
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.MODIFIED_PREFIX
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.models.Album
import it.fast4x.rimusic.models.Song
import it.fast4x.rimusic.models.SongAlbumMap
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.Skeleton
import it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import it.fast4x.rimusic.ui.components.themed.AutoResizeText
import it.fast4x.rimusic.ui.components.themed.Enqueue
import it.fast4x.rimusic.ui.components.themed.FontSizeRange
import it.fast4x.rimusic.ui.components.themed.PlayNext
import it.fast4x.rimusic.ui.components.themed.PlaylistsMenu
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.ui.styling.px
import it.fast4x.rimusic.utils.DisposableListener
import it.fast4x.rimusic.utils.addNext
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.asSong
import it.fast4x.rimusic.utils.center
import it.fast4x.rimusic.utils.color
import it.fast4x.rimusic.utils.enqueue
import it.fast4x.rimusic.utils.fadingEdge
import it.fast4x.rimusic.utils.forcePlayAtIndex
import it.fast4x.rimusic.utils.isLandscape
import it.fast4x.rimusic.utils.medium
import it.fast4x.rimusic.utils.semiBold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.knighthat.component.album.AlbumModifier
import me.knighthat.component.tab.Locator
import me.knighthat.component.tab.Radio
import me.knighthat.component.tab.SongShuffler
import me.knighthat.component.ui.screens.DynamicOrientationLayout
import me.knighthat.innertube.Constants
import me.knighthat.innertube.Innertube
import me.knighthat.innertube.model.InnertubeAlbum
import me.knighthat.innertube.model.InnertubeSong
import me.knighthat.utils.PropUtils
import me.knighthat.utils.Toaster
import timber.log.Timber

private fun updateAlbumInDatabase( dbAlbum: Album?, innertubeAlbum: InnertubeAlbum ) = Database.asyncTransaction {
    val onlineAlbum = Album(
        id = innertubeAlbum.id,
        title = PropUtils.retainIfModified( dbAlbum?.title, innertubeAlbum.name ),
        thumbnailUrl = PropUtils.retainIfModified(
            dbAlbum?.thumbnailUrl,
            innertubeAlbum.thumbnails.firstOrNull()?.url
        ),
        year = innertubeAlbum.year,
        authorsText = PropUtils.retainIfModified(
            dbAlbum?.authorsText,
            innertubeAlbum.artists.fastJoinToString { it.text }
        ),
        shareUrl = dbAlbum?.shareUrl,
        timestamp = dbAlbum?.timestamp ?: System.currentTimeMillis(),
        bookmarkedAt = dbAlbum?.bookmarkedAt,
        isYoutubeAlbum = true
    )

    // Upsert to override/update default values
    albumTable.upsert( onlineAlbum )

    // Map ignore to make sure only positions
    // are overridden, not the songs themselves
    innertubeAlbum.songs
                 .fastMap( InnertubeSong::toMediaItem )
                 .onEach( ::insertIgnore )
                 .mapIndexed { position, mediaItem ->
                     SongAlbumMap(
                         songId = mediaItem.mediaId,
                         albumId = innertubeAlbum.id,
                         position = position
                     )
                 }
                 .also( songAlbumMapTable::upsert )
}

@ExperimentalFoundationApi
@UnstableApi
private fun LazyListScope.renderSection(
    navController: NavController,
    section: InnertubeAlbum.Section,
    sectionTextModifier: Modifier,
    albumThumbnailSizePx: Int,
    albumThumbnailSizeDp: Dp
) {
    stickyHeader( System.identityHashCode( section ) ) {
        Text(
            text = if( !section.title.isNullOrBlank() )
                section.title!!
            else if( section.contents.fastAll { it is InnertubeSong } )
                stringResource( R.string.songs )
            else
                "",
            style = typography().m.semiBold,
            modifier = sectionTextModifier.fillMaxWidth()
        )
    }

    section.contents.fastMapNotNull { it as? InnertubeAlbum }.also {
        item( section.title ) {
            val appearance = LocalAppearance.current
            val albumItemValues = remember( appearance ) {
                AlbumItem.Values.from( appearance )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(AlbumItem.COLUMN_SPACING.dp )
            ) {
                this@LazyRow.items(
                    items = it,
                    key = InnertubeAlbum::id
                ) { item ->
                    AlbumItem.Vertical( item, albumThumbnailSizeDp, albumItemValues, navController )
                }
            }
        }
    }
}

@ExperimentalFoundationApi
@ExperimentalMaterial3Api
@UnstableApi
@Composable
fun YouTubeAlbum(
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
        val binder = LocalPlayerServiceBinder.current ?: return@Skeleton
        val hapticFeedback = LocalHapticFeedback.current
        val (colorPalette, typography) = LocalAppearance.current
        val context = LocalContext.current
        val menuState = LocalMenuState.current
        val lazyListState = rememberLazyListState()
        //</editor-fold>

        var albumPage: InnertubeAlbum? by remember { mutableStateOf( null ) }
        val dbAlbum: Album? by remember {
            Database.albumTable
                    .findById( browseId )
        }.collectAsState( null, Dispatchers.IO )
        val items by remember {
            Database.songAlbumMapTable
                    .allSongsOf( browseId )
        }.collectAsState( emptyList(), Dispatchers.IO )
        var isRefreshing by remember { mutableStateOf( false ) }
        val sectionTextModifier = remember {
            Modifier.padding( 16.dp, 24.dp, 16.dp, 8.dp )
        }
        val albumThumbnailSizeDp = 108.dp
        val albumThumbnailSizePx = albumThumbnailSizeDp.px
        val thumbnailSizeDp = Dimensions.thumbnails.song

        //<editor-fold desc="Buttons">
        val itemSelector = remember {
            ItemSelector( menuState ) { addAll( items ) }
        }

        fun getSongs() = itemSelector.ifEmpty { items }
        fun getMediaItems() = getSongs().map( Song::asMediaItem )

        val bookmark = remember { Bookmark(browseId) }
        val deleteAllDownloadsDialog = remember {
            DeleteAllDownloadedDialog( binder, context, ::getSongs )
        }
        val downloadALlDialog = remember {
            DownloadAllDialog( binder, context, ::getSongs )
        }
        val shuffle = SongShuffler {
            getMediaItems().map( MediaItem::asSong )
        }
        val radio = Radio( ::getSongs )
        val locator = Locator( lazyListState, ::getSongs )
        val playNext = PlayNext {
            getMediaItems().let {
                binder.player.addNext( it, appContext() )

                // Turn of selector clears the selected list
                itemSelector.isActive = false
            }
        }
        val enqueue = Enqueue {
            getMediaItems().let {
                binder.player.enqueue( it, appContext() )

                // Turn of selector clears the selected list
                itemSelector.isActive = false
            }
        }
        val addToPlaylist = PlaylistsMenu.init(
            navController,
            { getMediaItems() },
            { throwable, preview ->
                Timber.e( "Failed to add songs to playlist ${preview.playlist.name} on HomeSongs" )
                throwable.printStackTrace()
            },
            {
                // Turn of selector clears the selected list
                itemSelector.isActive = false
            }
        )
        //<editor-fold defaultstate="collapsed" desc="Album modifiers">
        val changeTitle = AlbumModifier(
            iconId = R.drawable.title_edit,
            messageId = R.string.update_title,
            getDefaultValue = { dbAlbum?.cleanTitle() ?: "" },
        ) {
            updateTitle( browseId, "$MODIFIED_PREFIX$it" )
        }
        val changeAuthors = AlbumModifier(
            iconId = R.drawable.artists_edit,
            messageId = R.string.update_authors,
            getDefaultValue = { dbAlbum?.cleanAuthorsText() ?: "" },
        ) {
            updateAuthors( browseId, "$MODIFIED_PREFIX$it" )
        }
        val changeCover = AlbumModifier(
            iconId = R.drawable.cover_edit,
            messageId = R.string.update_cover,
            getDefaultValue = { dbAlbum?.thumbnailUrl ?: "" },
        ) {
            updateCover( browseId, "$MODIFIED_PREFIX$it" )
        }
        //</editor-fold>

        downloadALlDialog.Render()
        deleteAllDownloadsDialog.Render()
        changeTitle.Render()
        changeAuthors.Render()
        changeCover.Render()
        //</editor-fold>

        fun onRefresh() = CoroutineScope( Dispatchers.IO ).launch {
            Innertube.browseAlbum( browseId, CURRENT_LOCALE, params )
                     .onSuccess {
                         albumPage = it
                         updateAlbumInDatabase( dbAlbum, it )
                     }
                     .onFailure { err ->
                         Timber.tag( "YouTubeAlbum" ).e( err )
                         Toaster.e( R.string.error_failed_to_load_album )
                     }

            isRefreshing = false
        }
        LaunchedEffect( Unit ) { onRefresh() }

        var currentlyPlaying by remember { mutableStateOf(binder.player.currentMediaItem?.mediaId) }
        binder.player.DisposableListener {
            object : Player.Listener {
                override fun onMediaItemTransition( mediaItem: MediaItem?, reason: Int ) {
                    currentlyPlaying = mediaItem?.mediaId
                }
            }
        }
        val songItemValues = remember( colorPalette, typography ) {
            SongItem.Values.from( colorPalette, typography )
        }

        val thumbnailPainter = ImageFactory.rememberAsyncImagePainter( dbAlbum?.thumbnailUrl )
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
                    userScrollEnabled = albumPage != null || dbAlbum != null,
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
                                    modifier = Modifier.aspectRatio( 4f / 3 )      // Limit height
                                                       .fillMaxWidth()
                                                       .align( Alignment.Center )
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
                                                       albumPage?.shareUrl( Constants.YOUTUBE_MUSIC_URL )?.also { url ->
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
                                text = dbAlbum?.cleanTitle().orEmpty(),
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
                                                   .scrollingText()
                            )
                        }
                    }

                    item( "artists" ) {
                        val text = remember( albumPage ) {
                            val artistsText = albumPage?.artists?.fastJoinToString( " • " ) { it.text }.orEmpty()
                            val yearText = if( albumPage?.year.isNullOrBlank() ) "" else " • ${albumPage?.year}"

                            "$artistsText%s".format(yearText)
                        }
                        BasicText(
                            text = text,
                            style = typography().xs.medium.copy( colorPalette().textSecondary ),
                            maxLines = 1
                        )
                    }

                    item( "subtitle" ) {
                        BasicText(
                            text = albumPage?.subtitle.orEmpty(),
                            style = typography().xs.medium.copy( colorPalette().textSecondary ),
                            maxLines = 1
                        )
                    }

                    item( "action_buttons" ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Place this button alone so we can space it further from other buttons
                            bookmark.ToolBarButton()

                            Spacer( Modifier.width(15.dp) )

                            TabToolBar.Buttons(
                                downloadALlDialog,
                                deleteAllDownloadsDialog,
                                shuffle,
                                radio,
                                locator,
                                itemSelector,
                                changeTitle,
                                changeAuthors,
                                changeCover,
                                playNext,
                                enqueue,
                                addToPlaylist,
                                modifier = Modifier.fillMaxWidth( .8f )
                            )
                        }
                    }

                    stickyHeader( "songs" ) {
                        Text(
                            text = stringResource( R.string.songs ),
                            style = typography().m.semiBold,
                            modifier = sectionTextModifier.fillMaxWidth()
                        )
                    }

                    if( items.isEmpty() )
                        items( 10 ) { SongItem.Placeholder() }
                    else
                        itemsIndexed(
                            items = items,
                            key = { i, s -> "${System.identityHashCode( s )} - $i"}
                        ) { index, song ->
                            SwipeablePlaylistItem(
                                mediaItem = song.asMediaItem,
                                onPlayNext = {
                                    binder.player.addNext(song.asMediaItem)
                                }
                            ) {
                                SongItem.Render(
                                    song = song,
                                    context = context,
                                    binder = binder,
                                    hapticFeedback = hapticFeedback,
                                    isPlaying = currentlyPlaying == song.id,
                                    values = songItemValues,
                                    itemSelector = itemSelector,
                                    navController = navController,
                                    showThumbnail = false,
                                    thumbnailOverlay = {
                                        BasicText(
                                            text = "${index + 1}",
                                            style = typography().s
                                                                .semiBold
                                                                .center
                                                                .color(
                                                                    colorPalette().textDisabled
                                                                ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.width( thumbnailSizeDp )
                                                               .align( Alignment.Center )
                                        )
                                    },
                                    onClick = {
                                        binder.stopRadio()

                                        val selectedSongs = getSongs()
                                        if( song in selectedSongs )
                                            binder.player.forcePlayAtIndex(
                                                selectedSongs.fastMap( Song::asMediaItem ),
                                                selectedSongs.indexOf( song )
                                            )
                                        else
                                            binder.player.forcePlayAtIndex(
                                                items.fastMap( Song::asMediaItem ),
                                                index
                                            )

                                        /*
                                            Due to the small size of checkboxes,
                                            we shouldn't disable [itemSelector]
                                         */
                                    }
                                )
                            }
                        }

                    albumPage?.sections?.fastForEach {
                        renderSection(
                            navController, it, sectionTextModifier, albumThumbnailSizePx, albumThumbnailSizeDp
                        )
                    }

                    albumPage?.description?.also( this::renderDescription )
                }
            }
        }
    }
}