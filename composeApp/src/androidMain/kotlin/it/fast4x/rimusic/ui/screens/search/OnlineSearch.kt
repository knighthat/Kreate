package it.fast4x.rimusic.ui.screens.search

import androidx.annotation.OptIn
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import app.kreate.android.R
import app.kreate.android.service.player.StatefulPlayer
import app.kreate.android.themed.rimusic.component.album.AlbumItem
import app.kreate.android.themed.rimusic.component.artist.ArtistItem
import app.kreate.android.themed.rimusic.component.playlist.PlaylistItem
import app.kreate.android.themed.rimusic.component.song.SongItem
import app.kreate.android.viewmodel.OnlineSearchViewModel
import app.kreate.database.Database
import app.kreate.database.models.SearchQuery
import app.kreate.gateway.innertube.PageType
import app.kreate.gateway.innertube.models.InnertubeAlbum
import app.kreate.gateway.innertube.models.InnertubeArtist
import app.kreate.gateway.innertube.models.InnertubeItem
import app.kreate.gateway.innertube.models.InnertubePlaylist
import app.kreate.gateway.innertube.models.InnertubeSearchSuggestion
import app.kreate.gateway.innertube.models.InnertubeSong
import app.kreate.preferences.Preferences
import it.fast4x.rimusic.LocalPlayerAwareWindowInsets
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.themed.FloatingActionsContainerWithScrollToTop
import it.fast4x.rimusic.ui.components.themed.Header
import it.fast4x.rimusic.ui.components.themed.NonQueuedMediaItemMenu
import it.fast4x.rimusic.ui.components.themed.TitleMiniSection
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.align
import it.fast4x.rimusic.utils.forcePlay
import it.fast4x.rimusic.utils.medium
import it.fast4x.rimusic.utils.secondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel


@OptIn(UnstableApi::class)
private fun InnertubeSearchSuggestion.Item.toMediaItem(): MediaItem {
    val metadata = MediaMetadata.Builder()
        .setMediaType( MediaMetadata.MEDIA_TYPE_MUSIC )
        .setTitle( name )
        .setArtworkUri( thumbnails.lastOrNull()?.url?.toUri() )

    subtitle?.runs
            ?.firstOrNull {
                it.navigationEndpoint
                    ?.browseEndpoint
                    ?.browseEndpointContextSupportedConfigs
                    ?.browseEndpointContextMusicConfig
                    ?.pageType == PageType.ARTIST
            }
            ?.also { metadata.setArtist(it.text) }

    return MediaItem.Builder()
        .setMediaId( id )
        .setMediaMetadata( metadata.build() )
        .setUri( id.toUri() )
        .setCustomCacheKey( id )
        .build()
}

