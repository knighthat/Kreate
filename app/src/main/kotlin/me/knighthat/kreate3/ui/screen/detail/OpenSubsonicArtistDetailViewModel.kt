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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
 * An artist of the OpenSubsonic server as their screen shows them, without the parts that come
 * later, see [OpenSubsonicArtistDetailViewModel.topSongs] and
 * [OpenSubsonicArtistDetailViewModel.description].
 *
 * @property albums the newest first, those without a year last
 */
data class OpenSubsonicArtistDetail(
    val name: String?,
    val thumbnail: Uri?,
    val albums: List<MediaItem>,
)

/**
 * The songs the screen of an artist shows.
 *
 * @property songs up to [TOP_SONGS] of their songs, see [OpenSubsonicProvider.topSongs]
 * @property hasMore whether [songs] may be only some of their songs, so the screen links to every
 * song: `false` when fewer than [TOP_SONGS] were found
 */
data class TopSongs(val songs: List<MediaItem>, val hasMore: Boolean)

/** Songs the screen of an artist shows, the rest are a tap away. */
const val TOP_SONGS = 10

/**
 * Fetches the artist whose id is in the saved state and their albums from the server, then their
 * top songs and biography, which take longer, each shown once it is there. Every song of theirs is
 * fetched only when asked for, see [allSongs]. Nothing is stored.
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

    /** What the screen shows of the artist until the server answers. */
    val opened: OpenedItem = savedStateHandle.openedItem()

    override val notFound: String get() = context.serverOff()

    private val _topSongs = MutableStateFlow<LiveState<TopSongs>>(LiveState.Loading)

    /**
     * The artist's top songs without the ones the user disliked, asked for once the artist is
     * there. A server that can't give them gives none.
     */
    val topSongs: StateFlow<LiveState<TopSongs>> = combine(_topSongs, disliked) { state, ids ->
        if (state is LiveState.Ready && ids.isNotEmpty())
            LiveState.Ready(state.value.copy(songs = state.value.songs.withoutDisliked(ids)))
        else
            state
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, LiveState.Loading)

    private val _description = MutableStateFlow<LiveState<String?>>(LiveState.Loading)

    /** The server's biography of the artist, `null` when it has none. */
    val description: StateFlow<LiveState<String?>> = _description.asStateFlow()

    /** Fetches what [topSongs] and [description] show, again on every [load]. */
    private var later: Job? = null

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
        failure = { e, _ ->
            e.serverFailure(
                context.resources,
                context.resources.itemName(opened.name, R.string.snackbar_this_artist)
            )
        },
    )

    init {
        load()
    }

    override suspend fun fetch(): OpenSubsonicArtistDetail? {
        val (found, albums) = provider.artistWithAlbums(artistId) ?: return null
        val artist = found.item
        follow.known(found.starredAt != null)
        fetchLater(artist.name)
        return OpenSubsonicArtistDetail(
            name = artist.name,
            thumbnail = artist.thumbnail,
            // Newest first like the device's, the server gives them in its own order
            albums = albums
                .sortedWith(compareByDescending(nullsFirst()) { it.item.year })
                .map { it.item.toLiveMediaItem() }
                .distinctBy { it.mediaId },
        )
    }

    /**
     * Asks for the biography and the top songs of the artist named [name], each alongside the
     * other: the top songs can take a while, the server may ask another service for them.
     */
    private fun fetchLater(name: String?) {
        later?.cancel()
        _topSongs.value = LiveState.Loading
        _description.value = LiveState.Loading
        later = viewModelScope.launch(Dispatchers.Default) {
            launch {
                _description.value =
                    LiveState.Ready(askServerOrNull { provider.artistBiography(artistId) })
            }
            val top = name?.let { askServerOrNull { provider.topSongs(artistId, it, TOP_SONGS) } }
                .orEmpty()
            _topSongs.value = LiveState.Ready(
                TopSongs(
                    songs = top.map { it.toLiveMediaItem() }.distinctBy { it.mediaId },
                    hasMore = top.size >= TOP_SONGS,
                )
            )
        }
    }

    // Its songs are in topSongs
    override fun OpenSubsonicArtistDetail.withoutSongs(disliked: Set<String>) = this

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
