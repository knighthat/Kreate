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
import androidx.media3.common.MediaItem
import me.knighthat.kreate3.provider.youtube.YouTubeLinks
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.source.youtube.HEADER_ARTWORK
import me.knighthat.kreate3.source.youtube.SONG_ARTWORK
import me.knighthat.kreate3.source.youtube.artwork
import me.knighthat.kreate3.ui.component.toLiveMediaItem
import me.knighthat.kreate3.ui.navigation.LocalArtist

/**
 * An artist of YouTube as their screen shows them.
 *
 * @property songsPlaylistId the playlist of every song of the artist, `null` when there is none
 * @property sections rows like "Albums", "Singles & EPs" and "Fans might also like"
 * @property description YouTube's description of the artist, `null` when it has none
 */
data class YouTubeArtistDetail(
    val name: String?,
    val thumbnail: Uri?,
    val topSongs: List<MediaItem>,
    val songsPlaylistId: String?,
    val sections: List<LiveSection>,
    val description: String? = null,
)

/**
 * Fetches the artist whose id is in the saved state from YouTube. Nothing is stored.
 */
class YouTubeArtistDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val provider: YouTubeProvider,
) : LiveDetailViewModel<YouTubeArtistDetail>() {

    val artistId: String = checkNotNull(savedStateHandle[LocalArtist.ID]) {
        "LocalArtist.ID is missing from the saved state"
    }

    override val notFound = "YouTube doesn't have this artist"

    init {
        load()
    }

    override suspend fun fetch(): YouTubeArtistDetail? {
        val page = provider.artistPage(artistId) ?: return null
        return YouTubeArtistDetail(
            name = page.artist.name,
            thumbnail = provider.artwork(page.artist.thumbnail, HEADER_ARTWORK),
            topSongs = page.topSongs
                .map {
                    it.toLiveMediaItem(
                        provider.artwork(
                            it.song.thumbnail ?: it.album?.thumbnail,
                            SONG_ARTWORK
                        )
                    )
                }
                .distinctBy { it.mediaId },
            songsPlaylistId = page.songsPlaylistId,
            sections = page.sections.map { it.toLiveSection(provider) }
                .filter { it.items.isNotEmpty() },
            description = page.description?.trim()?.ifEmpty { null },
        )
    }

    /** The artist's public link, it needs no request. */
    fun shareLink(): Result<String> = Result.success(YouTubeLinks.artist(artistId))
}
