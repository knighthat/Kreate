/*
 * Kreate
 * Copyright (C) 2026 KnightHat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.knighthat.kreate3.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.flow.first
import me.knighthat.kreate3.constant.ThemeMode
import me.knighthat.kreate3.download.SongDownloads
import me.knighthat.kreate3.notification.Notifier
import me.knighthat.kreate3.playback.PlaybackController
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.ui.component.LocalIconOnly
import me.knighthat.kreate3.ui.component.LocalMessageHost
import me.knighthat.kreate3.ui.component.LocalNotifier
import me.knighthat.kreate3.ui.component.LocalPlaylistInitial
import me.knighthat.kreate3.ui.component.LocalSongMenuHost
import me.knighthat.kreate3.ui.component.LocalSongStorage
import me.knighthat.kreate3.ui.component.MessageHost
import me.knighthat.kreate3.ui.component.MiniPlayer
import me.knighthat.kreate3.ui.component.PlaybackMiniPlayer
import me.knighthat.kreate3.ui.component.ShowMessages
import me.knighthat.kreate3.ui.component.SongMenuHost
import me.knighthat.kreate3.ui.component.SongMenuOverlay
import me.knighthat.kreate3.ui.component.TabBar
import me.knighthat.kreate3.ui.component.hasMiniPlayer
import me.knighthat.kreate3.ui.component.rememberMiniPlayerSpace
import me.knighthat.kreate3.ui.navigation.Albums
import me.knighthat.kreate3.ui.navigation.Artists
import me.knighthat.kreate3.ui.navigation.BackupSettings
import me.knighthat.kreate3.ui.navigation.ExitConfirmation
import me.knighthat.kreate3.ui.navigation.GeneralSettings
import me.knighthat.kreate3.ui.navigation.LocalAlbum
import me.knighthat.kreate3.ui.navigation.LocalArtist
import me.knighthat.kreate3.ui.navigation.LocalNavigator
import me.knighthat.kreate3.ui.navigation.LocalPlaylist
import me.knighthat.kreate3.ui.navigation.Navigator
import me.knighthat.kreate3.ui.navigation.NetworkSettings
import me.knighthat.kreate3.ui.navigation.OpenSubsonicAlbum
import me.knighthat.kreate3.ui.navigation.OpenSubsonicArtist
import me.knighthat.kreate3.ui.navigation.OpenSubsonicArtistSongs
import me.knighthat.kreate3.ui.navigation.OpenSubsonicPlaylist
import me.knighthat.kreate3.ui.navigation.OpenSubsonicSettings
import me.knighthat.kreate3.ui.navigation.PlayerSettings
import me.knighthat.kreate3.ui.navigation.Playlists
import me.knighthat.kreate3.ui.navigation.Route
import me.knighthat.kreate3.ui.navigation.Search
import me.knighthat.kreate3.ui.navigation.SearchResults
import me.knighthat.kreate3.ui.navigation.Settings
import me.knighthat.kreate3.ui.navigation.Songs
import me.knighthat.kreate3.ui.navigation.Tab
import me.knighthat.kreate3.ui.navigation.UiSettings
import me.knighthat.kreate3.ui.navigation.YouTubeAlbum
import me.knighthat.kreate3.ui.navigation.YouTubeArtist
import me.knighthat.kreate3.ui.navigation.YouTubeMore
import me.knighthat.kreate3.ui.navigation.YouTubePlaylist
import me.knighthat.kreate3.ui.navigation.YouTubeSettings
import me.knighthat.kreate3.ui.navigation.YouTubeSignIn
import me.knighthat.kreate3.ui.navigation.albumRoute
import me.knighthat.kreate3.ui.navigation.artistRoute
import me.knighthat.kreate3.ui.navigation.itemRoute
import me.knighthat.kreate3.ui.navigation.playlistRoute
import me.knighthat.kreate3.ui.navigation.rememberTopLevelBackStack
import me.knighthat.kreate3.ui.navigation.routeViewModel
import me.knighthat.kreate3.ui.navigation.youTubeMoreRoute
import me.knighthat.kreate3.ui.screen.detail.LocalAlbumDetailScreen
import me.knighthat.kreate3.ui.screen.detail.LocalArtistDetailScreen
import me.knighthat.kreate3.ui.screen.detail.LocalPlaylistDetailScreen
import me.knighthat.kreate3.ui.screen.detail.OpenSubsonicAlbumDetailScreen
import me.knighthat.kreate3.ui.screen.detail.OpenSubsonicArtistDetailScreen
import me.knighthat.kreate3.ui.screen.detail.OpenSubsonicArtistSongsScreen
import me.knighthat.kreate3.ui.screen.detail.OpenSubsonicPlaylistDetailScreen
import me.knighthat.kreate3.ui.screen.detail.YouTubeAlbumDetailScreen
import me.knighthat.kreate3.ui.screen.detail.YouTubeArtistDetailScreen
import me.knighthat.kreate3.ui.screen.detail.YouTubeMoreScreen
import me.knighthat.kreate3.ui.screen.detail.YouTubePlaylistDetailScreen
import me.knighthat.kreate3.ui.screen.home.HomeAlbumsScreen
import me.knighthat.kreate3.ui.screen.home.HomeArtistsScreen
import me.knighthat.kreate3.ui.screen.home.HomePlaylistsScreen
import me.knighthat.kreate3.ui.screen.home.HomeSongsScreen
import me.knighthat.kreate3.ui.screen.player.LocalPlayerSheet
import me.knighthat.kreate3.ui.screen.player.PlayerSheet
import me.knighthat.kreate3.ui.screen.player.PlayerSheetValue
import me.knighthat.kreate3.ui.screen.player.rememberPlayerSheetState
import me.knighthat.kreate3.ui.screen.search.SearchResultsScreen
import me.knighthat.kreate3.ui.screen.search.SearchScreen
import me.knighthat.kreate3.ui.screen.settings.SettingsScreen
import me.knighthat.kreate3.ui.screen.settings.backup.BackupSettingsScreen
import me.knighthat.kreate3.ui.screen.settings.general.GeneralSettingsScreen
import me.knighthat.kreate3.ui.screen.settings.network.NetworkSettingsScreen
import me.knighthat.kreate3.ui.screen.settings.network.opensubsonic.OpenSubsonicSettingsScreen
import me.knighthat.kreate3.ui.screen.settings.network.youtube.YouTubeSettingsScreen
import me.knighthat.kreate3.ui.screen.settings.network.youtube.YouTubeSignInScreen
import me.knighthat.kreate3.ui.screen.settings.player.PlayerSettingsScreen
import me.knighthat.kreate3.ui.screen.settings.ui.UiSettingsScreen
import me.knighthat.kreate3.ui.theme.KreateTheme
import me.knighthat.kreate3.ui.theme.rememberThumbnailSeed
import org.koin.compose.koinInject
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectFlowAsState

/**
 * Whether the screen gets the [MiniPlayer] at the bottom of the window while something plays.
 * Settings pages don't, they aren't about the music. Search places it above its search bar, and
 * search results above their tabs.
 */
