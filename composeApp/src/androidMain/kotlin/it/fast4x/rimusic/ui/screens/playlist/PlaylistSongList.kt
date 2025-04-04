package it.fast4x.rimusic.ui.screens.playlist

import android.annotation.SuppressLint
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.R
import coil.compose.AsyncImage
import it.fast4x.compose.persist.persist
import it.fast4x.compose.persist.persistList
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.requests.PlaylistPage
import it.fast4x.rimusic.Database
import it.fast4x.rimusic.LocalPlayerServiceBinder
import it.fast4x.rimusic.appContext
import it.fast4x.rimusic.cleanPrefix
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.enums.ThumbnailRoundness
import it.fast4x.rimusic.enums.UiType
import it.fast4x.rimusic.models.Playlist
import it.fast4x.rimusic.models.Song
import it.fast4x.rimusic.service.isLocal
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.ShimmerHost
import it.fast4x.rimusic.ui.components.SwipeablePlaylistItem
import it.fast4x.rimusic.ui.components.themed.AutoResizeText
import it.fast4x.rimusic.ui.components.themed.ConfirmationDialog
import it.fast4x.rimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import it.fast4x.rimusic.ui.components.themed.FontSizeRange
import it.fast4x.rimusic.ui.components.themed.HeaderIconButton
import it.fast4x.rimusic.ui.components.themed.IconButton
import it.fast4x.rimusic.ui.components.themed.InputTextDialog
import it.fast4x.rimusic.ui.components.themed.LayoutWithAdaptiveThumbnail
import it.fast4x.rimusic.ui.components.themed.PlaylistsItemMenu
import it.fast4x.rimusic.ui.components.themed.adaptiveThumbnailContent
import it.fast4x.rimusic.ui.items.AlbumItemPlaceholder
import it.fast4x.rimusic.ui.items.SongItemPlaceholder
import it.fast4x.rimusic.ui.screens.settings.isYouTubeSyncEnabled
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.favoritesIcon
import it.fast4x.rimusic.utils.addNext
import it.fast4x.rimusic.utils.addToYtLikedSongs
import it.fast4x.rimusic.utils.align
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.asSong
import it.fast4x.rimusic.utils.color
import it.fast4x.rimusic.utils.disableScrollingTextKey
import it.fast4x.rimusic.utils.durationTextToMillis
import it.fast4x.rimusic.utils.enqueue
import it.fast4x.rimusic.utils.fadingEdge
import it.fast4x.rimusic.utils.forcePlayAtIndex
import it.fast4x.rimusic.utils.forcePlayFromBeginning
import it.fast4x.rimusic.utils.formatAsDuration
import it.fast4x.rimusic.utils.formatAsTime
import it.fast4x.rimusic.utils.getHttpClient
import it.fast4x.rimusic.utils.isDownloadedSong
import it.fast4x.rimusic.utils.isLandscape
import it.fast4x.rimusic.utils.isNetworkConnected
import it.fast4x.rimusic.utils.languageDestination
import it.fast4x.rimusic.utils.manageDownload
import it.fast4x.rimusic.utils.medium
import it.fast4x.rimusic.utils.operatorFilterSongItem
import it.fast4x.rimusic.utils.parentalControlEnabledKey
import it.fast4x.rimusic.utils.rememberPreference
import it.fast4x.rimusic.utils.resize
import it.fast4x.rimusic.utils.secondary
import it.fast4x.rimusic.utils.semiBold
import it.fast4x.rimusic.utils.showFloatingIconKey
import it.fast4x.rimusic.utils.thumbnailRoundnessKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import me.bush.translator.Language
import me.bush.translator.Translator
import me.knighthat.utils.Toaster


