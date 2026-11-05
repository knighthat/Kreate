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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.knighthat.kreate3.R
import me.knighthat.kreate3.musicbrainz.MusicBrainz
import me.knighthat.kreate3.musicbrainz.MusicBrainzArtist
import me.knighthat.kreate3.musicbrainz.MusicBrainzArtistMatch
import me.knighthat.kreate3.ui.navigation.ArtistDetails
import java.io.IOException

/** What the artist details page shows. */
sealed interface ArtistDetailsState {

    data object Loading : ArtistDetailsState

    /** MusicBrainz has no artist that matches the name. */
    data object NotFound : ArtistDetailsState

    data class Failed(val message: String) : ArtistDetailsState

    data class Loaded(val artist: MusicBrainzArtist) : ArtistDetailsState
}

/**
 * A search for the artist, from the top bar's search button.
 *
 * @property matches `null` while searching
 * @property error why the search failed, `null` when it didn't
 */
data class ArtistSearch(
    val name: String,
    val matches: List<MusicBrainzArtistMatch>?,
    val error: String? = null,
)

/**
 * The artist of the [ArtistDetails] route as MusicBrainz knows them: the best match of a search
 * by their name, or the one picked from a search. Nothing is kept, the page searches again each
 * time it opens.
 */
class ArtistDetailsViewModel(
    savedStateHandle: SavedStateHandle,
    private val context: Context,
    private val musicBrainz: MusicBrainz,
) : ViewModel() {

    val name: String = savedStateHandle[ArtistDetails.NAME] ?: ""

    private val _state = MutableStateFlow<ArtistDetailsState>(ArtistDetailsState.Loading)
    val state: StateFlow<ArtistDetailsState> = _state.asStateFlow()

    private val _search = MutableStateFlow<ArtistSearch?>(null)

    /** The open search, `null` while none is. */
    val search: StateFlow<ArtistSearch?> = _search.asStateFlow()

    /** The artist picked from a search, shown again by [load] in place of the best match. */
    private var picked: String? = null
    private var loading: Job? = null
    private var searching: Job? = null

    init {
        load()
    }

    /** Fetches the details again, of the artist picked if there is one. */
    fun load() {
        val id = picked
        fetch {
            if (id != null)
                musicBrainz.artist(id)
            else
                musicBrainz.identifyArtist(name)?.let { musicBrainz.artist(it.id) }
        }
    }

    /** Opens a search, by the artist's own name to start with. */
    fun openSearch() = searchArtists(name)

    fun closeSearch() {
        searching?.cancel()
        _search.value = null
    }

    /** Searches the artists named [name], in place of the open search's. */
    fun searchArtists(name: String) {
        searching?.cancel()
        _search.value = ArtistSearch(name, matches = null)
        searching = viewModelScope.launch {
            val result = try {
                ArtistSearch(name, musicBrainz.searchArtists(name))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e, TAG) { "Searching MusicBrainz failed" }
                ArtistSearch(name, emptyList(), failure(e))
            }
            _search.update { if (it == null) null else result }
        }
    }

    /** Shows [match] as the artist and closes the search. */
    fun pick(match: MusicBrainzArtistMatch) {
        closeSearch()
        picked = match.id
        load()
    }

    private fun fetch(block: suspend () -> MusicBrainzArtist?) {
        loading?.cancel()
        _state.value = ArtistDetailsState.Loading
        loading = viewModelScope.launch {
            _state.value = try {
                block()?.let { ArtistDetailsState.Loaded(it) } ?: ArtistDetailsState.NotFound
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e, TAG) { "Fetching from MusicBrainz failed" }
                ArtistDetailsState.Failed(failure(e))
            }
        }
    }

    private fun failure(e: Exception): String {
        val musicBrainz = context.getString(R.string.source_musicbrainz)
        return if (e is IOException)
            context.getString(R.string.error_unreachable, musicBrainz)
        else
            context.getString(R.string.error_artist_details, musicBrainz)
    }

    private companion object {
        const val TAG = "ArtistDetails"
    }
}
