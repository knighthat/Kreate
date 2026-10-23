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

import kotlinx.coroutines.flow.first
import me.knighthat.kreate3.database.model.Album
import me.knighthat.kreate3.database.model.Artist
import me.knighthat.kreate3.database.repository.AlbumRepository
import me.knighthat.kreate3.database.repository.ArtistRepository
import me.knighthat.kreate3.database.repository.UserAlbumRepository
import me.knighthat.kreate3.database.repository.UserArtistRepository
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Bookmarks of albums and follows of artists kept in the active user's library on the device,
 * for items fetched live too, like a YouTube album while nobody is signed in. Such an item is
 * stored with the first one, the library entry needs it in the database. Taking it back removes
 * the entry again when the user keeps nothing else about the item (a name or cover of their
 * own); the stored item stays.
 */
interface DeviceReactions {

    /** Whether the user bookmarked the album, `false` for one that isn't stored. */
    suspend fun isBookmarked(albumId: String): Boolean

    /** Bookmarks [album], storing it first when it isn't, or takes the bookmark away. */
    suspend fun setBookmarked(album: Album, on: Boolean)

    /** Whether the user follows the artist, `false` for one that isn't stored. */
    suspend fun isFollowed(artistId: String): Boolean

    /** Follows [artist], storing them first when they aren't, or unfollows them. */
    suspend fun setFollowed(artist: Artist, on: Boolean)
}

class DeviceReactionsImpl(
    private val albums: AlbumRepository,
    private val userAlbums: UserAlbumRepository,
    private val artists: ArtistRepository,
    private val userArtists: UserArtistRepository,
    private val clock: Clock = Clock.System,
) : DeviceReactions {

    override suspend fun isBookmarked(albumId: String): Boolean =
        userAlbums.find(albumId).first()?.bookmarkedAt.isPositive

    override suspend fun setBookmarked(album: Album, on: Boolean) {
        if (on) {
            // A stored album keeps its own values
            if (albums.find(album.id).first() == null) albums.save(album)
            userAlbums.update(album.id) { it.copy(bookmarkedAt = clock.now()) }
        } else if (userAlbums.find(album.id).first() != null) {
            userAlbums.update(album.id) { it.copy(bookmarkedAt = null) }
            // Kept for the bookmark alone, the entry leaves the library with it
            userAlbums.removeIfEmpty(album.id)
        }
    }

    override suspend fun isFollowed(artistId: String): Boolean =
        userArtists.find(artistId).first()?.followSince.isPositive

    override suspend fun setFollowed(artist: Artist, on: Boolean) {
        if (on) {
            if (artists.find(artist.id).first() == null) artists.save(artist)
            userArtists.update(artist.id) { it.copy(followSince = clock.now()) }
        } else if (userArtists.find(artist.id).first() != null) {
            userArtists.update(artist.id) { it.copy(followSince = null) }
            // Kept for the follow alone, the entry leaves the library with it
            userArtists.removeIfEmpty(artist.id)
        }
    }

    /** Whether a reaction holds a positive one, not a dislike or nothing. */
    private val Instant?.isPositive: Boolean
        get() = this != null && toEpochMilliseconds() != DISLIKED

    private companion object {
        /** Stored as the reaction of a dislike, see `UserAlbum.bookmarkedAt`. */
        const val DISLIKED = -1L
    }
}
