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
package me.knighthat.kreate3.ui.screen.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import me.knighthat.kreate3.provider.youtube.YouTubeBrowse
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.source.youtube.browseYouTube
import me.knighthat.kreate3.ui.navigation.YouTubeMore

/**
 * Every item of the YouTube page in the saved state, fetched live and never stored.
 */
class YouTubeMoreViewModel(
    savedStateHandle: SavedStateHandle,
    provider: YouTubeProvider,
) : ViewModel() {

    val title: String = savedStateHandle[YouTubeMore.TITLE] ?: ""

    private val endpoint = YouTubeBrowse(
        browseId = checkNotNull(savedStateHandle[YouTubeMore.BROWSE_ID]) {
            "YouTubeMore.BROWSE_ID is missing from the saved state"
        },
        params = savedStateHandle[YouTubeMore.PARAMS],
    )

    val items = browseYouTube(provider, endpoint).cachedIn(viewModelScope)
}
