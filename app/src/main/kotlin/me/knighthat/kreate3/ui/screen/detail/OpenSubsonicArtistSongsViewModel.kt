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
import androidx.media3.common.MediaItem
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.source.withoutDisliked
import me.knighthat.kreate3.ui.component.toLiveMediaItem
import me.knighthat.kreate3.ui.navigation.LocalArtist
import me.knighthat.kreate3.ui.navigation.OpenSubsonicArtistSongs

/**
 * Fetches every song of the artist whose id is in the saved state from the server, album after
 * album. Nothing is stored.
 */
class OpenSubsonicArtistSongsViewModel(
    savedStateHandle: SavedStateHandle,
    private val provider: OpenSubsonicProvider,
    library: UserSongRepository,
) : LiveDetailViewModel<List<MediaItem>>(library.dislikedIds()) {

    private val artistId: String = checkNotNull(savedStateHandle[LocalArtist.ID]) {
        "LocalArtist.ID is missing from the saved state"
    }

    /** The artist's name as their screen had it, `null` when they have none. */
    val name: String? = savedStateHandle[OpenSubsonicArtistSongs.NAME]

    override val notFound = SERVER_OFF

    init {
        load()
    }

    override suspend fun fetch(): List<MediaItem>? =
        provider.artistWithSongs(artistId)
            ?.songs
            ?.map { it.toLiveMediaItem() }
            ?.distinctBy { it.mediaId }

    override fun List<MediaItem>.withoutSongs(disliked: Set<String>) = withoutDisliked(disliked)
}
