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

package me.knighthat.kreate

import me.knighthat.kreate.ui.screen.detail.LocalAlbumDetailViewModel
import me.knighthat.kreate.ui.screen.detail.LocalArtistDetailViewModel
import me.knighthat.kreate.ui.screen.detail.LocalPlaylistDetailViewModel
import me.knighthat.kreate.ui.screen.home.HomeAlbumsViewModel
import me.knighthat.kreate.ui.screen.home.HomeArtistsViewModel
import me.knighthat.kreate.ui.screen.home.HomePlaylistsViewModel
import me.knighthat.kreate.ui.screen.home.HomeSongsViewModel
import me.knighthat.kreate.ui.screen.search.SearchResultsViewModel
import me.knighthat.kreate.ui.screen.search.SearchViewModel
import me.knighthat.kreate.ui.screen.settings.NetworkSettingsViewModel
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

    viewModelOf(::NetworkSettingsViewModel)
}
