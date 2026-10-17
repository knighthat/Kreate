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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.opensubsonic.Shareable
import me.knighthat.kreate3.ui.component.toLiveMediaItem
import me.knighthat.kreate3.ui.navigation.LocalAlbum

/**
 * An album of the OpenSubsonic server as its screen shows it.
 *
 * @property songs in the album's order, they show the album's cover so they carry none
 * @property description the server's notes about the album, `null` when it has none
 */
data class OpenSubsonicAlbumDetail(
    val name: String?,
    val thumbnail: Uri?,
    val subtitle: String?,
    val songs: List<MediaItem>,
    val description: String? = null,
)

/**
 * Fetches the album whose id is in the saved state, and its songs, from the server. Nothing is
 * stored.
 */
class OpenSubsonicAlbumDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val provider: OpenSubsonicProvider,
) : LiveDetailViewModel<OpenSubsonicAlbumDetail>() {

    val albumId: String = checkNotNull(savedStateHandle[LocalAlbum.ID]) {
        "LocalAlbum.ID is missing from the saved state"
    }

    override val notFound = SERVER_OFF

    /** Whether the user may make shares on the server, `false` until that is known. */
    val canShare = provider.canShare(viewModelScope)

    init {
        load()
    }

    override suspend fun fetch(): OpenSubsonicAlbumDetail? = coroutineScope {
        // Asked alongside, the album shows without notes when the server has none to give
        val notes = async { askServerOrNull { provider.albumNotes(albumId) } }
        val (remote, songs) = provider.albumWithSongs(albumId) ?: return@coroutineScope null
        val album = remote.item
        OpenSubsonicAlbumDetail(
            name = album.name,
            thumbnail = album.thumbnail,
            subtitle = listOfNotNull(album.artists?.joinToString(", "), album.year?.toString())
                .takeIf { it.isNotEmpty() }?.joinToString(" · "),
            songs = songs.map { it.toLiveMediaItem(thumbnail = null) }.distinctBy { it.mediaId },
            description = notes.await(),
        )
    }

    /**
     * A link to the album that anyone can open, asked of the server at every tap. It gives a
     * share it has already or makes one. Links aren't stored, a share can expire or be deleted on
     * the server.
     *
     * @return the link, or why there is none
     */
    suspend fun shareLink(name: String?): Result<String> =
        provider.shareLinkOf(Shareable.Album(albumId), name)
}
