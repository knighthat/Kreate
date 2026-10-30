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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.constant.SyncMode
import me.knighthat.kreate3.preferences.Preference
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.provider.LibrarySection
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
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
    openSubsonicSection(Preferences.OPEN_SUBSONIC_SYNC_ALBUMS) { starredOnly ->
        openSubsonic.libraryAlbums(starredOnly)?.map {
            it.item.toLiveMediaItem(isBookmarked = it.starredAt != null)
        }
    },
    // Saved to the library is YouTube's bookmark
    youTubeSection(LibrarySection.ALBUMS) {
        youTube.libraryAlbums()?.map {
            it.toLiveMediaItem(youTube.artwork(it.thumbnail, CARD_ARTWORK), isBookmarked = true)
        }
    },
)

/** The artists followed on each source. */
suspend fun liveArtists(openSubsonic: OpenSubsonicProvider, youTube: YouTubeProvider) = fetchEach(
    openSubsonicSection(Preferences.OPEN_SUBSONIC_SYNC_ARTISTS) { starredOnly ->
        openSubsonic.libraryArtists(starredOnly)?.map {
            it.item.toLiveMediaItem(isFollowed = it.starredAt != null)
        }
    },
    // A subscription is YouTube's follow
    youTubeSection(LibrarySection.ARTISTS) {
        youTube.libraryArtists()?.map {
            it.toLiveMediaItem(youTube.artwork(it.thumbnail, CARD_ARTWORK), isFollowed = true)
        }
    },
)

/** The playlists of the user on each source. */
suspend fun livePlaylists(openSubsonic: OpenSubsonicProvider, youTube: YouTubeProvider) = fetchEach(
    {
        if (Preferences.OPEN_SUBSONIC_SYNC_PLAYLISTS.value)
            openSubsonic.libraryPlaylists()?.map { it.toLiveMediaItem() }
        else null
    },
    youTubeSection(LibrarySection.PLAYLISTS) {
        youTube.libraryPlaylists()?.map {
            it.toLiveMediaItem(youTube.artwork(it.thumbnail, CARD_ARTWORK))
        }
    },
)

/**
 * [fetch], unless [setting] turns this part of the server's library off.
 *
 * @param fetch gets whether only the starred items show, [SyncMode.LIKED]
 */
private fun openSubsonicSection(
    setting: Preference<SyncMode>,
    fetch: suspend (starredOnly: Boolean) -> List<MediaItem>?,
): suspend () -> List<MediaItem>? = {
    when (setting.value) {
        SyncMode.OFF -> null
        SyncMode.LIKED -> fetch(true)
        SyncMode.ALL -> fetch(false)
    }
}

/** [fetch], unless [section] of the YouTube library is turned off. */
private fun youTubeSection(
    section: LibrarySection,
    fetch: suspend () -> List<MediaItem>?,
): suspend () -> List<MediaItem>? = { if (section.youTubeSyncSetting.value) fetch() else null }

/**
 * Every source at once, their items in the order of [sources], each item once. Called from a
 * screen, so the lists are built off the main thread: a large library takes long.
 *
 * @param sources each gives `null` while it is off or nobody is signed in
 */
private suspend fun fetchEach(
    vararg sources: suspend () -> List<MediaItem>?,
): List<MediaItem> = withContext(Dispatchers.Default) {
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
