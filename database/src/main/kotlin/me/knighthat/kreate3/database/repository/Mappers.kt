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

package me.knighthat.kreate3.database.repository

import me.knighthat.kreate3.database.entity.AlbumEntity
import me.knighthat.kreate3.database.entity.ArtistEntity
import me.knighthat.kreate3.database.entity.LyricsEntity
import me.knighthat.kreate3.database.entity.PersistentQueueEntity
import me.knighthat.kreate3.database.entity.PlaybackHistoryEntity
import me.knighthat.kreate3.database.entity.SearchHistoryEntity
import me.knighthat.kreate3.database.entity.SongEntity
import me.knighthat.kreate3.database.entity.SongFormatEntity
import me.knighthat.kreate3.database.entity.UserAlbumEntity
import me.knighthat.kreate3.database.entity.UserArtistEntity
import me.knighthat.kreate3.database.entity.UserEntity
import me.knighthat.kreate3.database.entity.UserPlaylistEntity
import me.knighthat.kreate3.database.entity.UserSongEntity
import me.knighthat.kreate3.database.model.Album
import me.knighthat.kreate3.database.model.Artist
import me.knighthat.kreate3.database.model.Lyrics
import me.knighthat.kreate3.database.model.PlaybackHistory
import me.knighthat.kreate3.database.model.QueuedSong
import me.knighthat.kreate3.database.model.SearchHistory
import me.knighthat.kreate3.database.model.Song
import me.knighthat.kreate3.database.model.SongFormat
import me.knighthat.kreate3.database.model.User
import me.knighthat.kreate3.database.model.UserAlbum
import me.knighthat.kreate3.database.model.UserAlbumView
import me.knighthat.kreate3.database.model.UserArtist
import me.knighthat.kreate3.database.model.UserArtistView
import me.knighthat.kreate3.database.model.UserPlaylist
import me.knighthat.kreate3.database.model.UserSong
import me.knighthat.kreate3.database.model.UserSongView
import me.knighthat.kreate3.database.view.UserAlbumViewEntity
import me.knighthat.kreate3.database.view.UserArtistViewEntity
import me.knighthat.kreate3.database.view.UserSongViewEntity
import kotlin.time.Instant

// Models carry no timestamps and no user id. Repositories add them when writing,
// so `createdAt` and `updatedAt` are always set in one place.

internal fun UserEntity.toModel() = User(id, name, thumbnail, activeSince, lastSeen)

internal fun User.toEntity(createdAt: Instant, updatedAt: Instant) =
    UserEntity(id, name, thumbnail, activeSince, lastSeen, createdAt, updatedAt)

internal fun ArtistEntity.toModel() = Artist(id, origin, name, thumbnail)

internal fun Artist.toEntity(createdAt: Instant, updatedAt: Instant) =
    ArtistEntity(id, origin, name, thumbnail, createdAt, updatedAt)

internal fun AlbumEntity.toModel() =
    Album(id, origin, name, thumbnail, year, artists, url, isExplicit)

internal fun Album.toEntity(createdAt: Instant, updatedAt: Instant) =
    AlbumEntity(id, origin, name, thumbnail, year, artists, url, isExplicit, createdAt, updatedAt)

internal fun SongEntity.toModel() =
    Song(
        id,
        origin,
        name,
        thumbnail,
        duration,
        isExplicit,
        albumId,
        albumPosition,
        unavailableSince
    )

internal fun Song.toEntity(createdAt: Instant, updatedAt: Instant) =
    SongEntity(
        id,
        origin,
        name,
        thumbnail,
        duration,
        isExplicit,
        albumId,
        albumPosition,
        unavailableSince,
        createdAt,
        updatedAt
    )

internal fun UserArtistEntity.toModel() = UserArtist(artistId, name, thumbnail, followSince)

internal fun UserArtist.toEntity(userId: String, createdAt: Instant, updatedAt: Instant) =
    UserArtistEntity(userId, artistId, name, thumbnail, followSince, createdAt, updatedAt)

internal fun UserAlbumEntity.toModel() = UserAlbum(albumId, name, thumbnail, artists, bookmarkedAt)

internal fun UserAlbum.toEntity(userId: String, createdAt: Instant, updatedAt: Instant) =
    UserAlbumEntity(userId, albumId, name, thumbnail, artists, bookmarkedAt, createdAt, updatedAt)

internal fun UserSongEntity.toModel() = UserSong(songId, name, artists, thumbnail, likedAt)

internal fun UserSong.toEntity(userId: String, createdAt: Instant, updatedAt: Instant) =
    UserSongEntity(userId, songId, name, artists, thumbnail, likedAt, createdAt, updatedAt)

internal fun UserPlaylistEntity.toModel() =
    UserPlaylist(playlistId, origin, name, thumbnail, isMonthly, remoteId)

internal fun UserPlaylist.toEntity(userId: String, createdAt: Instant, updatedAt: Instant) =
    UserPlaylistEntity(
        id,
        userId,
        origin,
        name,
        thumbnail,
        isMonthly,
        createdAt,
        updatedAt,
        remoteId
    )

internal fun SongFormatEntity.toModel() = SongFormat(songId, tag, mimetype, bitrate, length, gain)

internal fun SongFormat.toEntity(createdAt: Instant, updatedAt: Instant) =
    SongFormatEntity(songId, tag, mimetype, bitrate, length, gain, createdAt, updatedAt)

internal fun LyricsEntity.toModel() = Lyrics(songId, lyrics)

internal fun Lyrics.toEntity(createdAt: Instant, updatedAt: Instant) =
    LyricsEntity(songId, lyrics, createdAt, updatedAt)

internal fun SearchHistoryEntity.toModel() = SearchHistory(query, lastUsed)

internal fun PlaybackHistoryEntity.toModel() = PlaybackHistory(id, songId, timeSpent, createdAt)

internal fun PersistentQueueEntity.toModel() = QueuedSong(songId, position)

internal fun UserSongViewEntity.toModel() = UserSongView(
    songId, origin, name, artists.orEmpty(), artistIds.orEmpty(), thumbnail, duration,
    isExplicit, albumId, albumName, albumPosition, unavailableSince, likedAt,
)

internal fun UserAlbumViewEntity.toModel() = UserAlbumView(
    albumId,
    origin,
    name,
    artists.orEmpty(),
    thumbnail,
    year,
    url,
    isExplicit,
    songCount,
    bookmarkedAt,
)

internal fun UserArtistViewEntity.toModel() = UserArtistView(
    artistId, origin, name, thumbnail, songCount, followSince,
)
