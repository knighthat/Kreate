package me.knighthat.ui.screens.artist

import android.content.Intent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.R
import coil.compose.AsyncImagePainter
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.cleanPrefix
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.models.Album
import it.fast4x.rimusic.models.Artist
import it.fast4x.rimusic.models.Song
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import it.fast4x.rimusic.ui.components.navigation.header.TabToolBar
import it.fast4x.rimusic.ui.components.themed.AutoResizeText
import it.fast4x.rimusic.ui.components.themed.Enqueue
import it.fast4x.rimusic.ui.components.themed.FontSizeRange
import it.fast4x.rimusic.ui.components.themed.HeaderIconButton
import it.fast4x.rimusic.ui.components.themed.PlayNext
import it.fast4x.rimusic.ui.items.AlbumItem
import it.fast4x.rimusic.ui.items.SongItemPlaceholder
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.px
import it.fast4x.rimusic.utils.addNext
import it.fast4x.rimusic.utils.align
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.color
import it.fast4x.rimusic.utils.conditional
import it.fast4x.rimusic.utils.disableScrollingTextKey
import it.fast4x.rimusic.utils.enqueue
import it.fast4x.rimusic.utils.fadingEdge
import it.fast4x.rimusic.utils.forcePlayAtIndex
import it.fast4x.rimusic.utils.getHttpClient
import it.fast4x.rimusic.utils.isLandscape
import it.fast4x.rimusic.utils.languageDestination
import it.fast4x.rimusic.utils.rememberPreference
import it.fast4x.rimusic.utils.secondary
import it.fast4x.rimusic.utils.semiBold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import me.bush.translator.Language
import me.bush.translator.Translator
import me.knighthat.component.SongItem
import me.knighthat.component.artist.FollowButton
import me.knighthat.component.tab.DeleteAllDownloadedSongsDialog
import me.knighthat.component.tab.DownloadAllSongsDialog
import me.knighthat.component.tab.ItemSelector
import me.knighthat.component.tab.Radio
import me.knighthat.component.tab.SongShuffler
import me.knighthat.component.ui.screens.DynamicOrientationLayout
import me.knighthat.component.ui.screens.album.Translate

