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

package me.knighthat.kreate3.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import me.knighthat.kreate3.database.model.UserPlaylist
import me.knighthat.kreate3.database.repository.PlaylistRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.youtube.PlaylistPrivacy
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.source.SongStore

/**
 * What the user filled in to make a playlist, one kind per place it can be made.
 *
 * @property name the name, not trimmed yet
 */
sealed interface NewPlaylist {

    val name: String

    /** Made in the app. */
    data class Local(override val name: String, val pinned: Boolean) : NewPlaylist

    /** Made on the OpenSubsonic server. */
    data class OpenSubsonic(
        override val name: String,
        val description: String,
        val public: Boolean,
    ) : NewPlaylist

    /** Made on YouTube Music. */
    data class YouTube(
        override val name: String,
        val description: String,
        val privacy: PlaylistPrivacy,
    ) : NewPlaylist
}

/** How making a playlist went. */
enum class CreateOutcome {
    CREATED,

    /** The playlist exists, only the options that need a second request were not saved. */
    OPTIONS_NOT_SAVED,
    FAILED,
}

/**
 * Makes playlists, in the app or on a source the user can use right now.
 */
class CreatePlaylistViewModel internal constructor(
    private val playlists: PlaylistRepository,
    private val songs: SongStore,
    private val openSubsonic: OpenSubsonicProvider,
    private val youTube: YouTubeProvider,
) : ViewModel() {

    /**
     * Where a playlist can be made now: the app always, a source while it is set up and, for
     * YouTube, signed in. Only the app until the sources are checked.
     */
    val places: StateFlow<Set<Origin>> = flow {
        emit(buildSet {
            add(Origin.LOCAL)
            if (openSubsonic.serverUrl != null) add(Origin.OPEN_SUBSONIC)
            if (youTube.isSignedIn()) add(Origin.YOUTUBE)
        })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, setOf(Origin.LOCAL))

    /**
     * Makes [playlist] and puts the song [songId] of [songOrigin] in it, if there is one. A song
     * of another source than the playlist's is left out, a source can't hold it.
     */
    suspend fun create(
        playlist: NewPlaylist,
        songId: String?,
        songOrigin: Origin?,
    ): CreateOutcome {
        val name = playlist.name.trim()
        return try {
            when (playlist) {
                is NewPlaylist.Local -> {
                    // Stored first, so a song that can't be leaves no empty playlist
                    if (songId != null && songOrigin != null) songs.store(songId, songOrigin)
                    val id = playlists.save(
                        UserPlaylist(origin = Origin.LOCAL, name = name, isPinned = playlist.pinned)
                    )
                    if (songId != null) playlists.addSong(id, songId)
                    CreateOutcome.CREATED
                }

                is NewPlaylist.OpenSubsonic -> {
                    val ids = listOfNotNull(songId?.takeIf { songOrigin == Origin.OPEN_SUBSONIC })
                    val made = openSubsonic.createPlaylist(
                        name,
                        playlist.description.trim(),
                        playlist.public,
                        ids,
                    )
                    when {
                        made == null -> CreateOutcome.FAILED
                        made.optionsSaved -> CreateOutcome.CREATED
                        else -> CreateOutcome.OPTIONS_NOT_SAVED
                    }
                }

                is NewPlaylist.YouTube -> {
                    val ids = listOfNotNull(songId?.takeIf { songOrigin == Origin.YOUTUBE })
                    val id = youTube.createPlaylist(
                        name,
                        playlist.description.trim(),
                        playlist.privacy,
                        ids,
                    )
                    if (id == null) CreateOutcome.FAILED else CreateOutcome.CREATED
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e, TAG) { "Couldn't create a playlist on ${playlist::class.simpleName}" }
            CreateOutcome.FAILED
        }
    }

    private companion object {
        const val TAG = "CreatePlaylist"
    }
}
