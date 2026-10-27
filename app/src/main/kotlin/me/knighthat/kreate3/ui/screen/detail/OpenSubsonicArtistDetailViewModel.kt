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

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.R
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.notification.Notifier
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.opensubsonic.ServerCheck
import me.knighthat.kreate3.provider.opensubsonic.ServerException
import me.knighthat.kreate3.provider.opensubsonic.Shareable
import me.knighthat.kreate3.source.withoutDisliked
import me.knighthat.kreate3.ui.component.toLiveMediaItem
import me.knighthat.kreate3.ui.navigation.LocalArtist

/**
 * An artist of the OpenSubsonic server as their screen shows them.
 *
 * @property topSongs up to [TOP_SONGS] of their songs, see [OpenSubsonicProvider.topSongs]
 * @property hasMoreSongs whether [topSongs] may be only some of their songs, so the screen links to
 * every song: `false` when fewer than [TOP_SONGS] were found
 * @property description the server's biography of the artist, `null` when it has none
 */
data class OpenSubsonicArtistDetail(
    val name: String?,
    val thumbnail: Uri?,
    val albums: List<MediaItem>,
    val topSongs: List<MediaItem>,
    val hasMoreSongs: Boolean,
    val description: String? = null,
)

/** Songs the screen of an artist shows, the rest are a tap away. */
const val TOP_SONGS = 10

/**
 * Fetches the artist whose id is in the saved state, their albums and top songs, from the server.
 * Every song of theirs is fetched only when asked for, see [allSongs]. Nothing is stored.
 *
 * @param scope the app's, where a follow is sent
 */
class OpenSubsonicArtistDetailViewModel(
    context: Context,
    savedStateHandle: SavedStateHandle,
    private val provider: OpenSubsonicProvider,
    library: UserSongRepository,
    scope: CoroutineScope,
    notifier: Notifier,
) : LiveDetailViewModel<OpenSubsonicArtistDetail>(context, library.dislikedIds()) {

    val artistId: String = checkNotNull(savedStateHandle[LocalArtist.ID]) {
        "LocalArtist.ID is missing from the saved state"
    }

    override val notFound: String get() = context.serverOff()

    /** Every song of the artist once [allSongs] got them, they're asked for once. */
    @Volatile
    private var everySong: List<MediaItem>? = null

    /** Whether the user may make shares on the server, `false` until that is known. */
    val canShare = provider.canShare(viewModelScope)

    /** Whether the artist is followed, which is their star on the server. */
    val follow = SourceToggle(
        scope,
        notifier,
        send = { on ->
            if (!provider.setArtistStarred(
                    artistId,
                    on
                )
            ) throw ServerException(ServerCheck.Disabled)
        },
        failure = { e, on ->
            e.serverFailure(
                context.resources,
                if (on) R.string.snackbar_change_follow_artist else R.string.snackbar_change_unfollow_artist
            )
        },
    )

    init {
        load()
    }

    override suspend fun fetch(): OpenSubsonicArtistDetail? = coroutineScope {
        // Asked alongside, the artist shows without one when the server has none to give
        val biography = async { askServerOrNull { provider.artistBiography(artistId) } }
        val (found, albums) = provider.artistWithAlbums(artistId) ?: return@coroutineScope null
        val artist = found.item
        follow.known(found.starredAt != null)
        val top = artist.name?.let { provider.topSongs(artistId, it, TOP_SONGS) }.orEmpty()
        OpenSubsonicArtistDetail(
            name = artist.name,
            thumbnail = artist.thumbnail,
            albums = albums.map { it.item.toLiveMediaItem() }.distinctBy { it.mediaId },
            topSongs = top.map { it.toLiveMediaItem() }.distinctBy { it.mediaId },
            hasMoreSongs = top.size >= TOP_SONGS,
            description = biography.await(),
        )
    }

    override fun OpenSubsonicArtistDetail.withoutSongs(disliked: Set<String>) =
        copy(topSongs = topSongs.withoutDisliked(disliked))

    /**
     * Every song of the artist, album after album, fetched the first time, like for Play. The
     * ones the user disliked are left out.
     *
     * @return `null` when the server can't give them
     */
    suspend fun allSongs(): List<MediaItem>? = (everySong ?: withContext(Dispatchers.Default) {
        askServerOrNull { provider.artistWithSongs(artistId) }
            ?.songs
            ?.map { it.toLiveMediaItem() }
            ?.distinctBy { it.mediaId }
    }?.also { everySong = it })?.withoutDisliked(disliked.value)

    /**
     * A link to the artist, as all of their albums, that anyone can open. See
     * [OpenSubsonicAlbumDetailViewModel.shareLink].
     */
    suspend fun shareLink(name: String?): Result<String> =
        provider.shareLinkOf(Shareable.Artist(artistId), name)
}
