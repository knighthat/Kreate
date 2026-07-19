package app.kreate.android.themed.common.screens.album

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastJoinToString
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMapNotNull
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.LocalBottomMenu
import app.kreate.android.coil3.ImageFactory
import app.kreate.android.constant.MenuPage
import app.kreate.android.themed.common.component.tab.DeleteAllDownloadedDialog
import app.kreate.android.themed.common.component.tab.DownloadAllDialog
import app.kreate.android.themed.rimusic.component.ItemSelector
import app.kreate.android.themed.rimusic.component.album.AlbumItem
import app.kreate.android.themed.rimusic.component.album.Bookmark
import app.kreate.android.themed.rimusic.component.song.SongItem
import app.kreate.android.utils.renderDescription
import app.kreate.android.utils.shallowCompare
import app.kreate.android.viewmodel.YoutubeAlbumViewModel
import app.kreate.compose.R
import app.kreate.database.models.Song
import app.kreate.gateway.innertube.models.InnertubeAlbum
import app.kreate.gateway.innertube.models.InnertubeSong
import app.kreate.gateway.innertube.models.Section
import app.kreate.internal.innertube.models.share
import app.kreate.player.Player
import app.kreate.util.MODIFIED_PREFIX
import app.kreate.util.scrollingText
import co.touchlab.kermit.Logger
import it.fast4x.rimusic.colorPalette
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
import it.fast4x.rimusic.utils.addNext
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.asSong
import it.fast4x.rimusic.utils.center
import it.fast4x.rimusic.utils.color
import it.fast4x.rimusic.utils.enqueue
import it.fast4x.rimusic.utils.fadingEdge
import it.fast4x.rimusic.utils.isLandscape
import it.fast4x.rimusic.utils.medium
import it.fast4x.rimusic.utils.semiBold
import me.knighthat.component.album.AlbumModifier
import me.knighthat.component.tab.Locator
import me.knighthat.component.tab.Radio
import me.knighthat.component.tab.SongShuffler
import me.knighthat.component.ui.screens.DynamicOrientationLayout
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel


