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

package me.knighthat.kreate3

import me.knighthat.kreate3.ui.screen.detail.LocalAlbumDetailViewModel
import me.knighthat.kreate3.ui.screen.detail.LocalArtistDetailViewModel
import me.knighthat.kreate3.ui.screen.detail.LocalPlaylistDetailViewModel
import me.knighthat.kreate3.ui.screen.detail.OpenSubsonicAlbumDetailViewModel
import me.knighthat.kreate3.ui.screen.detail.OpenSubsonicArtistDetailViewModel
import me.knighthat.kreate3.ui.screen.detail.OpenSubsonicPlaylistDetailViewModel
import me.knighthat.kreate3.ui.screen.detail.YouTubeAlbumDetailViewModel
import me.knighthat.kreate3.ui.screen.detail.YouTubeArtistDetailViewModel
import me.knighthat.kreate3.ui.screen.detail.YouTubeMoreViewModel
import me.knighthat.kreate3.ui.screen.detail.YouTubePlaylistDetailViewModel
import me.knighthat.kreate3.ui.screen.home.HomeAlbumsViewModel
import me.knighthat.kreate3.ui.screen.home.HomeArtistsViewModel
import me.knighthat.kreate3.ui.screen.home.HomePlaylistsViewModel
import me.knighthat.kreate3.ui.screen.home.HomeSongsViewModel
import me.knighthat.kreate3.ui.screen.search.SearchResultsViewModel
import me.knighthat.kreate3.ui.screen.search.SearchViewModel
import me.knighthat.kreate3.ui.screen.settings.NetworkSettingsViewModel
import me.knighthat.kreate3.ui.screen.settings.YouTubeAccountViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module


val viewModelModule = module {
    viewModelOf(::HomeSongsViewModel)
    viewModelOf(::HomeAlbumsViewModel)
    viewModelOf(::HomeArtistsViewModel)
    viewModelOf(::HomePlaylistsViewModel)

    viewModelOf(::SearchViewModel)
    viewModelOf(::SearchResultsViewModel)

    viewModelOf(::LocalAlbumDetailViewModel)
    viewModelOf(::LocalArtistDetailViewModel)
    viewModelOf(::LocalPlaylistDetailViewModel)
    viewModelOf(::OpenSubsonicAlbumDetailViewModel)
    viewModelOf(::OpenSubsonicArtistDetailViewModel)
    viewModelOf(::OpenSubsonicPlaylistDetailViewModel)
    viewModelOf(::YouTubeAlbumDetailViewModel)
    viewModelOf(::YouTubeArtistDetailViewModel)
    viewModelOf(::YouTubeMoreViewModel)
    viewModelOf(::YouTubeAccountViewModel)
    viewModelOf(::YouTubePlaylistDetailViewModel)

    viewModelOf(::NetworkSettingsViewModel)
}
