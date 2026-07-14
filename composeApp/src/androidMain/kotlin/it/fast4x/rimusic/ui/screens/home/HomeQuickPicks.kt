package it.fast4x.rimusic.ui.screens.home

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastDistinctBy
import androidx.compose.ui.util.fastFilter
import androidx.compose.ui.util.fastFilterNotNull
import androidx.compose.ui.util.fastMapNotNull
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import app.kreate.android.LocalBottomMenu
import app.kreate.android.R
import app.kreate.android.constant.MenuPage
import app.kreate.android.service.player.StatefulPlayer
import app.kreate.android.themed.rimusic.component.album.AlbumItem
import app.kreate.android.themed.rimusic.component.artist.ArtistItem
import app.kreate.android.themed.rimusic.component.playlist.PlaylistItem
import app.kreate.android.themed.rimusic.component.song.SongItem
import app.kreate.android.utils.ItemUtils
import app.kreate.android.utils.innertube.toMediaItem
import app.kreate.android.utils.shallowCompare
import app.kreate.android.viewmodel.home.HomeQuickPicksViewModel
import app.kreate.database.Database
import app.kreate.database.models.Song
import app.kreate.di.CacheType
import app.kreate.gateway.innertube.models.InnertubeAlbum
import app.kreate.gateway.innertube.models.InnertubeArtist
import app.kreate.gateway.innertube.models.InnertubeItem
import app.kreate.gateway.innertube.models.InnertubePlaylist
import app.kreate.gateway.innertube.models.InnertubeRankedArtist
import app.kreate.gateway.innertube.models.InnertubeSong
import app.kreate.preferences.Preferences
import app.kreate.util.scrollingText
import co.touchlab.kermit.Logger
import it.fast4x.compose.persist.persist
import it.fast4x.innertube.Innertube
import it.fast4x.rimusic.LocalPlayerAwareWindowInsets
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.enums.PlayEventsType
import it.fast4x.rimusic.enums.UiType
import it.fast4x.rimusic.models.Mood
import it.fast4x.rimusic.service.MyDownloadHelper
import it.fast4x.rimusic.typography
import it.fast4x.rimusic.ui.components.LocalMenuState
import it.fast4x.rimusic.ui.components.themed.HeaderWithIcon
import it.fast4x.rimusic.ui.components.themed.Loader
import it.fast4x.rimusic.ui.components.themed.Menu
import it.fast4x.rimusic.ui.components.themed.MenuEntry
import it.fast4x.rimusic.ui.components.themed.MultiFloatingActionsContainer
import it.fast4x.rimusic.ui.components.themed.Title
import it.fast4x.rimusic.ui.components.themed.Title2Actions
import it.fast4x.rimusic.ui.screens.settings.isYouTubeLoggedIn
import it.fast4x.rimusic.ui.styling.Dimensions
import it.fast4x.rimusic.ui.styling.LocalAppearance
import it.fast4x.rimusic.utils.WelcomeMessage
import it.fast4x.rimusic.utils.asMediaItem
import it.fast4x.rimusic.utils.bold
import it.fast4x.rimusic.utils.center
import it.fast4x.rimusic.utils.color
import it.fast4x.rimusic.utils.forcePlay
import it.fast4x.rimusic.utils.isLandscape
import it.fast4x.rimusic.utils.secondary
import it.fast4x.rimusic.utils.semiBold
import it.fast4x.rimusic.utils.shimmerEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kreate.resources.generated.resources.Res
import kreate.resources.generated.resources.by_casual_played_song
import kreate.resources.generated.resources.by_last_played_song
import kreate.resources.generated.resources.by_most_played_song
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds


