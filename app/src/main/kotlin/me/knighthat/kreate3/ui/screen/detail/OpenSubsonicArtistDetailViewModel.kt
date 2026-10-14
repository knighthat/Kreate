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
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.opensubsonic.Shareable
import me.knighthat.kreate3.ui.component.toLiveMediaItem
import me.knighthat.kreate3.ui.navigation.LocalArtist

/**
 * An artist of the OpenSubsonic server as their screen shows them.
 *
 * @property songs the songs of every album of theirs, album after album
 */
data class OpenSubsonicArtistDetail(
    val name: String?,
    val thumbnail: Uri?,
    val albums: List<MediaItem>,
    val songs: List<MediaItem>,
)

/**
 * Fetches the artist whose id is in the saved state, their albums and the songs on them, from the
 * server. Nothing is stored.
 */
class OpenSubsonicArtistDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val provider: OpenSubsonicProvider,
) : LiveDetailViewModel<OpenSubsonicArtistDetail>() {

    val artistId: String = checkNotNull(savedStateHandle[LocalArtist.ID]) {
        "LocalArtist.ID is missing from the saved state"
    }

    override val notFound = SERVER_OFF

    /** Whether the user may make shares on the server, `false` until that is known. */
    val canShare = provider.canShare(viewModelScope)

    init {
        load()
    }

    override suspend fun fetch(): OpenSubsonicArtistDetail? {
        val found = provider.artistWithSongs(artistId) ?: return null
        val artist = found.artist.item
        return OpenSubsonicArtistDetail(
            name = artist.name,
            thumbnail = artist.thumbnail,
            albums = found.albums.map { it.item.toLiveMediaItem() }.distinctBy { it.mediaId },
            songs = found.songs.map { it.toLiveMediaItem() }.distinctBy { it.mediaId },
        )
    }

    /**
     * A link to the artist, as all of their albums, that anyone can open. See
     * [OpenSubsonicAlbumDetailViewModel.shareLink].
     */
    suspend fun shareLink(name: String?): Result<String> =
        provider.shareLinkOf(Shareable.Artist(artistId), name)
}
