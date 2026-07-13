package it.fast4x.rimusic.ui.screens.home

import android.annotation.SuppressLint
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
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
import app.kreate.constant.ArtistSortBy
import app.kreate.constant.SortOrder
import app.kreate.database.Database
import app.kreate.database.models.Song
import app.kreate.di.CacheType
import app.kreate.gateway.innertube.models.InnertubePlaylist
import app.kreate.gateway.innertube.models.InnertubeRankedArtist
import app.kreate.gateway.innertube.models.InnertubeSong
import app.kreate.preferences.Preferences
import app.kreate.util.scrollingText
import co.touchlab.kermit.Logger
import it.fast4x.compose.persist.persist
import it.fast4x.compose.persist.persistList
import it.fast4x.innertube.Innertube
import it.fast4x.innertube.YtMusic
import it.fast4x.innertube.models.bodies.NextBody
import it.fast4x.innertube.requests.HomePage
import it.fast4x.innertube.requests.discoverPage
import it.fast4x.innertube.requests.relatedPage
import it.fast4x.rimusic.LocalPlayerAwareWindowInsets
import it.fast4x.rimusic.colorPalette
import it.fast4x.rimusic.enums.NavRoutes
import it.fast4x.rimusic.enums.NavigationBarPosition
import it.fast4x.rimusic.enums.PlayEventsType
import it.fast4x.rimusic.enums.UiType
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
import it.fast4x.rimusic.utils.asSong
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
    var relatedPage by persist<Innertube.RelatedPage?>(tag = "home/relatedPage")
    var discoverPage by persist<Innertube.DiscoverPage>("home/discoveryAlbums")
    var homePage by persist<HomePage?>("home/homePage")

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
                                    val song = songs.firstOrNull()
                                    if (relatedPage == null || trending?.id != song?.id) {
                                        relatedPage = Innertube.relatedPage(
                                            NextBody(
                                                videoId = (song?.id ?: "HZnNt9nnEhw")
                                            )
                                        )?.getOrNull()
                                    }
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
                                    if (relatedPage == null || trending?.id != song?.id) {
                                        relatedPage =
                                            Innertube.relatedPage(
                                                NextBody(
                                                    videoId = (song?.id ?: "HZnNt9nnEhw")
                                                )
                                            )?.getOrNull()
                                    }
                                    trending = song
                                }
                    }
                }
            }

            if (showNewAlbums || showNewAlbumsArtists || showMoodsAndGenres) {
                discoverPage = Innertube.discoverPage().getOrNull()
            }

            if (isYouTubeLoggedIn())
                homePage = YtMusic.getHomePage().getOrNull()

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
        relatedPage = null
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
    val moodAngGenresLazyGridState = rememberLazyGridState()
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
                            player.stopRadio()
                            trending?.let { player.forcePlay(it.asMediaItem) }
                            player.addMediaItems(relatedPage?.songs?.map { it.asMediaItem }
                                ?: emptyList())
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

                    LazyHorizontalGrid(
                        state = quickPicksLazyGridState,
                        rows = GridCells.Fixed(if (relatedPage != null) 3 else 1),
                        flingBehavior = ScrollableDefaults.flingBehavior(),
                        contentPadding = endPaddingValues,
                        modifier = Modifier.fillMaxWidth()
                                           .height(
                                               if ( relatedPage != null)
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

                        relatedPage?.let { relatedPage ->
                            items(
                                items = relatedPage.songs
                                                   ?.distinctBy( Innertube.SongItem::key )
                                                   ?.filter {
                                                       cachedSongs == null || cachedSongs.indexOf( it.key ) < 0
                                                   }
                                                   ?.dropLast( if( trending == null) 0 else 1 )
                                                   ?.map( Innertube.SongItem::asSong )
                                                   .orEmpty(),
                                key = Song::id
                            ) { song ->
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

                discoverPage?.let { page ->
                    val artists by remember {
                        Database.artistTable
                                .sortFollowing( ArtistSortBy.TITLE, SortOrder.ASCENDING )
                                .distinctUntilChanged()
                    }.collectAsState( emptyList(), Dispatchers.IO )

                    var newReleaseAlbumsFiltered by persistList<Innertube.AlbumItem>("discovery/newalbumsartist")
                    page.newReleaseAlbums.forEach { album ->
                        artists.forEach { artist ->
                            if (artist.name == album.authors?.first()?.name) {
                                newReleaseAlbumsFiltered += album
                            }
                        }
                    }

                    if (showNewAlbumsArtists)
                        if (newReleaseAlbumsFiltered.isNotEmpty() && artists.isNotEmpty()) {

                            BasicText(
                                text = stringResource(R.string.new_albums_of_your_artists),
                                style = typography().l.semiBold,
                                modifier = sectionTextModifier
                            )

                            LazyRow(
                                contentPadding = endPaddingValues,
                                horizontalArrangement = Arrangement.spacedBy(AlbumItem.COLUMN_SPACING.dp )
                            ) {
                                items(
                                    items = newReleaseAlbumsFiltered.distinctBy { it.key },
                                    key = System::identityHashCode
                                ) { album ->
                                    AlbumItem.Vertical( album, albumItemValues, navController )
                                }
                            }

                        }

                    if (showNewAlbums) {
                        Title(
                            title = stringResource(R.string.new_albums),
                            onClick = { NavRoutes.newAlbums.navigateHere( navController ) },
                            //modifier = Modifier.fillMaxWidth(0.7f)
                        )

                        LazyRow(
                            contentPadding = endPaddingValues,
                            horizontalArrangement = Arrangement.spacedBy(AlbumItem.COLUMN_SPACING.dp )
                        ) {
                            items(
                                items = page.newReleaseAlbums.distinctBy { it.key },
                                key = System::identityHashCode
                            ) { album ->
                                AlbumItem.Vertical( album, albumItemValues, navController )
                            }
                        }
                    }
                }

                if (showRelatedAlbums)
                    relatedPage?.albums?.let { albums ->
                        BasicText(
                            text = stringResource(R.string.related_albums),
                            style = typography().l.semiBold,
                            modifier = sectionTextModifier
                        )

                        LazyRow(
                            contentPadding = endPaddingValues,
                            horizontalArrangement = Arrangement.spacedBy(AlbumItem.COLUMN_SPACING.dp )
                        ) {
                            items(
                                items = albums.distinctBy { it.key },
                                key = System::identityHashCode
                            ) { album ->
                                AlbumItem.Vertical( album, albumItemValues, navController )
                            }
                        }
                    }

                if (showSimilarArtists)
                    relatedPage?.artists?.let { artists ->
                        BasicText(
                            text = stringResource(R.string.similar_artists),
                            style = typography().l.semiBold,
                            modifier = sectionTextModifier
                        )

                        LazyRow(
                            contentPadding = endPaddingValues,
                            horizontalArrangement = Arrangement.spacedBy( ArtistItem.COLUMN_SPACING.dp )
                        ) {
                            items(
                                items = artists.distinctBy { it.key },
                                key = Innertube.ArtistItem::key,
                            ) { artist ->
                                ArtistItem.Render(
                                    innertubeArtist = artist,
                                    values = artistItemValues,
                                    navController = navController
                                )
                            }
                        }
                    }

                if (showPlaylistMightLike)
                    relatedPage?.playlists?.let { playlists ->
                        BasicText(
                            text = stringResource(R.string.playlists_you_might_like),
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
                                items = playlists.distinctBy { it.key },
                                key = Innertube.PlaylistItem::key,
                            ) { playlist ->
                                PlaylistItem.Vertical(
                                    innertubePlaylist = playlist,
                                    values = playlistItemValues,
                                    navController = navController
                                )
                            }
                        }
                    }



                if (showMoodsAndGenres)
                    discoverPage?.let { page ->

                        if (page.moods.isNotEmpty()) {

                            Title(
                                title = stringResource(R.string.moods_and_genres),
                                onClick = { NavRoutes.moodsPage.navigateHere( navController ) },
                                //modifier = Modifier.fillMaxWidth(0.7f)
                            )

                            LazyHorizontalGrid(
                                state = moodAngGenresLazyGridState,
                                rows = GridCells.Fixed(4),
                                flingBehavior = ScrollableDefaults.flingBehavior(),
                                //flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
                                contentPadding = endPaddingValues,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    //.height((thumbnailSizeDp + Dimensions.itemsVerticalPadding * 8) * 8)
                                    .height(Dimensions.itemsVerticalPadding * 4 * 8)
                            ) {
                                items(
                                    items = page.moods.sortedBy { it.title },
                                    key = { it.endpoint.params ?: it.title }
                                ) {
                                    MoodItemColored(
                                        mood = it,
                                        onClick = { it.endpoint.browseId?.let { _ -> onMoodClick(it) } },
                                        modifier = Modifier
                                            //.width(itemWidth)
                                            .padding(4.dp)
                                    )
                                }
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
                        if (it.items.isEmpty() || it.items.firstOrNull()?.key == null) return@forEach
                        println("homePage() in HomeYouTubeMusic sections: ${it.title} ${it.items.size}")
                        println("homePage() in HomeYouTubeMusic sections items: ${it.items}")

                        BasicText(
                            text = it.title,
                            style = typography().l.semiBold.color(colorPalette().text),
                            modifier = Modifier.padding(horizontal = 16.dp).padding(vertical = 4.dp)
                        )

                        val currentMediaItem by player.currentMediaItemState.collectAsState()
                        ItemUtils.LazyRowItem(
                            navController = navController,
                            innertubeItems = it.items.fastFilterNotNull(),
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


