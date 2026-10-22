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
import kotlinx.coroutines.CoroutineScope
import me.knighthat.kreate3.database.model.Artist
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.notification.Notifier
import me.knighthat.kreate3.provider.youtube.YouTubeLinks
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.source.DeviceReactions
import me.knighthat.kreate3.source.withoutDisliked
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
 * Fetches the artist whose id is in the saved state from YouTube. Nothing is stored, unless they
 * are followed while nobody is signed in.
 *
 * @param scope the app's, where a follow is sent
 */
class YouTubeArtistDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val provider: YouTubeProvider,
    private val device: DeviceReactions,
    library: UserSongRepository,
    scope: CoroutineScope,
    notifier: Notifier,
) : LiveDetailViewModel<YouTubeArtistDetail>(library.dislikedIds()) {

    val artistId: String = checkNotNull(savedStateHandle[LocalArtist.ID]) {
        "LocalArtist.ID is missing from the saved state"
    }

    override val notFound = "YouTube doesn't have this artist"

    /** The channel subscribed to, which may not be the artist's id. */
    @Volatile
    private var channelId: String? = null

    /** The artist to keep the follow of on the device, `null` while it goes to YouTube. */
    @Volatile
    private var onDevice: Artist? = null

    /**
     * Whether the artist is followed: subscribed to on YouTube while signed in, kept on the device
     * otherwise.
     */
    val follow = SourceToggle(
        scope,
        notifier,
        send = { on ->
            val artist = onDevice
            if (artist != null) device.setFollowed(artist, on)
            else provider.subscribe(checkNotNull(channelId), on)
        },
        failure = { e, on ->
            if (onDevice != null) if (on) "Couldn't follow the artist" else "Couldn't unfollow the artist"
            else e.youTubeFailure(if (on) "follow the artist" else "unfollow the artist")
        },
    )

    init {
        load()
    }

    override suspend fun fetch(): YouTubeArtistDetail? {
        val page = provider.artistPage(artistId) ?: return null
        val signedIn = provider.isSignedIn()
        channelId = page.channelId
        onDevice = page.artist.takeUnless { signedIn }
        follow.known(
            when {
                !signedIn -> device.isFollowed(artistId)
                // Asked in the session, the page tells whether the user is subscribed
                page.channelId != null -> page.subscribed
                else -> null
            }
        )
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

    override fun YouTubeArtistDetail.withoutSongs(disliked: Set<String>) = copy(
        topSongs = topSongs.withoutDisliked(disliked),
        sections = sections.withoutDislikedSongs(disliked),
    )

    /** The artist's public link, it needs no request. */
    fun shareLink(): Result<String> = Result.success(YouTubeLinks.artist(artistId))
}