@ExperimentalFoundationApi
@UnstableApi
private fun LazyListScope.renderSection(
    navController: NavController,
    section: Section,
    sectionTextModifier: Modifier
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
                    AlbumItem.Vertical( item, albumItemValues, navController )
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
    miniPlayer: @Composable () -> Unit = {},
    viewModel: YoutubeAlbumViewModel = koinViewModel()
) {
    Skeleton(
        navController = navController,
        miniPlayer = miniPlayer,
        navBarContent = { item ->
            item(0, stringResource(R.string.songs), R.drawable.musical_notes)
        }
    ) {
        //<editor-fold desc="Essentials">
        val player: Player = koinInject()
        val hapticFeedback = LocalHapticFeedback.current
        val (colorPalette, typography) = LocalAppearance.current
        val context = LocalContext.current
        val menuState = LocalMenuState.current
        val lazyListState = rememberLazyListState()
        val menu = LocalBottomMenu.current
        val coroutineScope = rememberCoroutineScope()
        //</editor-fold>

        val albumPage by viewModel.albumPage.collectAsStateWithLifecycle()
        val dbAlbum by viewModel.dbAlbum.collectAsStateWithLifecycle()
        val items by viewModel.librarySongs().collectAsStateWithLifecycle()
        val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
        val sectionTextModifier = remember {
            Modifier.padding( 16.dp, 24.dp, 16.dp, 8.dp )
        }

        //<editor-fold desc="Buttons">
        val itemSelector = remember {
            ItemSelector( menuState ) { addAll( items ) }
        }

        fun getSongs() = itemSelector.ifEmpty { items }
        fun getMediaItems() = getSongs().map( Song::asMediaItem )

        val bookmark = remember { Bookmark(viewModel.browseId) }
        val deleteAllDownloadsDialog = remember {
            DeleteAllDownloadedDialog(::getSongs)
        }
        val downloadALlDialog = remember {
            DownloadAllDialog( context, ::getSongs )
        }
        val shuffle = SongShuffler {
            getMediaItems().map( MediaItem::asSong )
        }
        val radio = Radio( ::getSongs )
        val locator = Locator( lazyListState, ::getSongs )
        val playNext = PlayNext {
            getMediaItems().let {
                player.addNext( it, context )

                // Turn of selector clears the selected list
                itemSelector.isActive = false
            }
        }
        val enqueue = Enqueue {
            getMediaItems().let {
                player.enqueue( it, context )

                // Turn of selector clears the selected list
                itemSelector.isActive = false
            }
        }
        val addToPlaylist = PlaylistsMenu.init(
            coroutineScope,
            navController,
            { getMediaItems() },
            { throwable, preview ->
                Logger.e( throwable, "YouTubeAlbum" ) {
                    "Failed to add songs to playlist ${preview.playlist.name} on HomeSongs"
                }
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
            updateTitle( viewModel.browseId, "$MODIFIED_PREFIX$it" )
        }
        val changeAuthors = AlbumModifier(
            iconId = R.drawable.artists_edit,
            messageId = R.string.update_authors,
            getDefaultValue = { dbAlbum?.cleanAuthorsText() ?: "" },
        ) {
            updateAuthors( viewModel.browseId, "$MODIFIED_PREFIX$it" )
        }
        val changeCover = AlbumModifier(
            iconId = R.drawable.cover_edit,
            messageId = R.string.update_cover,
            getDefaultValue = { dbAlbum?.cleanThumbnailUrl() ?: "" },
        ) {
            updateCover( viewModel.browseId, "$MODIFIED_PREFIX$it" )
        }
        //</editor-fold>

        downloadALlDialog.Render()
        deleteAllDownloadsDialog.Render()
        changeTitle.Render()
        changeAuthors.Render()
        changeCover.Render()
        //</editor-fold>

        LaunchedEffect( Unit ) { viewModel.onRefresh() }

        val currentMediaItem by player.currentMediaItemState.collectAsState()
        val songItemValues = remember( colorPalette, typography ) {
            SongItem.Values.from( colorPalette, typography )
        }

        val thumbnailPainter = ImageFactory.rememberAsyncImagePainter( dbAlbum?.cleanThumbnailUrl() )
        DynamicOrientationLayout( thumbnailPainter ) {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = viewModel::onRefresh,
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
                                                       albumPage?.share( context )
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
                            val yearText = if( albumPage?.year == null ) "" else " • ${albumPage?.year}"

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
                            text = albumPage?.subtitle?.runs?.fastJoinToString( "" ) { it.text }.orEmpty(),
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
                            val mediaItem = song.asMediaItem

                            SwipeablePlaylistItem(
                                mediaItem = mediaItem,
                                onPlayNext = {
                                    player.addNext( mediaItem )
                                }
                            ) {
                                SongItem.Render(
                                    song = song,
                                    hapticFeedback = hapticFeedback,
                                    isPlaying = song.shallowCompare( currentMediaItem ),
                                    values = songItemValues,
                                    itemSelector = itemSelector,
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
                                            modifier = Modifier.width( SongItem.thumbnailSize().width )
                                                               .align( Alignment.Center )
                                        )
                                    },
                                    onClick = {
                                        val selectedSongs = getSongs()
                                        if( song in selectedSongs )
                                            player.play(
                                                selectedSongs.fastMap( Song::asMediaItem ),
                                                selectedSongs.indexOf( song )
                                            )
                                        else
                                            player.play(
                                                items.fastMap( Song::asMediaItem ),
                                                index
                                            )

                                        /*
                                            Due to the small size of checkboxes,
                                            we shouldn't disable [itemSelector]
                                         */
                                    },
                                    onLongClick = {
                                        val page = MenuPage.Song(mediaItem)
                                        menu.show( page, true )
                                    }
                                )
                            }
                        }

                    albumPage?.sections?.fastForEach {
                        renderSection( navController, it, sectionTextModifier )
                    }

                    albumPage?.description?.also( this::renderDescription )
                }
            }
        }
    }
}