@ExperimentalFoundationApi
@UnstableApi
@Composable
fun ArtistDetails(
    navController: NavController,
    browseId: String,
    artist: Artist?,
    thumbnailPainter: AsyncImagePainter,
    description: String,
    albums: List<Album>,
    singles: List<Album>
) {
    artist ?: return

    // Essentials
    val context = LocalContext.current
    val binder = LocalPlayerServiceBinder.current
    val lazyListState = rememberLazyListState()

    // Settings
    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    val sectionTextModifier = Modifier
        .padding(horizontal = 16.dp)
        .padding(top = 24.dp, bottom = 8.dp)
    val albumThumbnailSizeDp = 108.dp
    val albumThumbnailSizePx = albumThumbnailSizeDp.px

    var showMoreSongs by rememberSaveable { mutableStateOf( false ) }
    val songPreviews by remember( showMoreSongs ) {
        val filterLimit = if( showMoreSongs ) Int.MAX_VALUE else 5
        Database.songArtistMapTable
                .allSongsBy( browseId, filterLimit )
                .distinctUntilChanged()
    }.collectAsState( emptyList(), Dispatchers.IO )

    val itemSelector = ItemSelector<Song>()

    fun getSongs() = itemSelector.ifEmpty { songPreviews }
    fun getMediaItems() = getSongs().map( Song::asMediaItem )

    val followButton = FollowButton { artist }
    val shuffler = SongShuffler( ::getSongs )
    val downloadAllDialog = DownloadAllSongsDialog( ::getSongs )
    val deleteAllDownloadsDialog = DeleteAllDownloadedSongsDialog( ::getSongs )
    val radio = Radio( ::getSongs )
    val playNext = PlayNext {
        getMediaItems().let {
            binder?.player?.addNext( it, appContext() )

            // Turn of selector clears the selected list
            itemSelector.isActive = false
        }
    }
    val enqueue = Enqueue {
        getMediaItems().let {
            binder?.player?.enqueue( it, appContext() )

            // Turn of selector clears the selected list
            itemSelector.isActive = false
        }
    }

    //<editor-fold defaultstate="collapsed" desc="Translator">
    val translate = Translate.init()
    val translator = Translator(getHttpClient())
    val languageDestination = languageDestination()
    //</editor-fold>

    //<editor-fold desc="Dialog renders">
    downloadAllDialog.Render()
    deleteAllDownloadsDialog.Render()
    //</editor-fold>

    DynamicOrientationLayout( thumbnailPainter ) {
        LazyColumn(
            state = lazyListState,
            userScrollEnabled = songPreviews.isNotEmpty(),
            contentPadding = PaddingValues( bottom = Dimensions.bottomSpacer ),
        ) {
            item( "header" ) {
                Box( Modifier.fillMaxWidth() ) {
                    if ( !isLandscape )
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

                    AutoResizeText(
                        text = cleanPrefix( artist?.name ?: "..." ),
                        style = typography().l.semiBold,
                        fontSizeRange = FontSizeRange(32.sp, 38.sp),
                        fontWeight = typography().l.semiBold.fontWeight,
                        fontFamily = typography().l.semiBold.fontFamily,
                        color = typography().l.semiBold.color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align( Alignment.BottomCenter )
                                           .padding( horizontal = 30.dp )
                                           .conditional( !disableScrollingText ) {
                                               basicMarquee( iterations = Int.MAX_VALUE )
                                           }
                    )

                    HeaderIconButton(
                        icon = R.drawable.share_social,
                        color = colorPalette().text,
                        iconSize = 24.dp,
                        modifier = Modifier.align( Alignment.TopEnd )
                                           .padding( top = 5.dp, end = 5.dp ),
                        onClick = {
                            artist?.id?.let {
                                val url = "https://music.youtube.com/channel/$it"
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
                        itemSelector,
                        downloadAllDialog,
                        deleteAllDownloadsDialog,
                        modifier = Modifier.fillMaxWidth( .8f )
                    )
                }
            }

            item( "songsTitle" ) {
                val rotationAngle by animateFloatAsState(
                    targetValue = if ( showMoreSongs ) 90f else 0f,
                    animationSpec = tween(durationMillis = 200), label = ""
                )

                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = sectionTextModifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource( R.string.songs ),
                        style = typography().m.semiBold,
                        modifier = Modifier.weight( 1f )
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = colorPalette().text,
                        modifier = Modifier.clickable { showMoreSongs = !showMoreSongs }
                            .rotate( rotationAngle )
                    )
                }
            }
            // Placeholders while songs are loading
            if( songPreviews.isEmpty() )
                items(
                    count = 5,
                    key = { index -> "song_placeholders_no$index" }
                ) { SongItemPlaceholder() }
            itemsIndexed(
                items = songPreviews,
                key = { _, song -> song.id }
            ) { index, song ->

                SwipeablePlaylistItem(
                    mediaItem = song.asMediaItem,
                    onPlayNext = {
                        binder?.player?.addNext(song.asMediaItem)
                    }
                ) {
                    SongItem(
                        song = song,
                        itemSelector = itemSelector,
                        navController = navController,
                        showThumbnail = true,
                        onClick = {
                            binder?.stopRadio()
                            binder?.player?.forcePlayAtIndex(
                                songPreviews.map( Song::asMediaItem ),
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

            if( albums.isNotEmpty() )
                item( "albums" ) {
                    BasicText(
                        text = stringResource( R.string.albums ),
                        style = typography().m.semiBold.align( TextAlign.Start ),
                        modifier = sectionTextModifier
                    )

                    LazyRow {
                        items(
                            items = albums,
                            key = Album::id
                        ) { album ->
                            AlbumItem(
                                album = album,
                                alternative = true,
                                thumbnailSizePx = albumThumbnailSizePx,
                                thumbnailSizeDp = albumThumbnailSizeDp,
                                disableScrollingText = disableScrollingText,
                                isYoutubeAlbum = album.isYoutubeAlbum,
                                modifier = Modifier.clickable {
                                    navController.navigate("${NavRoutes.album.name}/${album.id}")
                                }
                            )
                        }
                    }
                }

            if( singles.isNotEmpty() )
                item( "singles" ) {
                    BasicText(
                        text = stringResource( R.string.singles ),
                        style = typography().m.semiBold.align( TextAlign.Start ),
                        modifier = sectionTextModifier
                    )

                    LazyRow {
                        items(
                            items = singles,
                            key = Album::id
                        ) { album ->
                            AlbumItem(
                                album = album,
                                alternative = true,
                                thumbnailSizePx = albumThumbnailSizePx,
                                thumbnailSizeDp = albumThumbnailSizeDp,
                                disableScrollingText = disableScrollingText,
                                isYoutubeAlbum = album.isYoutubeAlbum,
                                modifier = Modifier.clickable {
                                    navController.navigate("${NavRoutes.album.name}/${album.id}")
                                }
                            )
                        }
                    }
                }

            if( description.isNotBlank() )
                item( "description" ) {
                    // For some reason adding 2 "\n" makes double quotes appear
                    // on the same level as the last line of text
                    val attributionsIndex = description.lastIndexOf("\n\nFrom Wikipedia")

                    BasicText(
                        text = stringResource(R.string.information),
                        style = typography().m.semiBold.align(TextAlign.Start),
                        modifier = sectionTextModifier
                    )

                    Row(
                        modifier = Modifier.padding(
                            vertical = 16.dp,
                            horizontal = 8.dp
                        )
                    ) {
                        translate.ToolBarButton()

                        BasicText(
                            text = "“",
                            style = typography().xxl.semiBold,
                            modifier = Modifier.offset( y = (-8).dp )
                                               .align( Alignment.Top )
                        )

                        var translatedText by remember { mutableStateOf("") }
                        val nonTranslatedText by remember {
                            mutableStateOf(
                                if ( attributionsIndex == -1 ) {
                                    description
                                } else {
                                    description.substring( 0, attributionsIndex )
                                }
                            )
                        }

                        if ( translate.isActive ) {
                            LaunchedEffect(Unit) {
                                val result = withContext( Dispatchers.IO ) {
                                    try {
                                        translator.translate(
                                            nonTranslatedText,
                                            languageDestination,
                                            Language.AUTO
                                        ).translatedText
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                translatedText =
                                    if (result.toString() == "kotlin.Unit") "" else result.toString()
                            }
                        } else translatedText = nonTranslatedText

                        BasicText(
                            text = translatedText,
                            style = typography().xxs.secondary.align(TextAlign.Justify),
                            modifier = Modifier.padding( horizontal = 8.dp )
                                               .weight( 1f )
                        )

                        BasicText(
                            text = "„",
                            style = typography().xxl.semiBold,
                            modifier = Modifier.offset( y = 4.dp )
                                               .align( Alignment.Bottom )
                        )
                    }

                    if (attributionsIndex != -1) {
                        BasicText(
                            text = stringResource(R.string.from_wikipedia_cca),
                            style = typography().xxs
                                                .color( colorPalette().textDisabled )
                                                .align( TextAlign.Start ),
                            modifier = Modifier.padding( horizontal = 16.dp )
                        )
                    }
                }
        }
    }
}