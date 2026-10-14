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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import me.knighthat.kreate3.constant.ThemeMode
import me.knighthat.kreate3.playback.PlaybackController
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate3.ui.component.MiniPlayer
import me.knighthat.kreate3.ui.navigation.Albums
import me.knighthat.kreate3.ui.navigation.Artists
import me.knighthat.kreate3.ui.navigation.GeneralSettings
import me.knighthat.kreate3.ui.navigation.LocalAlbum
import me.knighthat.kreate3.ui.navigation.LocalArtist
import me.knighthat.kreate3.ui.navigation.LocalPlaylist
import me.knighthat.kreate3.ui.navigation.NetworkSettings
import me.knighthat.kreate3.ui.navigation.OpenSubsonicAlbum
import me.knighthat.kreate3.ui.navigation.OpenSubsonicArtist
import me.knighthat.kreate3.ui.navigation.OpenSubsonicPlaylist
import me.knighthat.kreate3.ui.navigation.Playlists
import me.knighthat.kreate3.ui.navigation.Route
import me.knighthat.kreate3.ui.navigation.Search
import me.knighthat.kreate3.ui.navigation.SearchResults
import me.knighthat.kreate3.ui.navigation.Settings
import me.knighthat.kreate3.ui.navigation.Songs
import me.knighthat.kreate3.ui.navigation.Tab
import me.knighthat.kreate3.ui.navigation.UiSettings
import me.knighthat.kreate3.ui.navigation.albumRoute
import me.knighthat.kreate3.ui.navigation.artistRoute
import me.knighthat.kreate3.ui.navigation.playlistRoute
import me.knighthat.kreate3.ui.navigation.rememberTopLevelBackStack
import me.knighthat.kreate3.ui.navigation.routeViewModel
import me.knighthat.kreate3.ui.screen.detail.LocalAlbumDetailScreen
import me.knighthat.kreate3.ui.screen.detail.LocalAlbumDetailViewModel
import me.knighthat.kreate3.ui.screen.detail.LocalArtistDetailScreen
import me.knighthat.kreate3.ui.screen.detail.LocalArtistDetailViewModel
import me.knighthat.kreate3.ui.screen.detail.LocalPlaylistDetailScreen
import me.knighthat.kreate3.ui.screen.detail.LocalPlaylistDetailViewModel
import me.knighthat.kreate3.ui.screen.detail.OpenSubsonicAlbumDetailScreen
import me.knighthat.kreate3.ui.screen.detail.OpenSubsonicArtistDetailScreen
import me.knighthat.kreate3.ui.screen.detail.OpenSubsonicPlaylistDetailScreen
import me.knighthat.kreate3.ui.screen.home.HomeAlbumsScreen
import me.knighthat.kreate3.ui.screen.home.HomeArtistsScreen
import me.knighthat.kreate3.ui.screen.home.HomePlaylistsScreen
import me.knighthat.kreate3.ui.screen.home.HomeSongsScreen
import me.knighthat.kreate3.ui.screen.search.SearchResultsScreen
import me.knighthat.kreate3.ui.screen.search.SearchScreen
import me.knighthat.kreate3.ui.screen.settings.GeneralSettingsScreen
import me.knighthat.kreate3.ui.screen.settings.NetworkSettingsScreen
import me.knighthat.kreate3.ui.screen.settings.SettingsScreen
import me.knighthat.kreate3.ui.screen.settings.UiSettingsScreen
import me.knighthat.kreate3.ui.theme.KreateTheme
import me.knighthat.kreate3.ui.theme.rememberThumbnailSeed
import org.koin.compose.koinInject
import androidx.lifecycle.compose.collectAsStateWithLifecycle as collectFlowAsState


/** Tells the ViewModel that syncs a server's item apart from the one that shows it. */
private const val SYNC_TAG = "sync"

/**
 * The [MiniPlayer] for what [controller] is playing, hidden while its queue is empty.
 */
@Composable
private fun PlaybackMiniPlayer(controller: PlaybackController) {
    val state by controller.state.collectFlowAsState()
    val item = state.current ?: return

    // Re-read every frame while playing, drawing is the only place that depends on it
    var progress by remember { mutableFloatStateOf(controller.progress()) }
    LaunchedEffect(item, state.isPlaying) {
        progress = controller.progress()
        while (state.isPlaying) {
            withFrameNanos { }
            progress = controller.progress()
        }
    }

    MiniPlayer(
        item = item,
        isPlaying = state.isPlaying,
        progress = { progress },
        onTogglePlay = controller::togglePlay,
        onNext = controller::next,
        // Expanding to the full player isn't there yet
        onClick = {},
    )
}

/**
 * One button for each [Tab].
 */
@Composable
private fun BottomBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
) {
    NavigationBar {
        Tab.entries.forEach {
            NavigationBarItem(
                selected = it == selected,
                onClick = { onSelect(it) },
                icon = { Icon(painterResource(it.icon), contentDescription = null) },
                label = { Text(it.label) },
            )
        }
    }
}