@UnstableApi
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalTextApi
@Composable
fun OnlineSearch(
    navController: NavController,
    textFieldValue: TextFieldValue,
    onTextFieldValueChanged: (TextFieldValue) -> Unit,
    onSearch: (String) -> Unit,
    decorationBox: @Composable (@Composable () -> Unit) -> Unit,
    viewModel: OnlineSearchViewModel = koinViewModel()
) {
    // Settings
    val isHistoryPaused by Preferences.PAUSE_SEARCH_HISTORY.collectAsStateWithLifecycle()

    var reloadHistory by remember {
        mutableStateOf(false)
    }

    val history by remember( textFieldValue.text, isHistoryPaused, reloadHistory ) {
        if( isHistoryPaused ) return@remember flowOf()

        Database.searchTable
                .findAllContain( textFieldValue.text )
                .distinctUntilChanged()
                .map{ list -> list.reversed() }
    }.collectAsState( emptyList(), Dispatchers.IO )

    val suggestion by viewModel.suggestion.collectAsStateWithLifecycle()
    val isFetchingSuggestions by viewModel.isFetchingSuggestions.collectAsStateWithLifecycle()
    LaunchedEffect(textFieldValue.text) {
        viewModel.onQueryChanged( textFieldValue.text )
    }

    val rippleIndication = ripple(bounded = false)
    val timeIconPainter = painterResource(R.drawable.search_circle)
    val closeIconPainter = painterResource(R.drawable.trash)

    val coroutineScope = rememberCoroutineScope()

    val focusRequester = remember {
        FocusRequester()
    }

    val thumbnailRoundness by Preferences.THUMBNAIL_BORDER_RADIUS.collectAsStateWithLifecycle()

    val lazyListState = rememberLazyListState()

    val menuState = LocalMenuState.current
    val player: StatefulPlayer = koinInject()
    val (colorPalette, typography) = LocalAppearance.current

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
        val songItemValues = remember( colorPalette, typography ) {
            SongItem.Values.from( colorPalette, typography )
        }

        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current
                .only(WindowInsetsSides.Vertical + WindowInsetsSides.End).asPaddingValues(),
            modifier = Modifier
                .fillMaxSize()
        ) {
            item(
                key = "header",
                contentType = 0
            ) {
                Header(
                    titleContent = {
                        BasicTextField(
                            value = textFieldValue,
                            onValueChange = onTextFieldValueChanged,
                            textStyle = typography().l.medium.align(TextAlign.Start),
                            singleLine = true,
                            maxLines = 1,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if ( textFieldValue.text.isNotEmpty() )
                                        onSearch( textFieldValue.text )
                                }
                            ),
                            cursorBrush = SolidColor(colorPalette().text),
                            decorationBox = decorationBox,
                            modifier = Modifier
                                .background(
                                    //colorPalette().background4,
                                    colorPalette().background1,
                                    shape = thumbnailRoundness.shape
                                )
                                .padding(all = 4.dp)
                                .focusRequester(focusRequester)
                                .fillMaxWidth()
                        )
                    },
                    actionsContent = {}
                )
            }

            suggestion?.also { suggestion ->
                if( suggestion.items.isNotEmpty() )
                    item {
                        TitleMiniSection(title = stringResource(R.string.searches_suggestions),
                            modifier = Modifier.padding(start = 12.dp).padding(vertical = 10.dp)
                        )
                    }

                items(
                    items = suggestion.items.distinctBy( InnertubeItem::id ),
                    key = InnertubeItem::id
                ) { item ->
                    ListItem(
                        headlineContent = {
                            SongItem.Title( item.name, songItemValues )
                        },
                        supportingContent = {
                            // Only render subtitle if there's something to render
                            val subtitle = item.subtitle?.joinToString( "" ) ?: return@ListItem
                            SongItem.Artists( subtitle, songItemValues )
                        },
                        leadingContent = {
                            val thumbnailUrl = item.thumbnails.lastOrNull()?.url

                            when( item.type ) {
                                InnertubeSong::class -> SongItem.Thumbnail(
                                    thumbnailUrl = thumbnailUrl,
                                    values = songItemValues,
                                    sizeDp = SongItem.thumbnailSize()
                                )

                                InnertubeAlbum::class -> AlbumItem.Thumbnail(
                                    item.id,
                                    thumbnailUrl = thumbnailUrl,
                                    sizeDp = SongItem.thumbnailSize() ,
                                    showPlatformIcon = false
                                )

                                InnertubeArtist::class -> ArtistItem.Thumbnail(
                                    item.id,
                                    thumbnailUrl = thumbnailUrl,
                                    sizeDp = SongItem.thumbnailSize() ,
                                    showPlatformIcon = false
                                )

                                InnertubePlaylist::class -> PlaylistItem.Thumbnail(
                                    item.id,
                                    thumbnailUrl = thumbnailUrl,
                                    sizeDp = SongItem.thumbnailSize() ,
                                    showPlatformIcon = false
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        ),
                        modifier = Modifier.combinedClickable(
                            role = Role.Button,
                            onClick = {
                                when( item.type ) {
                                    InnertubeSong::class -> player.forcePlay( item.toMediaItem() )
                                    InnertubeAlbum::class -> NavRoutes.YT_ALBUM.navigateHere( navController, item.id )
                                    InnertubeArtist::class -> NavRoutes.YT_ARTIST.navigateHere( navController, item.id )
                                    InnertubePlaylist::class -> NavRoutes.YT_PLAYLIST.navigateHere( navController, item.id )
                                }
                            },
                            onLongClick = {
                                if( item.type != InnertubeSong::class ) return@combinedClickable

                                menuState.display {
                                    NonQueuedMediaItemMenu(
                                        navController = navController,
                                        onDismiss = menuState::hide,
                                        mediaItem = item.toMediaItem()
                                    )
                                }
                            }
                        )
                    )
                }

                items(
                    items = suggestion.suggestions.distinctBy( InnertubeSearchSuggestion.Suggestion::query ),
                    key = InnertubeSearchSuggestion.Suggestion::query
                ) { suggestion ->
                    val query = suggestion.query

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable (
                                onClick = {
                                    onSearch(query.replace("/", "", true))
                                }
                            )
                            .fillMaxWidth()
                            .padding(all = 16.dp)
                    ) {
                        Spacer(
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .size(20.dp)
                        )

                        BasicText(
                            text = query,
                            style = typography().s.secondary,
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .weight(1f)
                        )

                        Image(
                            painter = painterResource(R.drawable.pencil),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colorPalette().textDisabled),
                            modifier = Modifier
                                .clickable(
                                    indication = rippleIndication,
                                    interactionSource = remember { MutableInteractionSource() },
                                    onClick = {
                                        onTextFieldValueChanged(
                                            TextFieldValue(
                                                text = query,
                                                selection = TextRange(query.length)
                                            )
                                        )
                                        coroutineScope.launch {
                                            lazyListState.animateScrollToItem(0)
                                        }
                                    }
                                )
                                //.rotate(225f)
                                .padding(horizontal = 8.dp)
                                .size(22.dp)
                        )
                    }
                }
            }
            if( suggestion == null && !isFetchingSuggestions && textFieldValue.text.isNotBlank() )
                item {
                    Box( Modifier.fillMaxSize() ) {
                        TitleMiniSection(title = stringResource(R.string.searches_no_suggestions),
                            modifier = Modifier.padding(start = 12.dp).padding(vertical = 10.dp)
                        )
                    }
                }

            if(history.isNotEmpty())
                item {
                    TitleMiniSection(title = stringResource(R.string.searches_saved_searches), modifier = Modifier.padding(start = 12.dp))
                }

            items(
                items = history,
                key = SearchQuery::id
            ) { searchQuery ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable(onClick = {
                            onSearch(searchQuery.query.replace("/", "", true))
                        })
                        .fillMaxWidth()
                        .padding(all = 16.dp)
                ) {
                    Spacer(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(20.dp)
                            .paint(
                                painter = timeIconPainter,
                                colorFilter = ColorFilter.tint(colorPalette().textDisabled)
                            )
                    )

                    BasicText(
                        text = searchQuery.query,
                        style = typography().s.secondary,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .weight(1f)
                    )

                    Image(
                        painter = closeIconPainter,
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette().textDisabled),
                        modifier = Modifier
                            .combinedClickable(
                                indication = rippleIndication,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = {
                                    Database.asyncTransaction {
                                        searchTable.delete( searchQuery )
                                    }
                                },
                                onLongClick = {
                                    Database.asyncTransaction {
                                        history.also( searchTable::delete )
                                    }
                                    reloadHistory = !reloadHistory
                                }
                            )
                            .padding(horizontal = 8.dp)
                            .size(20.dp)
                    )

                    Image(
                        painter = painterResource(R.drawable.pencil),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette().textDisabled),
                        modifier = Modifier
                            .clickable(
                                indication = rippleIndication,
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = {
                                    onTextFieldValueChanged(
                                        TextFieldValue(
                                            text = searchQuery.query,
                                            selection = TextRange(searchQuery.query.length)
                                        )
                                    )
                                    coroutineScope.launch {
                                        lazyListState.animateScrollToItem(0)
                                    }
                                }
                            )
                            //.rotate(310f)
                            .padding(horizontal = 8.dp)
                            .size(22.dp)
                    )
                }
            }



        }

        FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)
    }

    LaunchedEffect(Unit) {
        delay(300)
        focusRequester.requestFocus()
    }

}
