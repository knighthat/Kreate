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

package me.knighthat.kreate3.source

import android.util.Log
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import me.knighthat.kreate3.provider.LibrarySection
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.opensubsonic.openSubsonicSyncSetting
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.provider.youtube.youTubeSyncSetting
import me.knighthat.kreate3.source.youtube.CARD_ARTWORK
import me.knighthat.kreate3.source.youtube.artwork
import me.knighthat.kreate3.ui.component.origin
import me.knighthat.kreate3.ui.component.toLiveMediaItem

/*
 * The albums, artists and playlists of the user's library on each external source, fetched each
 * time they show and never stored. A source that is off, signed out or fails adds nothing, the
 * others still show. The OpenSubsonic server comes first, then YouTube.
 */

private const val TAG = "LiveLibrary"

/** The albums saved on each source. */
suspend fun liveAlbums(openSubsonic: OpenSubsonicProvider, youTube: YouTubeProvider) = fetchEach(
    openSubsonicSection(LibrarySection.ALBUMS) {
        openSubsonic.libraryAlbums()?.map { it.item.toLiveMediaItem() }
    },
    youTubeSection(LibrarySection.ALBUMS) {
        youTube.libraryAlbums()?.map {
            it.toLiveMediaItem(youTube.artwork(it.thumbnail, CARD_ARTWORK))
        }
    },
)

/** The artists followed on each source. */
suspend fun liveArtists(openSubsonic: OpenSubsonicProvider, youTube: YouTubeProvider) = fetchEach(
    openSubsonicSection(LibrarySection.ARTISTS) {
        openSubsonic.libraryArtists()?.map { it.item.toLiveMediaItem() }
    },
    youTubeSection(LibrarySection.ARTISTS) {
        youTube.libraryArtists()?.map {
            it.toLiveMediaItem(youTube.artwork(it.thumbnail, CARD_ARTWORK))
        }
    },
)

/** The playlists of the user on each source. */
suspend fun livePlaylists(openSubsonic: OpenSubsonicProvider, youTube: YouTubeProvider) = fetchEach(
    openSubsonicSection(LibrarySection.PLAYLISTS) {
        openSubsonic.libraryPlaylists()?.map { it.toLiveMediaItem() }
    },
    youTubeSection(LibrarySection.PLAYLISTS) {
        youTube.libraryPlaylists()?.map {
            it.toLiveMediaItem(youTube.artwork(it.thumbnail, CARD_ARTWORK))
        }
    },
)

/** [fetch], unless [section] of the server's library is turned off. */
private fun openSubsonicSection(
    section: LibrarySection,
    fetch: suspend () -> List<MediaItem>?,
): suspend () -> List<MediaItem>? = { if (section.openSubsonicSyncSetting.value) fetch() else null }

/** [fetch], unless [section] of the YouTube library is turned off. */
private fun youTubeSection(
    section: LibrarySection,
    fetch: suspend () -> List<MediaItem>?,
): suspend () -> List<MediaItem>? = { if (section.youTubeSyncSetting.value) fetch() else null }

/**
 * Every source at once, their items in the order of [sources], each item once.
 *
 * @param sources each gives `null` while it is off or nobody is signed in
 */
private suspend fun fetchEach(
    vararg sources: suspend () -> List<MediaItem>?,
): List<MediaItem> = coroutineScope {
    sources.map { source ->
        async {
            try {
                source().orEmpty()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't fetch a library", e)
                emptyList()
            }
        }
    }.awaitAll().flatten().distinctBy { it.origin to it.mediaId }
}