@Composable
fun KreateApp() {
    val themeMode by Preferences.THEME_MODE.collectAsStateWithLifecycle()
    val contrastLevel by Preferences.CONTRAST_LEVEL.collectAsStateWithLifecycle()

    val topLevel = rememberTopLevelBackStack()
    val playback = koinInject<PlaybackController>()
    val playing by playback.state.collectFlowAsState()
    val seed = rememberThumbnailSeed(playing.current, themeMode == ThemeMode.THUMBNAIL)

    KreateTheme(
        themeMode = themeMode,
        contrastLevel = contrastLevel,
        colorSeed = seed,
    ) {
        Scaffold(
            bottomBar = {
                // Only the tabs have the bar, a detail screen covers the whole window
                if ((topLevel.backStack.lastOrNull() as? Route)?.let(Tab::of) != null)
                    Column {
                        PlaybackMiniPlayer(playback)
                        BottomBar(selected = topLevel.currentTab, onSelect = topLevel::switchTab)
                    }
            },
            containerColor = MaterialTheme.colorScheme.surfaceDim,
            modifier = Modifier.fillMaxSize(),
        ) { barPadding ->
            NavDisplay(
                backStack = topLevel.backStack,
                onBack = topLevel::pop,
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider {
                    val paddingModifier =
                        Modifier.padding(bottom = barPadding.calculateBottomPadding())

                    entry<Songs> {
                        HomeSongsScreen(
                            { topLevel.push(Search) },
                            { topLevel.push(Settings) },
                            paddingModifier,
                        )
                    }
                    entry<Albums> {
                        HomeAlbumsScreen(
                            { topLevel.push(albumRoute(it)) },
                            { topLevel.push(Search) },
                            { topLevel.push(Settings) },
                            paddingModifier,
                        )
                    }
                    entry<Artists> {
                        HomeArtistsScreen(
                            { topLevel.push(artistRoute(it)) },
                            { topLevel.push(Search) },
                            { topLevel.push(Settings) },
                            paddingModifier,
                        )
                    }
                    entry<Playlists> {
                        HomePlaylistsScreen(
                            { topLevel.push(playlistRoute(it)) },
                            { topLevel.push(Search) },
                            { topLevel.push(Settings) },
                            paddingModifier,
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
                    entry<UiSettings> { UiSettingsScreen(topLevel::pop) }
                    entry<NetworkSettings> { NetworkSettingsScreen(topLevel::pop) }

                    entry<LocalAlbum> {
                        LocalAlbumDetailScreen(topLevel::pop, viewModel = routeViewModel(it))
                    }
                    entry<LocalArtist> {
                        LocalArtistDetailScreen(
                            onBack = topLevel::pop,
                            onOpenAlbum = { item -> topLevel.push(albumRoute(item)) },
                            viewModel = routeViewModel(it),
                        )
                    }
                    entry<LocalPlaylist> {
                        LocalPlaylistDetailScreen(topLevel::pop, viewModel = routeViewModel(it))
                    }

                    // Server items are fetched and stored first, then show like library items
                    entry<OpenSubsonicAlbum> {
                        val library = routeViewModel<LocalAlbumDetailViewModel>(it)
                        val album by library.album.collectFlowAsState()

                        OpenSubsonicAlbumDetailScreen(
                            title = album?.name.orEmpty(),
                            onBack = topLevel::pop,
                            viewModel = routeViewModel(it, tag = SYNC_TAG),
                        ) {
                            LocalAlbumDetailScreen(topLevel::pop, viewModel = library)
                        }
                    }
                    entry<OpenSubsonicArtist> {
                        val library = routeViewModel<LocalArtistDetailViewModel>(it)
                        val artist by library.artist.collectFlowAsState()

                        OpenSubsonicArtistDetailScreen(
                            title = artist?.name.orEmpty(),
                            onBack = topLevel::pop,
                            viewModel = routeViewModel(it, tag = SYNC_TAG),
                        ) {
                            LocalArtistDetailScreen(
                                onBack = topLevel::pop,
                                onOpenAlbum = { item -> topLevel.push(albumRoute(item)) },
                                viewModel = library,
                            )
                        }
                    }
                    entry<OpenSubsonicPlaylist> {
                        val library = routeViewModel<LocalPlaylistDetailViewModel>(it)
                        val playlist by library.playlist.collectFlowAsState()

                        OpenSubsonicPlaylistDetailScreen(
                            title = playlist?.name.orEmpty(),
                            onBack = topLevel::pop,
                            viewModel = routeViewModel(it, tag = SYNC_TAG),
                        ) {
                            LocalPlaylistDetailScreen(topLevel::pop, viewModel = library)
                        }
                    }
                },
            )
        }
    }
}
