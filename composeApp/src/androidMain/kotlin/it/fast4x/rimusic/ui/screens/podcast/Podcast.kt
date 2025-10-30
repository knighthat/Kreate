package it.fast4x.rimusic.ui.screens.podcast

import android.annotation.SuppressLint
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import app.kreate.android.Preferences
import app.kreate.android.R
import app.kreate.android.coil3.ImageFactory
import app.kreate.android.themed.rimusic.component.album.AlbumItem
import app.kreate.android.themed.rimusic.component.song.SongItem
import it.fast4x.compose.persist.persist
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.models.bodies.BrowseBody
import it.fast4x.innertube.requests.podcastPage
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.enums.UiType
import it.fast4x.rimusic.models.Playlist
import it.fast4x.rimusic.service.modern.isLocal
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import it.fast4x.rimusic.ui.components.themed.AutoResizeText
import it.fast4x.rimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import it.fast4x.rimusic.ui.components.themed.FontSizeRange
import it.fast4x.rimusic.ui.components.themed.HeaderIconButton
import it.fast4x.rimusic.ui.components.themed.IconButton
import it.fast4x.rimusic.ui.components.themed.InputTextDialog
import it.fast4x.rimusic.ui.components.themed.LayoutWithAdaptiveThumbnail
import it.fast4x.rimusic.ui.components.themed.NonQueuedMediaItemMenu
import it.fast4x.rimusic.ui.components.themed.PlaylistsItemMenu
import it.fast4x.rimusic.ui.components.themed.adaptiveThumbnailContent
import it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.ui.styling.favoritesIcon
import it.fast4x.rimusic.ui.styling.px
import it.fast4x.rimusic.utils.DisposableListener
import it.fast4x.rimusic.utils.addNext
import it.fast4x.rimusic.utils.addToYtPlaylist
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.durationTextToMillis
import it.fast4x.rimusic.utils.durationToMillis
import it.fast4x.rimusic.utils.enqueue
import it.fast4x.rimusic.utils.fadingEdge
import it.fast4x.rimusic.utils.forcePlayAtIndex
import it.fast4x.rimusic.utils.formatAsTime
import it.fast4x.rimusic.utils.isDownloadedSong
import it.fast4x.rimusic.utils.isLandscape
import it.fast4x.rimusic.utils.manageDownload
import it.fast4x.rimusic.utils.medium
import it.fast4x.rimusic.utils.playShuffled
import it.fast4x.rimusic.utils.secondary
import it.fast4x.rimusic.utils.semiBold
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.knighthat.utils.Toaster