@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun PlaylistSongList(
    navController: NavController,
    browseId: String,
) {
    // Context
    val binder = LocalPlayerServiceBinder.current
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val hapticFeedback = LocalHapticFeedback.current
    val lazyListState = rememberLazyListState()

    // Settings
    val parentalControlEnabled by rememberPreference(parentalControlEnabledKey, false)
    val disableScrollingText by rememberPreference(disableScrollingTextKey, false)

    var playlistPage by persist<PlaylistPage?>("playlist/$browseId/playlistPage")
    var continuation: String? by remember { mutableStateOf( null ) }
    var playlistSongs by persistList<Innertube.SongItem>("playlist/$browseId/songs")

    val updatedItemsPageProvider: suspend (String?) -> Result<PlaylistPage> by rememberUpdatedState {
        if( it == null )
            YtMusic.getPlaylist( browseId )
        else
            YtMusic.getPlaylistContinuation( it )
                   .map { fetchedPlaylist ->
                       playlistPage!!.copy(
                           songs = fetchedPlaylist?.songs.orEmpty(),
                           songsContinuation = fetchedPlaylist?.continuation
                       )
                   }
    }

    LaunchedEffect( lazyListState ) {
        snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.any { it.key == "loading" } }
            .collect { shouldLoadMore ->
                if ( !shouldLoadMore ) return@collect

                withContext(Dispatchers.IO) {
                    updatedItemsPageProvider(continuation)
                }.onSuccess { onlinePlaylist ->
                    if( continuation == null )
                        playlistPage = onlinePlaylist

                    playlistSongs += onlinePlaylist.songs.filter { !parentalControlEnabled || !it.explicit }
                    continuation = onlinePlaylist.songsContinuation
                }.exceptionOrNull()?.printStackTrace()
            }
    }

    var filter: String? by rememberSaveable { mutableStateOf(null) }
    var saveCheck by remember { mutableStateOf(false) }

    val sectionTextModifier = Modifier
        .padding(horizontal = 16.dp)
        .padding(top = 24.dp, bottom = 8.dp)

    var translateEnabled by remember {
        mutableStateOf(false)
    }

    val translator = Translator(getHttpClient())
    val languageDestination = languageDestination()

    val localPlaylist by remember( saveCheck ) {
        Database.playlistTable
                .findByBrowseId( browseId.substringAfter("VL") )
    }.collectAsState( null, Dispatchers.IO )

    var filterCharSequence: CharSequence
    filterCharSequence = filter.toString()
    //Log.d("mediaItemFilter", "<${filter}>  <${filterCharSequence}>")
    if (!filter.isNullOrBlank()) {
        playlistPage?.songs =
            playlistPage?.songs?.operatorFilterSongItem(filterCharSequence)!!
    } else playlistPage?.songs = playlistSongs

    var playlistNotLikedSongs by persistList<Innertube.SongItem>("")

    var searching by rememberSaveable { mutableStateOf(false) }

    var isImportingPlaylist by rememberSaveable {
        mutableStateOf(false)
    }

    var thumbnailRoundness by rememberPreference(
        thumbnailRoundnessKey,
        ThumbnailRoundness.Heavy
    )

    var showYoutubeLikeConfirmDialog by remember {
        mutableStateOf(false)
    }
    var totalMinutesToLike by remember { mutableStateOf("") }

    if (showYoutubeLikeConfirmDialog) {
        runBlocking {
            playlistNotLikedSongs = playlistSongs.filter {
                Database.songTable.isLiked( it.asSong.id ).first()
            }
        }
        totalMinutesToLike = formatAsDuration(playlistNotLikedSongs.size.toLong()*1000)
        ConfirmationDialog(
            text = "$totalMinutesToLike "+stringResource(R.string.do_you_really_want_to_like_all),
            onDismiss = { showYoutubeLikeConfirmDialog = false },
            onConfirm = {
                showYoutubeLikeConfirmDialog = false
                CoroutineScope(Dispatchers.IO).launch {
                    addToYtLikedSongs(playlistNotLikedSongs.map {it.asMediaItem})
                }
            }
        )
    }

    var totalPlayTimes = 0L
    playlistPage?.songs?.forEach {
        totalPlayTimes += it.durationText?.let { it1 ->
            durationTextToMillis(it1) }?.toLong() ?: 0
    }

    val dislikedSongs by remember {
        Database.songTable
                .allDisliked()
                .map { list ->
                    list.map( Song::id )
                }
                .distinctUntilChanged()
    }.collectAsState( emptyList(), Dispatchers.IO )

    if (isImportingPlaylist) {
        InputTextDialog(
            onDismiss = { isImportingPlaylist = false },
            title = stringResource(R.string.enter_the_playlist_name),
            value = playlistPage?.playlist?.title ?: "",
            placeholder = "https://........",
            setValue = { text ->
                Database.asyncTransaction {
                    val playlist = Playlist(name = text, browseId = browseId)

                    playlistPage?.songs
                                ?.map( Innertube.SongItem::asMediaItem )
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

    val thumbnailContent = adaptiveThumbnailContent(playlistPage == null, playlistPage?.playlist?.thumbnail?.url)

    LayoutWithAdaptiveThumbnail(thumbnailContent = thumbnailContent) {
        Box(
            modifier = Modifier
                .background(colorPalette().background0)
                //.fillMaxSize()
                .fillMaxHeight()
                .fillMaxWidth(
                    if (NavigationBarPosition.Right.isCurrent())
                        Dimensions.contentWidthRightBar
                    else
                        1f
                )
        ) {
            LazyColumn(
                state = lazyListState,
                contentPadding = PaddingValues(bottom = Dimensions.bottomSpacer),
                modifier = Modifier.background( colorPalette().background0 )
                                   .fillMaxSize()
            ) {

                item(
                    key = "header"
                ) {

                    val modifierArt = Modifier.fillMaxWidth()

                    Box(
                        modifier = modifierArt
                    ) {
                        if (playlistPage != null) {
                            if(!isLandscape)
                                Box {
                                    AsyncImage(
                                        model = playlistPage!!.playlist.thumbnail?.url?.resize(
                                            1200,
                                            1200
                                        ),
                                        contentDescription = "loading...",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.Center)
                                            .fadingEdge(
                                                top = WindowInsets.systemBars
                                                    .asPaddingValues()
                                                    .calculateTopPadding() + Dimensions.fadeSpacingTop,
                                                bottom = Dimensions.fadeSpacingBottom
                                            )
                                    )
                                }
                                if (localPlaylist?.isYoutubePlaylist == true) {
                                   Image(
                                        painter = painterResource(R.drawable.ytmusic),
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(
                                        Color.Red.copy(0.75f).compositeOver(Color.White)
                                        ),
                                        modifier = Modifier
                                            .size(40.dp)
                                            .offset(5.dp, 5.dp)
                                   )
                                }

                            AutoResizeText(
                                text = playlistPage?.playlist?.title ?: "",
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
                                text = playlistPage!!.songs?.size.toString() + " "
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
                                    .padding(top = 5.dp, end = 5.dp),
                                onClick = {
                                    (playlistPage?.playlist?.thumbnail?.url ?: "https://music.youtube.com/playlist?list=${browseId.removePrefix("VL")}").let { url ->
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
                                ShimmerHost {
                                    AlbumItemPlaceholder(
                                        thumbnailSizeDp = 200.dp,
                                        alternative = true
                                    )
                                }
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

                        if (playlistPage != null) {

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
                                            if (playlistPage?.songs?.any { it.asMediaItem.mediaId !in dislikedSongs } == true) {
                                                if (playlistPage?.songs?.any { it.asMediaItem.mediaId !in dislikedSongs } == true)
                                                    playlistPage?.songs?.filter { it.asMediaItem.mediaId !in dislikedSongs }
                                                        ?.forEach {
                                                            binder?.cache?.removeResource(it.asMediaItem.mediaId)
                                                            Database.asyncTransaction {
                                                                formatTable.findBySongId( it.key )
                                                            }
                                                            manageDownload(
                                                                context = context,
                                                                mediaItem = it.asMediaItem,
                                                                downloadState = false
                                                            )
                                                        } else
                                                    Toaster.e(R.string.disliked_this_collection)
                                            }
                                        },
                                        onLongClick = {
                                            Toaster.i(R.string.info_download_all_songs)
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
                                            if (playlistPage?.songs?.any { it.asMediaItem.mediaId !in dislikedSongs } == true) {
                                                if (playlistPage?.songs?.isNotEmpty() == true)
                                                    playlistPage?.songs?.forEach {
                                                        binder?.cache?.removeResource(it.asMediaItem.mediaId)
                                                        Database.asyncTransaction {
                                                            formatTable.findBySongId( it.key )
                                                        }
                                                        manageDownload(
                                                            context = context,
                                                            mediaItem = it.asMediaItem,
                                                            downloadState = true
                                                        )
                                                    } else {
                                                    Toaster.e(R.string.disliked_this_collection)
                                                }
                                            }
                                        },
                                        onLongClick = {
                                            Toaster.n(R.string.info_remove_all_downloaded_songs)
                                        }
                                    )
                            )



                            HeaderIconButton(
                                icon = R.drawable.enqueue,
                                enabled = playlistPage?.songs?.any { it.asMediaItem.mediaId !in dislikedSongs } == true,
                                color =  if (playlistPage?.songs?.any { it.asMediaItem.mediaId !in dislikedSongs } == true) colorPalette().text else colorPalette().textDisabled,
                                onClick = {},
                                modifier = Modifier
                                    .padding(horizontal = 5.dp)
                                    .combinedClickable(
                                        onClick = {
                                            if (playlistPage?.songs?.any { it.asMediaItem.mediaId !in dislikedSongs } == true) {
                                                playlistPage?.songs?.filter { it.asMediaItem.mediaId !in dislikedSongs }
                                                    ?.map(Innertube.SongItem::asMediaItem)
                                                    ?.let { mediaItems ->
                                                        binder?.player?.enqueue(mediaItems, context)
                                                    }
                                            } else
                                                Toaster.e(R.string.disliked_this_collection)
                                        },
                                        onLongClick = {
                                            Toaster.i(R.string.info_enqueue_songs)
                                        }
                                    )
                            )

                            HeaderIconButton(
                                icon = R.drawable.shuffle,
                                enabled = playlistPage?.songs?.any { it.asMediaItem.mediaId !in dislikedSongs } == true,
                                color = if (playlistPage?.songs?.any { it.asMediaItem.mediaId !in dislikedSongs } == true) colorPalette().text else colorPalette().textDisabled,
                                onClick = {},
                                modifier = Modifier
                                    .padding(horizontal = 5.dp)
                                    .combinedClickable(
                                        onClick = {
                                            if (playlistPage?.songs?.any { it.asMediaItem.mediaId !in dislikedSongs } == true) {
                                                binder?.stopRadio()
                                                playlistPage?.songs?.filter { it.asMediaItem.mediaId !in dislikedSongs }
                                                    ?.shuffled()
                                                    ?.map(Innertube.SongItem::asMediaItem)
                                                    ?.let {
                                                        binder?.player?.forcePlayFromBeginning(
                                                            it
                                                        )
                                                    }
                                            } else
                                                Toaster.e( R.string.disliked_this_collection )
                                        },
                                        onLongClick = {
                                            Toaster.i( R.string.info_shuffle )
                                        }
                                    )
                            )

                            HeaderIconButton(
                                icon = R.drawable.radio,
                                enabled = playlistPage?.songs?.any { it.asMediaItem.mediaId !in dislikedSongs } == true,
                                color = if (playlistPage?.songs?.any { it.asMediaItem.mediaId !in dislikedSongs } == true) colorPalette().text else colorPalette().textDisabled,
                                onClick = {},
                                modifier = Modifier
                                    .padding(horizontal = 5.dp)
                                    .combinedClickable(
                                        onClick = {
                                            if (binder != null) {
                                                if (playlistPage?.songs?.any { it.asMediaItem.mediaId !in dislikedSongs } == true) {
                                                    binder.stopRadio()
                                                    binder.playRadio(
                                                        NavigationEndpoint.Endpoint.Watch(videoId =
                                                        if (binder.player.currentMediaItem?.mediaId != null)
                                                            binder.player.currentMediaItem?.mediaId
                                                        else playlistPage?.songs?.first { it.asMediaItem.mediaId !in dislikedSongs }?.asMediaItem?.mediaId
                                                        )
                                                    )
                                                } else
                                                    Toaster.e( R.string.disliked_this_collection )
                                            }
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

                                                    onAddToPlaylist = { playlistPreview ->
                                                        position =
                                                            playlistPreview.songCount.minus(1) ?: 0
                                                        if (position > 0) position++ else position =
                                                            0

                                                        val playlistSize = playlistPage?.songs?.size ?: 0

                                                        if ((playlistSize + playlistPreview.songCount) > 5000 && playlistPreview.playlist.isYoutubePlaylist && isYouTubeSyncEnabled()){
                                                            Toaster.e( R.string.yt_playlist_limited )
                                                        } else if (!isYouTubeSyncEnabled() || !playlistPreview.playlist.isYoutubePlaylist) {
                                                            Database.asyncTransaction {
                                                                val songs = playlistPage?.songs
                                                                                                          ?.map( Innertube.SongItem::asMediaItem )
                                                                                                          .orEmpty()
                                                                mapIgnore( playlistPreview.playlist, *songs.toTypedArray() )
                                                            }
                                                        } else {
                                                            CoroutineScope(Dispatchers.IO).launch {
                                                                YtMusic.addPlaylistToPlaylist(
                                                                    cleanPrefix(playlistPreview.playlist.browseId ?: ""),
                                                                    browseId.substringAfter("VL")

                                                                    )
                                                                }
                                                        }
                                                        Toaster.done()
                                                    },
                                                    onGoToPlaylist = {
                                                        navController.navigate("${NavRoutes.localPlaylist.name}/$it")
                                                    },
                                                    disableScrollingText = disableScrollingText
                                                )
                                            }
                                        },
                                        onLongClick = {
                                            Toaster.i( R.string.info_add_in_playlist )
                                        }
                                    )
                            )
                            HeaderIconButton(
                                icon = R.drawable.heart,
                                enabled = playlistPage?.songs?.isNotEmpty() == true,
                                color = colorPalette().text,
                                onClick = {},
                                modifier = Modifier
                                    .padding(horizontal = 5.dp)
                                    .combinedClickable(
                                        onClick = {
                                            if (!isNetworkConnected(appContext()) && isYouTubeSyncEnabled()) {
                                                Toaster.noInternet()
                                            } else if (!isYouTubeSyncEnabled()){
                                                CoroutineScope( Dispatchers.IO ).launch {
                                                    playlistPage!!.songs
                                                                  .map{ it.asSong.id }
                                                                  .filter {
                                                                      !Database.songTable.isLiked( it ).first()
                                                                  }
                                                                  .forEach( Database.songTable::toggleLike )

                                                    Toaster.done()
                                                }
                                            } else {
                                                showYoutubeLikeConfirmDialog = true
                                            }
                                        },
                                        onLongClick = {
                                            Toaster.i( R.string.add_to_favorites )
                                        }
                                    )
                            )
                            if (isYouTubeSyncEnabled()) {
                                HeaderIconButton(
                                    icon = if (localPlaylist?.isYoutubePlaylist == true) R.drawable.bookmark else R.drawable.bookmark_outline,
                                    color = colorPalette().text,
                                    onClick = {},
                                    modifier = Modifier
                                        .padding(horizontal = 5.dp)
                                        .combinedClickable(
                                            onClick = {
                                                if (isNetworkConnected(context)) {
                                                    if (localPlaylist?.isYoutubePlaylist == true) {
                                                        CoroutineScope(Dispatchers.IO).launch {
                                                            YtMusic.removelikePlaylistOrAlbum(
                                                                browseId.substringAfter("VL")
                                                            )

                                                            Database.playlistTable
                                                                    .findByBrowseId( browseId.substringAfter("VL") )
                                                                    .first()
                                                                    ?.let( Database.playlistTable::delete )
                                                        }
                                                    } else {
                                                        CoroutineScope(Dispatchers.IO).launch {
                                                            YtMusic.likePlaylistOrAlbum(
                                                                browseId.substringAfter(
                                                                    "VL"
                                                                )
                                                            )
                                                        }

                                                        Database.asyncTransaction {
                                                            val playlist = Playlist(
                                                                name = (playlistPage?.playlist?.title ?: ""),
                                                                browseId = browseId.substringAfter("VL"),
                                                                isYoutubePlaylist = true,
                                                                isEditable = false
                                                            )

                                                            playlistPage?.songs
                                                                        ?.map( Innertube.SongItem::asMediaItem )
                                                                        ?.let { mapIgnore( playlist, *it.toTypedArray() ) }
                                                        }
                                                    }
                                                    Toaster.done()
                                                    saveCheck = !saveCheck
                                                } else
                                                    Toaster.noInternet()
                                            },
                                            onLongClick = {
                                                Toaster.i( R.string.save_youtube_library )
                                            }
                                        )
                                )
                            }


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
                                                text = stringResource(R.string.search),
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

                playlistPage?.description?.let { description ->
                    item(
                        key = "playlistInfo"
                    ) {

                        val attributionsIndex = description.lastIndexOf("\n\nFrom Wikipedia")

                        BasicText(
                            text = stringResource(R.string.information),
                            style = typography().m.semiBold.align(TextAlign.Start),
                            modifier = sectionTextModifier
                                .fillMaxWidth()
                        )

                        Row(
                            modifier = Modifier
                                //.padding(top = 16.dp)
                                .padding(vertical = 16.dp, horizontal = 8.dp)
                            //.padding(endPaddingValues)
                            //.padding(end = Dimensions.bottomSpacer)
                        ) {
                            IconButton(
                                icon = R.drawable.translate,
                                color = if (translateEnabled == true) colorPalette()
                                    .text else colorPalette()
                                    .textDisabled,
                                enabled = true,
                                onClick = {},
                                modifier = Modifier
                                    .padding(all = 8.dp)
                                    .size(18.dp)
                                    .combinedClickable(
                                        onClick = {
                                            translateEnabled = !translateEnabled
                                        },
                                        onLongClick = {
                                            Toaster.i( R.string.info_translation )
                                        }
                                    )
                            )
                            BasicText(
                                text = "“",
                                style = typography().xxl.semiBold,
                                modifier = Modifier
                                    .offset(y = (-8).dp)
                                    .align(Alignment.Top)
                            )

                            var translatedText by remember { mutableStateOf("") }
                            val nonTranslatedText by remember {
                                mutableStateOf(
                                    if (attributionsIndex == -1) {
                                        description
                                    } else {
                                        description.substring(0, attributionsIndex)
                                    }
                                )
                            }


                            if (translateEnabled == true) {
                                LaunchedEffect(Unit) {
                                    val result = withContext(Dispatchers.IO) {
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
                                modifier = Modifier
                                    .padding(horizontal = 8.dp)
                                    .weight(1f)
                            )

                            BasicText(
                                text = "„",
                                style = typography().xxl.semiBold,
                                modifier = Modifier
                                    .offset(y = 4.dp)
                                    .align(Alignment.Bottom)
                            )
                        }

                        if (attributionsIndex != -1) {
                            BasicText(
                                text = stringResource(R.string.from_wikipedia_cca),
                                style = typography().xxs.color(
                                    colorPalette()
                                        .textDisabled).align(
                                    TextAlign.Start
                                ),
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .padding(bottom = 16.dp)
                                //.padding(endPaddingValues)
                            )
                        }

                    }
                }

                items(
                    items = playlistSongs,
                    key = Innertube.SongItem::key
                ) { ytSong ->
                    val isLocal by remember { derivedStateOf { ytSong.asMediaItem.isLocal } }
                    val isDownloaded = !isLocal && isDownloadedSong( ytSong.key )

                    SwipeablePlaylistItem(
                        mediaItem = ytSong.asMediaItem,
                        onPlayNext = {
                            binder?.player?.addNext(ytSong.asMediaItem)
                        },
                        onDownload = {
                            binder?.cache?.removeResource( ytSong.key )
                            Database.asyncTransaction {
                                formatTable.updateContentLengthOf( ytSong.key )
                            }

                            if (!isLocal)
                                manageDownload(
                                    context = context,
                                    mediaItem = ytSong.asMediaItem,
                                    downloadState = isDownloaded
                                )
                        },
                        onEnqueue = {
                            binder?.player?.enqueue(ytSong.asMediaItem)
                        }
                    ) {
                        me.knighthat.component.SongItem(
                            song = ytSong.asSong,
                            onClick = {
                                if ( ytSong.key !in dislikedSongs ) {
                                    searching = false
                                    filter = null
                                    playlistPage?.songs
                                                ?.filter { it.key !in dislikedSongs }
                                                ?.map(Innertube.SongItem::asMediaItem)
                                                ?.let { mediaItems ->
                                                    binder?.stopRadio()
                                                    binder?.player?.forcePlayAtIndex(
                                                        mediaItems,
                                                        mediaItems.indexOf( ytSong.asMediaItem )
                                                    )
                                                }
                                } else
                                    Toaster.e( R.string.disliked_this_song )
                            }
                        )
                    }
                }

                if ( playlistPage == null || continuation != null )
                    item( "loading" ) { SongItemPlaceholder() }
            }

            val showFloatingIcon by rememberPreference(showFloatingIconKey, false)
            if( UiType.ViMusic.isCurrent() && showFloatingIcon )
            FloatingActionsContainerWithScrollToTop(
                lazyListState = lazyListState,
                iconId = R.drawable.shuffle,
                onClick = {
                    if (playlistPage?.songs?.any { it.asMediaItem.mediaId !in dislikedSongs } == true) {
                        binder?.stopRadio()
                        playlistPage?.songs?.filter{ it.asMediaItem.mediaId !in dislikedSongs }?.shuffled()?.map(Innertube.SongItem::asMediaItem)
                            ?.let {
                                binder?.player?.forcePlayFromBeginning(
                                    it
                                )
                            }
                    } else
                        Toaster.e( R.string.disliked_this_collection )
                }
            )


        }
    }
}
