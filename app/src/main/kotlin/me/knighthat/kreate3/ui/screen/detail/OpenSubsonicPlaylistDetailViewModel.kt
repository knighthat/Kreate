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

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.opensubsonic.Shareable
import me.knighthat.kreate3.source.withoutDisliked
import me.knighthat.kreate3.ui.component.toLiveMediaItem
import me.knighthat.kreate3.ui.navigation.OpenSubsonicPlaylist

/**
 * A playlist of the OpenSubsonic server as its screen shows it.
 */
data class OpenSubsonicPlaylistDetail(
    val name: String,
    val thumbnail: Uri?,
    val songs: List<MediaItem>,
)

/**
 * Fetches the playlist whose id on the server is in the saved state, and its songs. Nothing is
 * stored.
 */
class OpenSubsonicPlaylistDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val provider: OpenSubsonicProvider,
    library: UserSongRepository,
) : LiveDetailViewModel<OpenSubsonicPlaylistDetail>(library.dislikedIds()) {

    val playlistId: String = checkNotNull(savedStateHandle[OpenSubsonicPlaylist.ID]) {
        "OpenSubsonicPlaylist.ID is missing from the saved state"
    }

    override val notFound = SERVER_OFF

    /** Whether the user may make shares on the server, `false` until that is known. */
    val canShare = provider.canShare(viewModelScope)

    init {
        load()
    }

    override suspend fun fetch(): OpenSubsonicPlaylistDetail? {
        val (playlist, songs) = provider.playlistWithSongs(playlistId) ?: return null
        return OpenSubsonicPlaylistDetail(
            name = playlist.name,
            thumbnail = playlist.thumbnail,
            songs = songs.map { it.toLiveMediaItem() }.distinctBy { it.mediaId },
        )
    }

    override fun OpenSubsonicPlaylistDetail.withoutSongs(disliked: Set<String>) =
        copy(songs = songs.withoutDisliked(disliked))

    /**
     * A link to the playlist that anyone can open. See
     * [OpenSubsonicAlbumDetailViewModel.shareLink].
     */
    suspend fun shareLink(name: String?): Result<String> =
        provider.shareLinkOf(Shareable.Playlist(playlistId), name)
}