@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun Podcast(
    navController: NavController,
    browseId: String,
    params: String?,
    maxDepth: Int?,
) {
    val binder = LocalPlayerServiceBinder.current ?: return
    val (colorPalette, typography) = LocalAppearance.current
    val context = LocalContext.current
    val menuState = LocalMenuState.current

    var podcastPage by persist<Innertube.Podcast?>("podcast/$browseId/listEpisodes")

    var filter: String? by rememberSaveable { mutableStateOf(null) }
    val hapticFeedback = LocalHapticFeedback.current

    LaunchedEffect(Unit, filter) {
        if (podcastPage != null) return@LaunchedEffect

        podcastPage = withContext(Dispatchers.IO) {
            Innertube.podcastPage(BrowseBody(browseId = browseId)).getOrNull()
        }

        /*
        println("mediaItem playlists podcasts call " + withContext(Dispatchers.IO) {
            Innertube.podcastPage(BrowseBody(browseId = browseId)).getOrNull()
        })
         */


    }

    var filterCharSequence: CharSequence
    filterCharSequence = filter.toString()
    /*
    //Log.d("mediaItemFilter", "<${filter}>  <${filterCharSequence}>")
    if (!filter.isNullOrBlank())
        playlistPage?.songsPage?.items =
        playlistPage?.songsPage?.items?.filter {songItem ->
                songItem.asMediaItem.mediaMetadata.title?.contains(filterCharSequence,true) ?: false
                        || songItem.asMediaItem.mediaMetadata.artist?.contains(filterCharSequence,true) ?: false
            }
    */

    var searching by rememberSaveable { mutableStateOf(false) }

    val songThumbnailSizeDp = Dimensions.thumbnails.song
    val songThumbnailSizePx = songThumbnailSizeDp.px

    var isImportingPlaylist by rememberSaveable {
        mutableStateOf(false)
    }

    var downloadState by remember {
        mutableStateOf(Download.STATE_STOPPED)
    }

    var thumbnailRoundness by Preferences.THUMBNAIL_BORDER_RADIUS

    var totalPlayTimes = 0L
    podcastPage?.listEpisode?.forEach {
        totalPlayTimes += it.durationString?.let { it1 ->
            durationTextToMillis(it1) }?.toLong() ?: 0
    }

    if (isImportingPlaylist) {
        InputTextDialog(
            onDismiss = { isImportingPlaylist = false },
            title = stringResource(R.string.enter_the_playlist_name),
            value = podcastPage?.title ?: "",
            placeholder = "https://........",
            setValue = { text ->
                Database.asyncTransaction {
                    val playlist = Playlist(name = text, browseId = browseId)

                    podcastPage?.listEpisode
                               ?.map( Innertube.Podcast.EpisodeItem::asMediaItem )
                               ?.let {
                                   mapIgnore( playlist, *it.toTypedArray() )
                               }

                    Toaster.done()
                }
            }
        )
    }

    var position by remember {
        mutableIntStateOf(0)
    }

    val thumbnailContent = adaptiveThumbnailContent(podcastPage == null, podcastPage?.thumbnail?.firstOrNull()?.url)

    val lazyListState = rememberLazyListState()

    LayoutWithAdaptiveThumbnail(thumbnailContent = thumbnailContent) {
        Box(
            modifier = Modifier
                .background(colorPalette().background0)
                //.fillMaxSize()
                .fillMaxHeight()
                .fillMaxWidth(
                    if( NavigationBarPosition.Right.isCurrent() )
                        Dimensions.contentWidthRightBar
                    else
                        1f
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

            LazyColumn(
                state = lazyListState,
                //contentPadding = LocalPlayerAwareWindowInsets.current
                //.only(WindowInsetsSides.Vertical + WindowInsetsSides.End).asPaddingValues(),
                modifier = Modifier
                    .background(colorPalette().background0)
                    .fillMaxSize()
            ) {

                item(
                    key = "header"
                ) {

                    val modifierArt = if (isLandscape) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().aspectRatio(4f / 3)

                    Box(
                        modifier = modifierArt
                    ) {
                        if (podcastPage != null) {
                            if(!isLandscape)
                                ImageFactory.AsyncImage(
                                    thumbnailUrl = podcastPage!!.thumbnail.firstOrNull()?.url,
                                    modifier = Modifier.fillMaxWidth()
                                                       .align( Alignment.Center )
                                                       .fadingEdge(
                                                           top = WindowInsets.systemBars
                                                               .asPaddingValues()
                                                               .calculateTopPadding() + Dimensions.fadeSpacingTop,
                                                           bottom = Dimensions.fadeSpacingBottom
                                                       )
                                )

                            AutoResizeText(
                                text = podcastPage?.title ?: "",
                                style = typography().l.semiBold,
                                fontSizeRange = FontSizeRange(32.sp, 38.sp),
                                fontWeight = typography().l.semiBold.fontWeight,
                                fontFamily = typography().l.semiBold.fontFamily,
                                color = typography().l.semiBold.color,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = 30.dp)
                                    .padding(bottom = 20.dp)
                            )

                            BasicText(
                                text = podcastPage!!.listEpisode.size.toString() + " "
                                        + stringResource(R.string.songs)
                                        + " - " + formatAsTime(totalPlayTimes),
                                style = typography().xs.medium,
                                maxLines = 1,
                                modifier = Modifier
                                    //.padding(top = 10.dp)
                                    .align(Alignment.BottomCenter)
                            )


                            HeaderIconButton(
                                icon = R.drawable.share_social,
                                color = colorPalette().text,
                                iconSize = 24.dp,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 5.dp, end= 5.dp),
                                onClick = {
                                    ("https://music.youtube.com/playlist?list=${browseId.removePrefix("VL")}").let { url ->
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, url)
                                        }

                                        context.startActivity(Intent.createChooser(sendIntent, null))
                                    }
                                }
                            )

                        } else {
                            Column(
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(4f / 3)
                            ) {
                                AlbumItem.VerticalPlaceholder( 200.dp )

                                BasicText(
                                    text = stringResource(R.string.info_wait_it_may_take_a_few_minutes),
                                    style = typography().xs.medium,
                                    maxLines = 1,
                                    modifier = Modifier
                                    //.padding(top = 10.dp)

                                )
                            }
                        }
                    }

                }

                item(
                    key = "actions",
                    contentType = 0
                ) {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth()
                    ) {

                        //if (!isLandscape) thumbnailContent()

                        if (podcastPage != null) {

                            //actionsContent()

                            HeaderIconButton(
                                onClick = { searching = !searching },
                                icon = R.drawable.search_circle,
                                color = colorPalette().text,
                                iconSize = 24.dp,
                                modifier = Modifier
                                    .padding(horizontal = 5.dp)
                            )

                            HeaderIconButton(
                                icon = R.drawable.downloaded,
                                color = colorPalette().text,
                                onClick = {},
                                modifier = Modifier
                                    .padding(horizontal = 5.dp)
                                    .combinedClickable(
                                        onClick = {
                                            downloadState = Download.STATE_DOWNLOADING
                                            if (podcastPage?.listEpisode?.isNotEmpty() == true)
                                                podcastPage?.listEpisode?.forEach {
                                                    binder?.cache?.removeResource(it.asMediaItem.mediaId)
                                                    Database.asyncTransaction {
                                                        formatTable.findBySongId( it.asMediaItem.mediaId )
                                                    }
                                                    manageDownload(
                                                        context = context,
                                                        mediaItem = it.asMediaItem,
                                                        downloadState = false
                                                    )
                                                }
                                        },
                                        onLongClick = {
                                            Toaster.i( R.string.info_download_all_songs )
                                        }
                                    )
                            )

                            HeaderIconButton(
                                icon = R.drawable.download,
                                color = colorPalette().text,
                                onClick = {},
                                modifier = Modifier
                                    .padding(horizontal = 5.dp)
                                    .combinedClickable(
                                        onClick = {
                                            downloadState = Download.STATE_DOWNLOADING
                                            if (podcastPage?.listEpisode?.isNotEmpty() == true)
                                                podcastPage?.listEpisode?.forEach {
                                                    binder?.cache?.removeResource(it.asMediaItem.mediaId)
                                                    Database.asyncTransaction {
                                                        formatTable.findBySongId( it.asMediaItem.mediaId )
                                                    }
                                                    manageDownload(
                                                        context = context,
                                                        mediaItem = it.asMediaItem,
                                                        downloadState = true
                                                    )
                                                }
                                        },
                                        onLongClick = {
                                            Toaster.i( R.string.info_remove_all_downloaded_songs )
                                        }
                                    )
                            )



                            HeaderIconButton(
                                icon = R.drawable.enqueue,
                                enabled = podcastPage?.listEpisode?.isNotEmpty() == true,
                                color =  if (podcastPage?.listEpisode?.isNotEmpty() == true) colorPalette().text else colorPalette().textDisabled,
                                onClick = {},
                                modifier = Modifier
                                    .padding(horizontal = 5.dp)
                                    .combinedClickable(
                                        onClick = {
                                            binder.player.enqueue(
                                                items = podcastPage?.listEpisode.orEmpty(),
                                                toMediaItem = Innertube.Podcast.EpisodeItem::asMediaItem,
                                                getDuration = {
                                                    durationToMillis( it.durationString.orEmpty() )
                                                }
                                            )
                                        },
                                        onLongClick = {
                                            Toaster.i( R.string.info_enqueue_songs )
                                        }
                                    )
                            )

                            HeaderIconButton(
                                icon = R.drawable.shuffle,
                                enabled = podcastPage?.listEpisode?.isNotEmpty() == true,
                                color = if (podcastPage?.listEpisode?.isNotEmpty() ==true) colorPalette().text else colorPalette().textDisabled,
                                onClick = {},
                                modifier = Modifier
                                    .padding(horizontal = 5.dp)
                                    .combinedClickable(
                                        onClick = {
                                            binder.stopRadio()
                                            podcastPage?.listEpisode?.also( binder.player::playShuffled )
                                        },
                                        onLongClick = {
                                            Toaster.i( R.string.info_shuffle )
                                        }
                                    )
                            )

                            HeaderIconButton(
                                icon = R.drawable.radio,
                                enabled = podcastPage?.listEpisode?.isNotEmpty() == true,
                                color = colorPalette().text,
                                onClick = {},
                                modifier = Modifier
                                    .padding(horizontal = 5.dp)
                                    .combinedClickable(
                                        onClick = {
                                            val mediaItem = binder?.player?.currentMediaItem ?: podcastPage?.listEpisode?.first()?.asMediaItem
                                            mediaItem?.let { binder?.startRadio( it ) }
                                        },
                                        onLongClick = {
                                            Toaster.i( R.string.info_start_radio )
                                        }
                                    )
                            )


                            HeaderIconButton(
                                icon = R.drawable.add_in_playlist,
                                color = colorPalette().text,
                                onClick = {},
                                modifier = Modifier
                                    .padding(horizontal = 5.dp)
                                    .combinedClickable(
                                        onClick = {
                                            menuState.display {
                                                PlaylistsItemMenu(
                                                    navController = navController,
                                                    modifier = Modifier.fillMaxHeight(0.4f),
                                                    onDismiss = menuState::hide,
                                                    onImportOnlinePlaylist = {
                                                        isImportingPlaylist = true
                                                    },

                                                    //NOT NECESSARY IN ONLINE PLAYLIST USE IMPORT
                                                    onAddToPlaylist = { playlistPreview ->
                                                        position =
                                                            playlistPreview.songCount.minus(1) ?: 0
                                                        if (position > 0) position++ else position = 0

                                                        if (!isYouTubeSyncEnabled() || !playlistPreview.playlist.isYoutubePlaylist) {
                                                            val songs = podcastPage?.listEpisode
                                                                                                     ?.map( Innertube.Podcast.EpisodeItem::asMediaItem )
                                                                                                     .orEmpty()
                                                            Database.asyncTransaction {
                                                                mapIgnore( playlistPreview.playlist, *songs.toTypedArray() )
                                                            }
                                                        } else {
                                                            CoroutineScope(Dispatchers.IO).launch {
                                                                playlistPreview.playlist.browseId?.let { id ->
                                                                    addToYtPlaylist(playlistPreview.playlist.id,
                                                                        position,
                                                                        id,
                                                                        podcastPage?.listEpisode?.map { it.asMediaItem } ?: emptyList())
                                                                }
                                                            }
                                                        }

                                                        CoroutineScope(Dispatchers.Main).launch {
                                                            Toaster.done()
                                                        }
                                                    },
                                                    onGoToPlaylist = {
                                                        NavRoutes.localPlaylist.navigateHere( navController, it.toString() )
                                                    }
                                                )
                                            }
                                        },
                                        onLongClick = {
                                            Toaster.i( R.string.info_add_in_playlist )
                                        }
                                    )
                            )


                            /*
                            HeaderIconButton(
                                icon = R.drawable.share_social,
                                color = colorPalette().text,
                                onClick = {
                                    (playlistPage?.url ?: "https://music.youtube.com/playlist?list=${browseId.removePrefix("VL")}").let { url ->
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, url)
                                        }

                                        context.startActivity(Intent.createChooser(sendIntent, null))
                                    }
                                }
                            )
                             */

                        } else {
                            BasicText(
                                text = stringResource(R.string.info_wait_it_may_take_a_few_minutes),
                                style = typography().xxs.medium,
                                maxLines = 1
                            )
                        }
                    }
                    Row (
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier
                            .padding(all = 10.dp)
                            .fillMaxWidth()
                    ) {
                        AnimatedVisibility(visible = searching) {
                            val focusRequester = remember { FocusRequester() }
                            val focusManager = LocalFocusManager.current
                            val keyboardController = LocalSoftwareKeyboardController.current

                            LaunchedEffect(searching) {
                                focusRequester.requestFocus()
                            }

                            BasicTextField(
                                value = filter ?: "",
                                onValueChange = { filter = it },
                                textStyle = typography().xs.semiBold,
                                singleLine = true,
                                maxLines = 1,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    if (filter.isNullOrBlank()) filter = ""
                                    focusManager.clearFocus()
                                }),
                                cursorBrush = SolidColor(colorPalette().text),
                                decorationBox = { innerTextField ->
                                    Box(
                                        contentAlignment = Alignment.CenterStart,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 10.dp)
                                    ) {
                                        IconButton(
                                            onClick = {},
                                            icon = R.drawable.search,
                                            color = colorPalette().favoritesIcon,
                                            modifier = Modifier
                                                .align(Alignment.CenterStart)
                                                .size(16.dp)
                                        )
                                    }
                                    Box(
                                        contentAlignment = Alignment.CenterStart,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 30.dp)
                                    ) {
                                        androidx.compose.animation.AnimatedVisibility(
                                            visible = filter?.isEmpty() ?: true,
                                            enter = fadeIn(tween(100)),
                                            exit = fadeOut(tween(100)),
                                        ) {
                                            BasicText(
                                                text = stringResource( android.R.string.search_go ),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = typography().xs.semiBold.secondary.copy(color = colorPalette().textDisabled)
                                            )
                                        }

                                        innerTextField()
                                    }
                                },
                                modifier = Modifier
                                    .height(30.dp)
                                    .fillMaxWidth()
                                    .background(
                                        colorPalette().background4,
                                        shape = thumbnailRoundness.shape
                                    )
                                    .focusRequester(focusRequester)
                                    .onFocusChanged {
                                        if (!it.hasFocus) {
                                            keyboardController?.hide()
                                            if (filter?.isBlank() == true) {
                                                filter = null
                                                searching = false
                                            }
                                        }
                                    }
                            )
                        }
                    }
                }

                itemsIndexed(
                    items = podcastPage?.listEpisode?.filter {item ->
                        if (!filter.isNullOrBlank()) {
                            item.asMediaItem.mediaMetadata.title?.contains(
                                filterCharSequence,
                                true
                            ) ?: false
                                    || item.asMediaItem.mediaMetadata.artist?.contains(
                                filterCharSequence,
                                true
                            ) ?: false
                        } else true
                    } ?: emptyList())
                { index, song ->
                    val isLocal by remember { derivedStateOf { song.asMediaItem.isLocal } }
                    val isDownloaded = if (!isLocal) isDownloadedSong(song.asMediaItem.mediaId) else true

                    SwipeablePlaylistItem(
                        mediaItem = song.asMediaItem,
                        onPlayNext = {
                            binder.player.addNext(
                                item = song,
                                toMediaItem = Innertube.Podcast.EpisodeItem::asMediaItem,
                                getDuration = {
                                    durationToMillis( it.durationString.orEmpty() )
                                }
                            )
                        },
                        onDownload = {
                            binder?.cache?.removeResource(song.asMediaItem.mediaId)
                            Database.asyncTransaction {
                                formatTable.updateContentLengthOf( song.asMediaItem.mediaId )
                            }

                            if (!isLocal)
                                manageDownload(
                                    context = context,
                                    mediaItem = song.asMediaItem,
                                    downloadState = isDownloaded
                                )
                        },
                        onEnqueue = {
                            binder.player.enqueue(
                                item = song,
                                toMediaItem = Innertube.Podcast.EpisodeItem::asMediaItem,
                                getDuration = {
                                    durationToMillis( it.durationString.orEmpty() )
                                }
                            )
                        }
                    ) {
                        SongItem.Render(
                            mediaItem = song.asMediaItem,
                            context = context,
                            binder = binder,
                            hapticFeedback = hapticFeedback,
                            values = songItemValues,
                            isPlaying = song.videoId == currentlyPlaying,
                            onLongClick = {
                                menuState.display {
                                    NonQueuedMediaItemMenu(
                                        navController = navController,
                                        onDismiss = menuState::hide,
                                        mediaItem = song.asMediaItem
                                    )
                                }
                            },
                            onClick = {
                                searching = false
                                filter = null

                                binder.stopRadio()
                                binder.player.forcePlayAtIndex(
                                    items = podcastPage?.listEpisode.orEmpty(),
                                    index = index,
                                    toMediaItem = Innertube.Podcast.EpisodeItem::asMediaItem,
                                    getDuration = {
                                        durationToMillis( it.durationString.orEmpty() )
                                    }
                                )
                            }
                        )
                    }
                }

                item(
                    key = "footer",
                    contentType = 0,
                ) {
                    Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
                }

                if (podcastPage == null) {
                    item( "loading" ) { SongItem.Placeholder() }
                }
            }

            val showFloatingIcon by Preferences.SHOW_FLOATING_ICON
            if( UiType.ViMusic.isCurrent() && showFloatingIcon )
            FloatingActionsContainerWithScrollToTop(
                lazyListState = lazyListState,
                iconId = R.drawable.shuffle,
                onClick = {
                    binder.stopRadio()
                    podcastPage?.listEpisode?.also( binder.player::playShuffled )
                }
            )


        }
    }
}
