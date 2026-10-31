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
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.knighthat.kreate3.R
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.musicbrainz.MusicBrainz
import me.knighthat.kreate3.musicbrainz.MusicBrainzMatch
import me.knighthat.kreate3.musicbrainz.MusicBrainzRecording
import me.knighthat.kreate3.ui.navigation.SongDetails
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds

/** What the song details page shows. */
sealed interface SongDetailsState {

    data object Loading : SongDetailsState

    /** MusicBrainz has no recording that matches the song. */
    data object NotFound : SongDetailsState

    data class Failed(val message: String) : SongDetailsState

    /** @property kept whether the recording is kept as the song's, only songs of the library are */
    data class Loaded(val recording: MusicBrainzRecording, val kept: Boolean) : SongDetailsState
}

/**
 * A search for the recording the song is, from the top bar's search button.
 *
 * @property matches `null` while searching
 * @property error why the search failed, `null` when it didn't
 */
data class RecordingSearch(
    val title: String,
    val artist: String,
    val matches: List<MusicBrainzMatch>?,
    val error: String? = null,
)

/**
 * The song of the [SongDetails] route as MusicBrainz knows it. The recording the song was
 * identified as before is fetched again; without one, the best match of a search by the song's
 * title, artist and length. Every recording fetched is kept as the song's, when it is in the
 * library.
 */
class SongDetailsViewModel(
    savedStateHandle: SavedStateHandle,
    private val context: Context,
    private val songs: UserSongRepository,
    private val musicBrainz: MusicBrainz,
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val songId: String = checkNotNull(savedStateHandle[SongDetails.ID]) {
        "SongDetails.ID is missing from the saved state"
    }
    val title: String = savedStateHandle[SongDetails.TITLE] ?: ""
    val artist: String? = savedStateHandle[SongDetails.ARTIST]
    private val length = savedStateHandle.get<Long>(SongDetails.DURATION)?.milliseconds

    private val _state = MutableStateFlow<SongDetailsState>(SongDetailsState.Loading)
    val state: StateFlow<SongDetailsState> = _state.asStateFlow()

    private val _search = MutableStateFlow<RecordingSearch?>(null)

    /** The open search, `null` while none is. */
    val search: StateFlow<RecordingSearch?> = _search.asStateFlow()

    private var loading: Job? = null
    private var searching: Job? = null

    init {
        load()
    }

    /** Fetches the details again, from the recording kept for the song if there is one. */
    fun load() = fetch {
        // A kept recording deleted from MusicBrainz since is identified again
        songs.musicBrainzId(songId)?.let { musicBrainz.recording(it) }
            ?: musicBrainz.identify(title, artist, length)?.let { musicBrainz.recording(it.id) }
    }

    /** Opens a search, by the song's own words to start with. */
    fun openSearch() = searchRecordings(title, artist.orEmpty())

    fun closeSearch() {
        searching?.cancel()
        _search.value = null
    }

    /** Searches the recordings named [title] by [artist], in place of the open search's. */
    fun searchRecordings(title: String, artist: String) {
        searching?.cancel()
        _search.value = RecordingSearch(title, artist, matches = null)
        searching = viewModelScope.launch {
            val result = try {
                RecordingSearch(title, artist, musicBrainz.search(title, artist.ifBlank { null }))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Searching MusicBrainz failed", e)
                RecordingSearch(title, artist, emptyList(), failure(e))
            }
            _search.update { if (it == null) null else result }
        }
    }

    /** Shows [match] as the song, which keeps it, and closes the search. */
    fun pick(match: MusicBrainzMatch) {
        closeSearch()
        fetch { musicBrainz.recording(match.id) }
    }

    /** Runs [block] for the recording to show, keeping it as the song's when it differs. */
    private fun fetch(block: suspend () -> MusicBrainzRecording?) {
        loading?.cancel()
        _state.value = SongDetailsState.Loading
        loading = viewModelScope.launch {
            _state.value = try {
                val recording = block()
                if (recording == null) SongDetailsState.NotFound
                else SongDetailsState.Loaded(recording, keep(recording.id))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Fetching from MusicBrainz failed", e)
                SongDetailsState.Failed(failure(e))
            }
        }
    }

    /**
     * Keeps [id] as the song's, a merged recording's new id too. On the app's scope, leaving the
     * page doesn't lose it.
     *
     * @return whether the song is in the library, so the id is kept
     */
    private suspend fun keep(id: String): Boolean {
        val kept = songs.musicBrainzId(songId)
        if (kept == id) return true
        return appScope.async { songs.setMusicBrainzId(songId, id) }.await()
    }

    private fun failure(e: Exception): String {
        val musicBrainz = context.getString(R.string.source_musicbrainz)
        return if (e is IOException) context.getString(R.string.error_unreachable, musicBrainz)
        else context.getString(R.string.error_song_details, musicBrainz)
    }

    private companion object {
        const val TAG = "SongDetails"
    }
}
