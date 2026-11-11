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
package me.knighthat.kreate3.ui.screen.details

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.knighthat.kreate3.R
import me.knighthat.kreate3.musicbrainz.MusicBrainz
import me.knighthat.kreate3.musicbrainz.MusicBrainzAlbum
import me.knighthat.kreate3.ui.navigation.AlbumDetails
import java.io.IOException

/** What the album details page shows. */
sealed interface AlbumDetailsState {

    data object Loading : AlbumDetailsState

    /** MusicBrainz has no album that matches the title. */
    data object NotFound : AlbumDetailsState

    data class Failed(val message: String) : AlbumDetailsState

    data class Loaded(val album: MusicBrainzAlbum) : AlbumDetailsState
}

/**
 * The album of the [AlbumDetails] route as MusicBrainz knows it: the best match of a search by
 * its title and artist. Nothing is kept, the page searches again each time it opens.
 */
class AlbumDetailsViewModel(
    savedStateHandle: SavedStateHandle,
    private val context: Context,
    private val musicBrainz: MusicBrainz,
) : ViewModel() {

    val title: String = savedStateHandle[AlbumDetails.TITLE] ?: ""

    private val artist: String? = savedStateHandle[AlbumDetails.ARTIST]

    private val _state = MutableStateFlow<AlbumDetailsState>(AlbumDetailsState.Loading)
    val state: StateFlow<AlbumDetailsState> = _state.asStateFlow()

    private var loading: Job? = null

    init {
        load()
    }

    /** Fetches the details again. */
    fun load() {
        loading?.cancel()
        _state.value = AlbumDetailsState.Loading
        loading = viewModelScope.launch {
            _state.value = try {
                musicBrainz.identifyAlbum(title, artist)
                    ?.let { musicBrainz.album(it.id) }
                    ?.let { AlbumDetailsState.Loaded(it) }
                    ?: AlbumDetailsState.NotFound
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e, TAG) { "Fetching from MusicBrainz failed" }
                AlbumDetailsState.Failed(failure(e))
            }
        }
    }

    private fun failure(e: Exception): String {
        val musicBrainz = context.getString(R.string.source_musicbrainz)
        return if (e is IOException)
            context.getString(R.string.error_unreachable, musicBrainz)
        else
            context.getString(R.string.error_details, musicBrainz)
    }

    private companion object {
        const val TAG = "AlbumDetails"
    }
}
