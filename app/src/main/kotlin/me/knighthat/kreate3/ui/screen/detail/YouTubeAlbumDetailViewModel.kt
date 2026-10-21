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
import me.knighthat.kreate3.provider.youtube.YouTubeSection
import me.knighthat.kreate3.source.youtube.HEADER_ARTWORK
import me.knighthat.kreate3.source.youtube.SONG_ARTWORK
import me.knighthat.kreate3.source.youtube.artwork
import me.knighthat.kreate3.source.youtube.tab
import me.knighthat.kreate3.source.youtube.toMediaItem
import me.knighthat.kreate3.ui.component.toLiveMediaItem
import me.knighthat.kreate3.ui.navigation.LocalAlbum

/**
 * An album of YouTube as its screen shows it.
 *
 * @property songs in the album's order, each with its cover (the album's when it has none) for the queue
 * @property description YouTube's description of the album, `null` when it has none
 */
data class YouTubeAlbumDetail(
    val name: String?,
    val thumbnail: Uri?,
    val subtitle: String?,
    val songs: List<MediaItem>,
    val sections: List<LiveSection>,
    val description: String? = null,
)

/** The row as cards to show, each item once. */
internal fun YouTubeSection.toLiveSection(provider: YouTubeProvider): LiveSection =
    LiveSection(
        title = title,
        items = items.map { it.tab to it.toMediaItem(provider) }
            .distinctBy { (tab, item) -> tab to item.mediaId },
        more = more,
    )

/**
 * Fetches the album whose id is in the saved state from YouTube. Nothing is stored.
 */
class YouTubeAlbumDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val provider: YouTubeProvider,
) : LiveDetailViewModel<YouTubeAlbumDetail>() {

    val albumId: String = checkNotNull(savedStateHandle[LocalAlbum.ID]) {
        "LocalAlbum.ID is missing from the saved state"
    }

    override val notFound = "YouTube doesn't have this album"

    init {
        load()
    }

    override suspend fun fetch(): YouTubeAlbumDetail? {
        val page = provider.albumPage(albumId) ?: return null
        val album = page.album
        return YouTubeAlbumDetail(
            name = album.name,
            thumbnail = provider.artwork(album.thumbnail, HEADER_ARTWORK),
            subtitle = listOfNotNull(album.artists?.joinToString(", "), album.year?.toString())
                .takeIf { it.isNotEmpty() }?.joinToString(" · "),
            songs = page.songs.map {
                it.toLiveMediaItem(
                    provider.artwork(it.song.thumbnail ?: album.thumbnail, SONG_ARTWORK)
                )
            }.distinctBy { it.mediaId },
            sections = page.sections.map { it.toLiveSection(provider) }
                .filter { it.items.isNotEmpty() },
            description = page.description?.trim()?.ifEmpty { null },
        )
    }

    /** The album's public link, it needs no request. */
    fun shareLink(): Result<String> = Result.success(YouTubeLinks.album(albumId))
}