private val Route.hasMiniPlayer: Boolean
    get() = when (this) {
        Settings, GeneralSettings, PlayerSettings, UiSettings, NetworkSettings, YouTubeSettings,
        OpenSubsonicSettings, BackupSettings, YouTubeSignIn, Search -> false

        is SearchResults -> false
        else -> true
    }

/**
 * Whether the app's scaffold shows the [Notifier]'s messages over the screen. Search and its
 * results show them above their own bottom bar, see [MessageHost].
 */
private val Route.showsMessages: Boolean
    get() = this != Search && this !is SearchResults

/**
 * @param openPlayer whether the full player was asked to open, like by a tap on the media
 * notification, unless [Preferences.NOTIFICATION_OPENS_PLAYER] is off; [onPlayerOpened] is called
 * once it is open or the request is dropped
 */
@Composable
fun KreateApp(openPlayer: Boolean = false, onPlayerOpened: () -> Unit = {}) {
    val themeMode by Preferences.THEME_MODE.collectAsStateWithLifecycle()
    val contrastLevel by Preferences.CONTRAST_LEVEL.collectAsStateWithLifecycle()
    val iconOnly by Preferences.ICON_ONLY.collectAsStateWithLifecycle()
    val playlistInitial by Preferences.PLAYLIST_INITIAL.collectAsStateWithLifecycle()
    val songStorage by koinInject<SongDownloads>().songs.collectFlowAsState()

    val topLevel = rememberTopLevelBackStack()
    val playback = koinInject<PlaybackController>()
    val playing by playback.state.collectFlowAsState()
    val seed = rememberThumbnailSeed(playing.current, themeMode == ThemeMode.THUMBNAIL)

    val notifier = koinInject<Notifier>()
    val messages = remember { SnackbarHostState() }
    ShowMessages(notifier, messages)

    val playerSheet = rememberPlayerSheetState()
    val miniPlayer = rememberMiniPlayerSpace()
    LaunchedEffect(openPlayer) {
        if (!openPlayer) return@LaunchedEffect
        Preferences.awaitLoaded()
        if (!Preferences.NOTIFICATION_OPENS_PLAYER.value) {
            onPlayerOpened()
            return@LaunchedEffect
        }
        // On a cold start the queue and the player's size come after the request
        snapshotFlow {
            val anchors = playerSheet.draggable.anchors
            playing.hasMiniPlayer && !anchors.positionOf(PlayerSheetValue.COLLAPSED).isNaN()
        }.first { it }
        playerSheet.expand()
        onPlayerOpened()
    }
    val currentRoute = topLevel.backStack.lastOrNull() as? Route
    val navigator = remember(topLevel, currentRoute) { Navigator(currentRoute, topLevel::push) }
    val songMenu = remember { SongMenuHost() }

    KreateTheme(
        themeMode = themeMode,
        contrastLevel = contrastLevel,
        colorSeed = seed,
    ) {
        CompositionLocalProvider(
            LocalPlayerSheet provides playerSheet,
            LocalIconOnly provides iconOnly,
            LocalPlaylistInitial provides playlistInitial,
            LocalSongStorage provides songStorage,
            LocalNotifier provides notifier,
            LocalMessageHost provides messages,
            LocalNavigator provides navigator,
            LocalSongMenuHost provides songMenu,
        ) {
            Box(Modifier.fillMaxSize()) {
                Scaffold(
                    bottomBar = {
                        val route = topLevel.backStack.lastOrNull() as? Route
                        when {
                            // Only the tabs have the bar
                            route?.let(Tab::of) != null -> Column {
                                PlaybackMiniPlayer(miniPlayer, controller = playback)
                                TabBar(
                                    selected = topLevel.currentTab,
                                    onSelect = topLevel::switchTab,
                                )
                            }

                            // Other screens cover the whole window until something plays
                            route?.hasMiniPlayer == true && playing.hasMiniPlayer -> Surface(
                                color = MaterialTheme.colorScheme.surfaceContainer,
                            ) {
                                // Without the bar, the mini player stays clear of the system's
                                Box(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                                    PlaybackMiniPlayer(miniPlayer, controller = playback)
                                }
                            }
                        }
                    },
                    snackbarHost = {
                        val route = topLevel.backStack.lastOrNull() as? Route
                        if (route?.showsMessages != false) MessageHost()
                    },
                    containerColor = MaterialTheme.colorScheme.surfaceDim,
                    modifier = Modifier.fillMaxSize(),
                ) { barPadding ->
                    // Before the screens, so their own handling of going back comes first
                    ExitConfirmation(atBottom = topLevel.backStack.size == 1, snackbar = messages)
                    NavDisplay(
                        backStack = topLevel.backStack,
                        onBack = topLevel::pop,
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        entryProvider = entryProvider {
                            // The bar already keeps clear of the system's, the screens don't again.
                            // The mini player isn't kept clear of from outside, the lists end with
                            // room for it instead, see MiniPlayerSpace
                            val barBottom =
                                PaddingValues(bottom = barPadding.calculateBottomPadding())
                            val paddingModifier = Modifier
                                .padding(miniPlayer.outside(barBottom))
                                .consumeWindowInsets(barBottom)
                            val contentPadding = miniPlayer.inside

                            entry<Songs> {
                                HomeSongsScreen(
                                    { topLevel.push(Search) },
                                    { topLevel.push(Settings) },
                                    paddingModifier,
                                    contentPadding,
                                )
                            }
                            entry<Albums> {
                                HomeAlbumsScreen(
                                    { topLevel.push(albumRoute(it)) },
                                    { topLevel.push(Search) },
                                    { topLevel.push(Settings) },
                                    paddingModifier,
                                    contentPadding,
                                )
                            }
                            entry<Artists> {
                                HomeArtistsScreen(
                                    { topLevel.push(artistRoute(it)) },
                                    { topLevel.push(Search) },
                                    { topLevel.push(Settings) },
                                    paddingModifier,
                                    contentPadding,
                                )
                            }
                            entry<Playlists> {
                                HomePlaylistsScreen(
                                    { topLevel.push(playlistRoute(it)) },
                                    { topLevel.push(Search) },
                                    { topLevel.push(Settings) },
                                    paddingModifier,
                                    contentPadding,
                                )
                            }

                            entry<Search> {
                                SearchScreen(
                                    onBack = topLevel::pop,
                                    onOpenAlbum = { topLevel.push(albumRoute(it)) },
                                    onOpenArtist = { topLevel.push(artistRoute(it)) },
                                    onOpenPlaylist = { topLevel.push(playlistRoute(it)) },
                                    onSearch = { query, origin, tab ->
                                        topLevel.push(SearchResults(query, origin, tab))
                                    },
                                )
                            }
                            entry<SearchResults> {
                                SearchResultsScreen(
                                    initialTab = it.tab,
                                    onBack = topLevel::pop,
                                    onOpenAlbum = { item -> topLevel.push(albumRoute(item)) },
                                    onOpenArtist = { item -> topLevel.push(artistRoute(item)) },
                                    onOpenPlaylist = { item -> topLevel.push(playlistRoute(item)) },
                                    viewModel = routeViewModel(it),
                                )
                            }

                            entry<Settings> {
                                SettingsScreen(
                                    onBack = topLevel::pop,
                                    onOpen = topLevel::push,
                                )
                            }
                            entry<GeneralSettings> { GeneralSettingsScreen(topLevel::pop) }
                            entry<PlayerSettings> { PlayerSettingsScreen(topLevel::pop) }
                            entry<UiSettings> { UiSettingsScreen(topLevel::pop) }
                            entry<NetworkSettings> {
                                NetworkSettingsScreen(
                                    onBack = topLevel::pop,
                                    onOpen = topLevel::push,
                                )
                            }
                            entry<YouTubeSettings> {
                                YouTubeSettingsScreen(
                                    onBack = topLevel::pop,
                                    onSignIn = { topLevel.push(YouTubeSignIn) },
                                )
                            }
                            entry<OpenSubsonicSettings> { OpenSubsonicSettingsScreen(topLevel::pop) }
                            entry<BackupSettings> { BackupSettingsScreen(topLevel::pop) }
                            entry<YouTubeSignIn> {
                                YouTubeSignInScreen(
                                    onBack = topLevel::pop,
                                    onSignedIn = topLevel::pop,
                                )
                            }

                            entry<LocalAlbum> {
                                LocalAlbumDetailScreen(
                                    topLevel::pop,
                                    paddingModifier,
                                    contentPadding,
                                    viewModel = routeViewModel(it),
                                )
                            }
                            entry<LocalArtist> {
                                LocalArtistDetailScreen(
                                    onBack = topLevel::pop,
                                    onOpenAlbum = { item -> topLevel.push(albumRoute(item)) },
                                    modifier = paddingModifier,
                                    contentPadding = contentPadding,
                                    viewModel = routeViewModel(it),
                                )
                            }
                            entry<LocalPlaylist> {
                                LocalPlaylistDetailScreen(
                                    topLevel::pop,
                                    paddingModifier,
                                    contentPadding,
                                    viewModel = routeViewModel(it),
                                )
                            }

                            // Items of external sources are fetched live and never stored
                            entry<OpenSubsonicAlbum> {
                                OpenSubsonicAlbumDetailScreen(
                                    topLevel::pop,
                                    paddingModifier,
                                    contentPadding,
                                    viewModel = routeViewModel(it),
                                )
                            }
                            entry<OpenSubsonicArtist> {
                                OpenSubsonicArtistDetailScreen(
                                    onBack = topLevel::pop,
                                    onOpenAlbum = { item -> topLevel.push(albumRoute(item)) },
                                    onOpenSongs = { name ->
                                        topLevel.push(OpenSubsonicArtistSongs(it.id, name))
                                    },
                                    modifier = paddingModifier,
                                    contentPadding = contentPadding,
                                    viewModel = routeViewModel(it),
                                )
                            }
                            entry<OpenSubsonicArtistSongs> {
                                OpenSubsonicArtistSongsScreen(
                                    topLevel::pop,
                                    paddingModifier,
                                    contentPadding,
                                    viewModel = routeViewModel(it),
                                )
                            }
                            entry<OpenSubsonicPlaylist> {
                                OpenSubsonicPlaylistDetailScreen(
                                    topLevel::pop,
                                    paddingModifier,
                                    contentPadding,
                                    viewModel = routeViewModel(it),
                                )
                            }
                            entry<YouTubeAlbum> {
                                YouTubeAlbumDetailScreen(
                                    onBack = topLevel::pop,
                                    onOpen = { tab, item ->
                                        itemRoute(tab, item)?.let(topLevel::push)
                                    },
                                    onSeeMore = { title, more ->
                                        topLevel.push(
                                            youTubeMoreRoute(
                                                title,
                                                more
                                            )
                                        )
                                    },
                                    modifier = paddingModifier,
                                    contentPadding = contentPadding,
                                    viewModel = routeViewModel(it),
                                )
                            }
                            entry<YouTubeArtist> {
                                YouTubeArtistDetailScreen(
                                    onBack = topLevel::pop,
                                    onOpen = { tab, item ->
                                        itemRoute(tab, item)?.let(topLevel::push)
                                    },
                                    onSeeMore = { title, more ->
                                        topLevel.push(
                                            youTubeMoreRoute(
                                                title,
                                                more
                                            )
                                        )
                                    },
                                    onOpenPlaylist = { id -> topLevel.push(YouTubePlaylist(id)) },
                                    modifier = paddingModifier,
                                    contentPadding = contentPadding,
                                    viewModel = routeViewModel(it),
                                )
                            }
                            entry<YouTubeMore> {
                                YouTubeMoreScreen(
                                    onBack = topLevel::pop,
                                    onOpen = { tab, item ->
                                        itemRoute(tab, item)?.let(topLevel::push)
                                    },
                                    modifier = paddingModifier,
                                    contentPadding = contentPadding,
                                    viewModel = routeViewModel(it),
                                )
                            }
                            entry<YouTubePlaylist> {
                                YouTubePlaylistDetailScreen(
                                    topLevel::pop,
                                    paddingModifier,
                                    contentPadding,
                                    viewModel = routeViewModel(it),
                                )
                            }
                        },
                    )
                }
                // Last, so it covers everything and handles going back first
                PlayerSheet(playerSheet, controller = playback)
                SongMenuOverlay(songMenu)
            }
        }
    }
}