@Composable
private fun MoodCard(
    title: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val thumbnailRoundness by Preferences.THUMBNAIL_BORDER_RADIUS.collectAsStateWithLifecycle()
    val (colorPalette, typography) = LocalAppearance.current

    Column (
        verticalArrangement = Arrangement.SpaceAround,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth()
                           .padding( 5.dp )
                           .clip( thumbnailRoundness.shape )
                           .clickable { onClick() }

    ) {
        Row(
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.background( color = color )
                               .padding( start = 10.dp )
                               .fillMaxHeight( 0.9f )
        ) {
            Box(
                Modifier.requiredWidth( 150.dp )
                        .background( color = colorPalette.background4 )
                        .fillMaxSize()
            ) {

                Text(
                    text = title,
                    maxLines = 2,
                    color = colorPalette.text,
                    fontWeight = typography.xs.semiBold.fontWeight,
                    fontStyle = typography.xs.semiBold.fontStyle,
                    modifier = Modifier.padding( horizontal = 10.dp )
                                       .align( Alignment.CenterStart )
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)
@ExperimentalTextApi
@SuppressLint("SuspiciousIndentation")
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun HomeQuickPicks(
    navController: NavController,
    onSearchClick: () -> Unit,
    onMoodClick: (mood: Innertube.Mood.Item) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: HomeQuickPicksViewModel = koinViewModel()
) {
    val hapticFeedback = LocalHapticFeedback.current
    val player: StatefulPlayer = koinInject()
    val (colorPalette, typography) = LocalAppearance.current
    val menuState = LocalMenuState.current
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val bottomMenu = LocalBottomMenu.current
    val playEventType by Preferences.QUICK_PICKS_TYPE.collectAsStateWithLifecycle()

    var trending by persist<Song?>("home/trending")
    val relatedPage by viewModel.relatedPage.collectAsStateWithLifecycle()
    val explorePage by viewModel.explorePage.collectAsStateWithLifecycle()
    val homePage by viewModel.homePage.collectAsStateWithLifecycle()

    val showRelatedAlbums by Preferences.QUICK_PICKS_SHOW_RELATED_ALBUMS.collectAsStateWithLifecycle()
    val showSimilarArtists by Preferences.QUICK_PICKS_SHOW_RELATED_ARTISTS.collectAsStateWithLifecycle()
    val showNewAlbumsArtists by Preferences.QUICK_PICKS_SHOW_NEW_ALBUMS_ARTISTS.collectAsStateWithLifecycle()
    val showPlaylistMightLike by Preferences.QUICK_PICKS_SHOW_MIGHT_LIKE_PLAYLISTS.collectAsStateWithLifecycle()
    val showMoodsAndGenres by Preferences.QUICK_PICKS_SHOW_MOODS_AND_GENRES.collectAsStateWithLifecycle()
    val showNewAlbums by Preferences.QUICK_PICKS_SHOW_NEW_ALBUMS.collectAsStateWithLifecycle()
    val showMonthlyPlaylistInQuickPicks by Preferences.QUICK_PICKS_SHOW_MONTHLY_PLAYLISTS.collectAsStateWithLifecycle()
    val showTips by Preferences.QUICK_PICKS_SHOW_TIPS.collectAsStateWithLifecycle()

    val refreshScope = rememberCoroutineScope()
    val last50Year: Duration = 18250.days
    val from = last50Year.inWholeMilliseconds

    val countryCode by Preferences.APP_REGION.collectAsStateWithLifecycle()

    val parentalControlEnabled by Preferences.PARENTAL_CONTROL.collectAsStateWithLifecycle()

    suspend fun loadData() {
        runCatching {
            refreshScope.launch(Dispatchers.IO) {
                when (playEventType) {
                    PlayEventsType.MostPlayed ->
                        Database.eventTable
                                .findSongsMostPlayedBetween(
                                    from = from,
                                    limit = 1
                                )
                                .distinctUntilChanged()
                                .collect { songs ->
                                    val song = songs.firstOrNull() ?: return@collect
                                    viewModel.loadRelatedSong( song.id )
                                    trending = song
                                }

                    PlayEventsType.LastPlayed, PlayEventsType.CasualPlayed -> {
                        val numSongs = if (playEventType == PlayEventsType.LastPlayed) 3 else 100
                        Database.eventTable
                                .findSongsMostPlayedBetween(
                                    from = 0,
                                    limit = numSongs
                                )
                                .distinctUntilChanged()
                                .collect { songs ->
                                    val song =
                                        if (playEventType == PlayEventsType.LastPlayed)
                                            songs.firstOrNull()
                                        else
                                            songs.shuffled().firstOrNull()
                                    song ?: return@collect
                                    viewModel.loadRelatedSong( song.id )
                                    trending = song
                                }
                    }
                }
            }

            if (showNewAlbums || showNewAlbumsArtists || showMoodsAndGenres)
                viewModel.loadExplorePage()

            if ( isYouTubeLoggedIn() )
                viewModel.loadHomePage()

        }.onFailure {
            Logger.e( tag = "HomeQuickPicks" ) { "loadData failed!" }
        }
    }

    LaunchedEffect( Unit, playEventType, countryCode ) {
        loadData()
    }

    var refreshing by remember { mutableStateOf(false) }

    fun refresh() {
        if (refreshing) return
        trending = null
        refreshScope.launch(Dispatchers.IO) {
            refreshing = true
            loadData()
            delay(500)
            refreshing = false
        }
    }

    val scrollState = rememberScrollState()
    val quickPicksLazyGridState = rememberLazyGridState()
    val chartsPageSongLazyGridState = rememberLazyGridState()
    val chartsPageArtistLazyGridState = rememberLazyGridState()

    val endPaddingValues = windowInsets.only(WindowInsetsSides.End).asPaddingValues()

    val sectionTextModifier = Modifier
        .padding(horizontal = 16.dp)
        .padding(top = 24.dp, bottom = 8.dp)
        .padding(endPaddingValues)

    val showSearchTab by Preferences.SHOW_SEARCH_IN_NAVIGATION_BAR.collectAsStateWithLifecycle()

    val cache: Cache = koinInject(CacheType.CACHE)
    var cachedSongs by remember { mutableStateOf( emptyList<String>() ) }
    // FIXME: This practically run once on start
    LaunchedEffect( cache ) {
        val keys = try {
            cache.keys
        } catch ( _: IllegalStateException ) {
            // Sometimes this block runs before SimpleCache
            // finishes it's init, it'll throw IllegalStateException
            // if the process is running. To avoid, small delay is added
            delay( 1.seconds )

            cache.keys
        }.toMutableSet()

        MyDownloadHelper.instance
                        .downloads
                        .value
                        .filter {
                            it.value.state == Download.STATE_COMPLETED
                        }
                        .keys
                        .also { keys.addAll(it) }

        cachedSongs = keys.toList()
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = ::refresh
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth(
                    if (NavigationBarPosition.Right.isCurrent())
                        Dimensions.contentWidthRightBar
                    else
                        1f
                )

        ) {
            val quickPicksLazyGridItemWidthFactor =
                if (isLandscape && maxWidth * 0.475f >= 320.dp) {
                    0.475f
                } else {
                    0.9f
                }
            val itemInHorizontalGridWidth = maxWidth * quickPicksLazyGridItemWidthFactor

            val moodItemWidthFactor =
                if (isLandscape && maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
            val itemWidth = maxWidth * moodItemWidthFactor

            Column(
                modifier = Modifier
                    .background(colorPalette().background0)
                    .fillMaxHeight()
                    .verticalScroll(scrollState)
            ) {
                if (UiType.ViMusic.isCurrent())
                    HeaderWithIcon(
                        title = if (!isYouTubeLoggedIn()) stringResource(R.string.quick_picks)
                        else stringResource(R.string.home),
                        iconId = R.drawable.search,
                        enabled = true,
                        showIcon = !showSearchTab,
                        modifier = Modifier,
                        onClick = onSearchClick
                    )

                WelcomeMessage()

                if (showTips) {
                    Title2Actions(
                        title = stringResource(R.string.tips),
                        onClick1 = {
                            menuState.display {
                                Menu {
                                    MenuEntry(
                                        icon = R.drawable.chevron_up,
                                        text = stringResource( Res.string.by_most_played_song ),
                                        onClick = {
                                            Preferences.QUICK_PICKS_TYPE.update( PlayEventsType.MostPlayed )
                                            menuState.hide()
                                        }
                                    )
                                    MenuEntry(
                                        icon = R.drawable.chevron_down,
                                        text = stringResource( Res.string.by_last_played_song ),
                                        onClick = {
                                            Preferences.QUICK_PICKS_TYPE.update( PlayEventsType.LastPlayed )
                                            menuState.hide()
                                        }
                                    )
                                    MenuEntry(
                                        icon = R.drawable.random,
                                        text = stringResource( Res.string.by_casual_played_song ),
                                        onClick = {
                                            Preferences.QUICK_PICKS_TYPE.update( PlayEventsType.CasualPlayed )
                                            menuState.hide()
                                        }
                                    )
                                }
                            }
                        },
                        icon2 = R.drawable.play,
                        onClick2 = {
                            trending ?: return@Title2Actions
                            viewModel.playAll( trending!! )
                        }

                        //modifier = Modifier.fillMaxWidth(0.7f)
                    )

                    BasicText(
                        text = playEventType.text,
                        style = typography().xxs.secondary,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 8.dp)
                    )

                    val currentMediaItem by player.currentMediaItemState.collectAsState()
                    val songItemValues = remember( colorPalette, typography ) {
                        SongItem.Values.from( colorPalette, typography )
                    }

                    var relatedSongs by remember { mutableStateOf(emptyList<InnertubeSong>()) }
                    LaunchedEffect( relatedPage ) {
                        relatedPage
                            ?.sections
                            ?.flatMap { it.contents }
                            ?.filterIsInstance<InnertubeSong>()
                            ?.filterNot { cachedSongs.contains(it.id) }
                            ?.also { relatedSongs = it }
                    }

                    LazyHorizontalGrid(
                        state = quickPicksLazyGridState,
                        rows = GridCells.Fixed(if (relatedPage != null) 3 else 1),
                        flingBehavior = ScrollableDefaults.flingBehavior(),
                        contentPadding = endPaddingValues,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(
                                if (relatedPage != null)
                                    Dimensions.itemsVerticalPadding * 3 * 9
                                else
                                    Dimensions.itemsVerticalPadding * 9
                            )
                    ) {
                        trending?.let { song ->
                            item {
                                SongItem.Render(
                                    song = song,
                                    hapticFeedback = hapticFeedback,
                                    isPlaying = song.shallowCompare( currentMediaItem ),
                                    values = songItemValues,
                                    modifier = Modifier.width( itemInHorizontalGridWidth ),
                                    onLongClick = {
                                        val page = MenuPage.Song(song.asMediaItem)
                                        bottomMenu.show( page, true )
                                    }
                                ) {
                                    player.startRadio( song, true )
                                }
                            }
                        }

                        items(
                            items = relatedSongs,
                            key = InnertubeSong::id
                        ) { song ->
                            val mediaItem = song.toMediaItem

                            SongItem.Render(
                                innertubeSong = song,
                                hapticFeedback = hapticFeedback,
                                isPlaying = song.shallowCompare( currentMediaItem ),
                                values = songItemValues,
                                modifier = Modifier.width( itemInHorizontalGridWidth ),
                                onLongClick = {
                                    val page = MenuPage.Song(mediaItem)
                                    bottomMenu.show( page, true )
                                }
                            ) {
                                player.startRadio( mediaItem, true )
                            }
                        }
                    }

                    if (relatedPage == null) Loader()
                }

                val albumItemValues = remember(  colorPalette, typography  ) {
                    AlbumItem.Values.from(  colorPalette, typography  )
                }
                val artistItemValues = remember( colorPalette, typography ) {
                    ArtistItem.Values.from( colorPalette, typography )
                }
                val playlistItemValues = remember( colorPalette, typography ) {
                    PlaylistItem.Values.from( colorPalette, typography )
                }

                if( showNewAlbumsArtists ) {
                    val newAlbumsFromFollowingArtists by viewModel.albumsFromArtists.collectAsStateWithLifecycle()
                    if( newAlbumsFromFollowingArtists.isNotEmpty() ) {
                        BasicText(
                            text = stringResource( R.string.new_albums_of_your_artists ),
                            style = typography().l.semiBold,
                            modifier = sectionTextModifier
                        )

                        LazyRow(
                            contentPadding = endPaddingValues,
                            horizontalArrangement = Arrangement.spacedBy( AlbumItem.COLUMN_SPACING.dp )
                        ) {
                            items(
                                items = newAlbumsFromFollowingArtists,
                                key = InnertubeItem::id
                            ) { album ->
                                AlbumItem.Vertical( album, albumItemValues, navController )
                            }
                        }
                    }
                }

                if( explorePage?.newAlbumsAndSingles != null && showNewAlbums ) {
                    val section = explorePage?.newAlbumsAndSingles!!

                    Title(
                        title = section.title.orEmpty(),
                        onClick = { NavRoutes.newAlbums.navigateHere( navController ) }
                    )

                    val albums by remember { derivedStateOf {
                        section.contents.mapNotNull { it as? InnertubeAlbum }
                    } }
                    LazyRow(
                        contentPadding = endPaddingValues,
                        horizontalArrangement = Arrangement.spacedBy(AlbumItem.COLUMN_SPACING.dp )
                    ) {
                        items(
                            items = albums,
                            key = InnertubeItem::id
                        ) { album ->
                            AlbumItem.Vertical( album, albumItemValues, navController )
                        }
                    }
                }

                relatedPage?.sections?.forEach { section ->
                    val canBeShown =
                        section.contents.all { it is InnertubeAlbum } && showRelatedAlbums
                                || section.contents.all { it is InnertubeArtist } && showSimilarArtists
                                || section.contents.all { it is InnertubePlaylist } && showPlaylistMightLike
                    if( !canBeShown ) return@forEach

                    BasicText(
                        text = section.title.orEmpty(),
                        style = typography().l.semiBold,
                        modifier = Modifier.padding( 16.dp, 24.dp, 16.dp, 8.dp )
                    )

                    LazyRow(
                        contentPadding = endPaddingValues,
                        horizontalArrangement = Arrangement.spacedBy( PlaylistItem.COLUMN_SPACING.dp )
                    ) {
                        items(
                            items = section.contents,
                            key = InnertubeItem::id,
                        ) { item ->
                            when( item ) {
                                is InnertubeAlbum -> {
                                    AlbumItem.Vertical(
                                        innertubeAlbum = item,
                                        values = albumItemValues,
                                        navController = navController
                                    )
                                }

                                is InnertubeArtist -> {
                                    ArtistItem.Render(
                                        innertubeArtist = item,
                                        values = artistItemValues,
                                        navController = navController
                                    )
                                }

                                is InnertubePlaylist -> {
                                    PlaylistItem.Vertical(
                                        innertubePlaylist = item,
                                        values = playlistItemValues,
                                        navController = navController
                                    )
                                }
                            }
                        }
                    }
                }

                if( explorePage?.moodsAndGenres != null && showMoodsAndGenres ) {
                    val section = explorePage?.moodsAndGenres!!

                    Title(
                        title = section.title.orEmpty(),
                        onClick = { NavRoutes.moodsPage.navigateHere( navController ) }
                    )

                    LazyHorizontalGrid(
                        rows = GridCells.Fixed(4),
                        contentPadding = endPaddingValues,
                        modifier = Modifier.fillMaxWidth().height( Dimensions.itemsVerticalPadding * 4 * 8 )
                    ) {
                        items(
                            items = section.contents
                        ) { card ->
                            val name = card.title.joinToString()
                            val stripeColor = Color(card.color)

                            MoodCard(
                                title = name,
                                color = stripeColor,
                                modifier = Modifier.padding(4.dp),
                                onClick = {
                                    val uiMood = Mood(name, stripeColor, card.endpoint.browseId, card.endpoint.params)
                                    navController.currentBackStackEntry?.savedStateHandle?.set("mood", uiMood)
                                    NavRoutes.mood.navigateHere( navController )
                                }
                            )
                        }
                    }
                }

                val monthlyPlaylists by remember {
                    Database.playlistTable
                            .allAsPreview()
                            .distinctUntilChanged()
                            .map { list ->
                                list.filter { it.playlist.isMonthly }
                            }
                }.collectAsState( emptyList(), Dispatchers.IO )

                if (showMonthlyPlaylistInQuickPicks)
                    monthlyPlaylists.let { playlists ->
                        if (playlists.isNotEmpty()) {
                            BasicText(
                                text = stringResource(R.string.monthly_playlists),
                                style = typography().l.semiBold,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 24.dp, bottom = 8.dp)
                            )

                            LazyRow(
                                contentPadding = endPaddingValues,
                                horizontalArrangement = Arrangement.spacedBy( PlaylistItem.COLUMN_SPACING.dp )
                            ) {
                                items(
                                    items = playlists.distinctBy { it.playlist.id },
                                    key = { it.playlist.id }
                                ) { preview ->
                                    PlaylistItem.Vertical(
                                        playlist = preview.playlist,
                                        values = playlistItemValues,
                                        showSongCount = false,
                                        navController = navController
                                    )
                                }
                            }
                        }
                    }

                val showCharts by Preferences.QUICK_PICKS_SHOW_CHARTS.collectAsStateWithLifecycle()
                if( showCharts ) {
                    val charts by viewModel.charts.collectAsStateWithLifecycle()
                    LaunchedEffect( refreshing, countryCode ) {
                        viewModel.loadCharts()
                    }

                    charts?.run {
                        Title(
                            title = "${stringResource(R.string.charts)} ($selectedCountryName)",
                            onClick = {
                                menuState.display {
                                    Menu {
                                        menu.items.forEach { item ->
                                            MenuEntry(
                                                icon = R.drawable.arrow_right,
                                                text = item.countryDisplayName,
                                                onClick = {
                                                    Preferences.APP_REGION.update( item.countryCode )
                                                    menuState.hide()
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        )

                        sections.forEach { section ->
                            // Don't show section if the title is null or blank
                            if( section.title.isNullOrBlank() ) return@forEach

                            BasicText(
                                text = section.title!!,
                                style = typography().l.semiBold,
                                modifier = Modifier
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 24.dp, bottom = 8.dp)
                            )

                            if( section.contents.all{ it is InnertubePlaylist } )
                                section.contents
                                    .fastMapNotNull { it as? InnertubePlaylist }
                                    .also {
                                        val playlistItemValues = remember( colorPalette, typography ) {
                                            PlaylistItem.Values.from( colorPalette, typography )
                                        }

                                        LazyRow(
                                            contentPadding = endPaddingValues,
                                            modifier = Modifier.wrapContentHeight(),
                                            horizontalArrangement = Arrangement.spacedBy( PlaylistItem.COLUMN_SPACING.dp )
                                        ) {
                                            items(
                                                items = it,
                                                key = InnertubePlaylist::hashCode
                                            ) { playlist ->
                                                PlaylistItem.Vertical(
                                                    innertubePlaylist = playlist,
                                                    values = playlistItemValues,
                                                    navController = navController
                                                )
                                            }
                                        }
                                    }
                            else if( section.contents.all{ it is InnertubeSong } )
                                section.contents
                                    .fastMapNotNull { it as? InnertubeSong }
                                    .also { songs ->
                                        val currentMediaItem by player.currentMediaItemState.collectAsState()
                                        val songItemValues = remember( colorPalette, typography ) {
                                            SongItem.Values.from( colorPalette, typography )
                                        }

                                        LazyHorizontalGrid(
                                            rows = GridCells.Fixed(2),
                                            modifier = Modifier
                                                .height(130.dp)
                                                .fillMaxWidth(),
                                            state = chartsPageSongLazyGridState,
                                            flingBehavior = ScrollableDefaults.flingBehavior()
                                        ) {
                                            itemsIndexed(
                                                items = songs.fastFilter { !parentalControlEnabled || !it.isExplicit }
                                                             .fastDistinctBy(InnertubeSong::id ),
                                                key = { i, s -> "${System.identityHashCode(s)}-$i"}
                                            ) { index, song ->
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(start = 16.dp)
                                                ) {
                                                    BasicText(
                                                        text = "${index + 1}",
                                                        style = typography().l.bold.center.color(
                                                            colorPalette().text
                                                        ),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    SongItem.Render(
                                                        innertubeSong = song,
                                                        hapticFeedback = hapticFeedback,
                                                        values = songItemValues,
                                                        isPlaying = song.shallowCompare( currentMediaItem ),
                                                        onClick = {
                                                            val mediaItem = song.toMediaItem
                                                            player.stopRadio()
                                                            player.forcePlay(mediaItem)
                                                            player.addMediaItems(songs.map { it.toMediaItem })
                                                        },
                                                        onLongClick = {
                                                            val page = MenuPage.Song(song.toMediaItem)
                                                            bottomMenu.show( page, true )
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                            else if( section.contents.all{ it is InnertubeRankedArtist } )
                                section.contents
                                    .fastMapNotNull { it as? InnertubeRankedArtist }
                                    .also { rankedArtists ->
                                        LazyHorizontalGrid(
                                            rows = GridCells.Fixed(2),
                                            modifier = Modifier
                                                .height(130.dp)
                                                .fillMaxWidth(),
                                            state = chartsPageArtistLazyGridState,
                                            flingBehavior = ScrollableDefaults.flingBehavior(),
                                        ) {
                                            itemsIndexed(
                                                items = rankedArtists.distinctBy( InnertubeRankedArtist::id ),
                                                key = { i, s -> "${System.identityHashCode(s)}-$i"}
                                            ) { index, artist ->
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy( 10.dp ),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding( start = 16.dp )
                                                                       .requiredHeight( ArtistItem.thumbnailSize().height )
                                                ) {
                                                    BasicText(
                                                        text = artist.rank,
                                                        style = typography().l.bold.center.color(
                                                            colorPalette().text
                                                        ),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )

                                                    ArtistItem.Thumbnail(
                                                        artistId = artist.id,
                                                        thumbnailUrl = artist.thumbnails.firstOrNull()?.url,
                                                        showPlatformIcon = false,
                                                        sizeDp = DpSize(Dimensions.thumbnails.song, Dimensions.thumbnails.song)
                                                    )

                                                    Column(
                                                        verticalArrangement = Arrangement.Center,
                                                        modifier = Modifier.fillMaxHeight()
                                                    ) {
                                                        ArtistItem.Title(
                                                            title = artist.name,
                                                            values = artistItemValues,
                                                            textAlign = TextAlign.Start
                                                        )

                                                        artist.shortNumSubscribers?.let { subscribers ->
                                                            Text(
                                                                text = subscribers,
                                                                style = typography.xs,
                                                                color = colorPalette.textSecondary,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Clip,
                                                                textAlign = TextAlign.Start,
                                                                modifier = Modifier.scrollingText()
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                        }
                    }

                }

                homePage?.let { page ->

                    page.sections.forEach {
                        if (it.contents.isEmpty() || it.contents.firstOrNull()?.id == null) return@forEach

                        BasicText(
                            text = it.title.orEmpty(),
                            style = typography().l.semiBold.color(colorPalette().text),
                            modifier = Modifier.padding(horizontal = 16.dp).padding(vertical = 4.dp)
                        )

                        val currentMediaItem by player.currentMediaItemState.collectAsState()
                        ItemUtils.LazyRowItem(
                            navController = navController,
                            innertubeItems = it.contents.fastFilterNotNull(),
                            currentlyPlaying = currentMediaItem?.mediaId
                        )
                    }
                } ?: if (!isYouTubeLoggedIn()) BasicText(
                    text = stringResource(R.string.log_in_to_ytm),
                    style = typography().xs.center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(vertical = 32.dp)
                        .fillMaxWidth()
                        .clickable {
                            NavRoutes.settings.navigateHere( navController )
                        }
                ) else {
                    repeat(3) {
                        SongItem.Placeholder()
                    }

                    repeat( 2 ) {
                        Text(
                            text = "",
                            style = typography.l.semiBold,
                            modifier = Modifier.padding( 16.dp, 24.dp, 16.dp, 8.dp )
                                               .fillMaxWidth( .45f )
                                               .shimmerEffect()
                        )

                        ItemUtils.PlaceholderRowItem {
                            AlbumItem.VerticalPlaceholder()
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))

                //} ?:

                relatedPage?.let {
                    BasicText(
                        text = stringResource(R.string.page_not_been_loaded),
                        style = typography().s.secondary.center,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(all = 16.dp)
                    )
                }

                /*
                if (related == null)
                    ShimmerHost {
                        repeat(3) {
                            SongItemPlaceholder(
                                thumbnailSizeDp = songThumbnailSizeDp,
                            )
                        }

                        TextPlaceholder(modifier = sectionTextModifier)

                        Row {
                            repeat(2) {
                                AlbumItemPlaceholder(
                                    thumbnailSizeDp = albumThumbnailSizeDp,
                                    alternative = true
                                )
                            }
                        }

                        TextPlaceholder(modifier = sectionTextModifier)

                        Row {
                            repeat(2) {
                                ArtistItemPlaceholder(
                                    thumbnailSizeDp = albumThumbnailSizeDp,
                                    alternative = true
                                )
                            }
                        }

                        TextPlaceholder(modifier = sectionTextModifier)

                        Row {
                            repeat(2) {
                                PlaylistItemPlaceholder(
                                    thumbnailSizeDp = albumThumbnailSizeDp,
                                    alternative = true
                                )
                            }
                        }
                    }
                 */


            }


            val showFloatingIcon by Preferences.SHOW_FLOATING_ICON.collectAsStateWithLifecycle()
            if (UiType.ViMusic.isCurrent() && showFloatingIcon)
                MultiFloatingActionsContainer(
                    iconId = R.drawable.search,
                    onClick = onSearchClick,
                    onClickSettings = onSettingsClick,
                    onClickSearch = onSearchClick
                )

        }

    }
}


