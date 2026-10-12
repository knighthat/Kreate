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

package me.knighthat.kreate.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import me.knighthat.kreate.database.repository.UserSongRepository
import me.knighthat.kreate.ui.component.toMediaItems

/**
 * The songs in the active user's library. Pages are kept while the screen lives.
 */
class HomeSongsViewModel(songRepository: UserSongRepository) : ViewModel() {

    val songs = songRepository.all()
        .toMediaItems()
        .cachedIn(viewModelScope)
}
