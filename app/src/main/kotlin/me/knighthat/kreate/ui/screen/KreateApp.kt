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

package me.knighthat.kreate.ui.screen

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import me.knighthat.kreate.preferences.Preferences
import me.knighthat.kreate.preferences.collectAsStateWithLifecycle
import me.knighthat.kreate.ui.navigation.Albums
import me.knighthat.kreate.ui.navigation.Artists
import me.knighthat.kreate.ui.navigation.LocalAlbum
import me.knighthat.kreate.ui.navigation.LocalArtist
import me.knighthat.kreate.ui.navigation.LocalPlaylist
import me.knighthat.kreate.ui.navigation.Playlists
import me.knighthat.kreate.ui.navigation.Route
import me.knighthat.kreate.ui.navigation.Songs
import me.knighthat.kreate.ui.navigation.Tab
import me.knighthat.kreate.ui.navigation.rememberTopLevelBackStack
import me.knighthat.kreate.ui.navigation.routeViewModel
import me.knighthat.kreate.ui.screen.detail.LocalAlbumDetailScreen
import me.knighthat.kreate.ui.screen.detail.LocalArtistDetailScreen
import me.knighthat.kreate.ui.screen.detail.LocalPlaylistDetailScreen
import me.knighthat.kreate.ui.screen.home.HomeAlbumsScreen
import me.knighthat.kreate.ui.screen.home.HomeArtistsScreen
import me.knighthat.kreate.ui.screen.home.HomePlaylistsScreen
import me.knighthat.kreate.ui.screen.home.HomeSongsScreen
import me.knighthat.kreate.ui.theme.KreateTheme


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

    KreateTheme(
        themeMode = themeMode,
        contrastLevel = contrastLevel,
        colorSeed = Color.Unspecified,
    ) {
        Scaffold(
            bottomBar = {
                // Only the tabs have the bar, a detail screen covers the whole window
                if ((topLevel.backStack.lastOrNull() as? Route)?.let(Tab::of) != null)
                    BottomBar(selected = topLevel.currentTab, onSelect = topLevel::switchTab)
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

                    entry<Songs> { HomeSongsScreen(paddingModifier) }
                    entry<Albums> {
                        HomeAlbumsScreen({ topLevel.push(LocalAlbum(it)) }, paddingModifier)
                    }
                    entry<Artists> {
                        HomeArtistsScreen({ topLevel.push(LocalArtist(it)) }, paddingModifier)
                    }
                    entry<Playlists> {
                        HomePlaylistsScreen(
                            { topLevel.push(LocalPlaylist(it.toLong())) },
                            paddingModifier
                        )
                    }

                    entry<LocalAlbum> {
                        LocalAlbumDetailScreen(topLevel::pop, viewModel = routeViewModel(it))
                    }
                    entry<LocalArtist> {
                        LocalArtistDetailScreen(
                            onBack = topLevel::pop,
                            onOpenAlbum = { id -> topLevel.push(LocalAlbum(id)) },
                            viewModel = routeViewModel(it),
                        )
                    }
                    entry<LocalPlaylist> {
                        LocalPlaylistDetailScreen(topLevel::pop, viewModel = routeViewModel(it))
                    }
                },
            )
        }
    }
}
