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

package me.knighthat.kreate3.database.legacy

import android.net.Uri
import me.knighthat.kreate3.database.entity.AlbumEntity
import me.knighthat.kreate3.database.entity.ArtistEntity
import me.knighthat.kreate3.database.entity.PlaybackHistoryEntity
import me.knighthat.kreate3.database.entity.SearchHistoryEntity
import me.knighthat.kreate3.database.entity.SongArtistMapEntity
import me.knighthat.kreate3.database.entity.SongEntity
import me.knighthat.kreate3.database.entity.SongFormatEntity
import me.knighthat.kreate3.database.entity.UserAlbumEntity
import me.knighthat.kreate3.database.entity.UserArtistEntity
import me.knighthat.kreate3.database.entity.UserEntity
import me.knighthat.kreate3.database.entity.UserPlaylistEntity
import me.knighthat.kreate3.database.entity.UserSongEntity
import me.knighthat.kreate3.model.Origin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/** The old app's mark on a title, name or picture the user changed by hand. */
private const val MODIFIED_PREFIX = "modified:"

/**
 * Text without the old app's `modified:` marks.
 *
 * @property text the text, `null` when nothing is left
 * @property isModified whether the user had changed it
 */
internal class CleanText(val text: String?, val isModified: Boolean)

internal fun String?.cleaned(): CleanText {
    var text = this.orEmpty()
    while (text.startsWith(MODIFIED_PREFIX)) text = text.removePrefix(MODIFIED_PREFIX)
    return CleanText(text.takeIf(String::isNotBlank), text.length != this.orEmpty().length)
}

/**
 * The length the old app stored as text: seconds (`215`) or `m:ss` / `h:mm:ss`. `null` when
 * there is none or it can't be read.
 */
internal fun parseLegacyDuration(text: String?): Duration? {
    val parts = text?.trim()?.split(':') ?: return null
    if (parts.size > 3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) }) return null

    var seconds = 0L
    for (part in parts) seconds = seconds * 60 + (part.toLongOrNull() ?: return null)
    return seconds.takeIf { it > 0 }?.seconds
}

private val YEAR = Regex("""\d{4}""")

private fun Long.toInstant() = Instant.fromEpochMilliseconds(this)

/** A playlist with the songs it holds, by song id and position. */
internal class MappedPlaylist(val playlist: UserPlaylistEntity, val songs: List<Pair<String, Int>>)

/**
 * Everything one old database becomes. Songs, albums and artists are shared by every user, so
 * the same one can come from several databases and is stored once.
 */
internal class MappedProfile(
    val user: UserEntity,
    val albums: List<AlbumEntity>,
    val artists: List<ArtistEntity>,
    val songs: List<SongEntity>,
    val songArtists: List<SongArtistMapEntity>,
    val userSongs: List<UserSongEntity>,
    val userAlbums: List<UserAlbumEntity>,
    val userArtists: List<UserArtistEntity>,
    val playlists: List<MappedPlaylist>,
    val formats: List<SongFormatEntity>,
    val plays: List<PlaybackHistoryEntity>,
    val searches: List<SearchHistoryEntity>,
)

/**
 * Turns the rows of an old database into what this app stores, for one user. Choices:
 *
 * - Songs, albums and artists found on the device are left out: the scan finds them again, and
 *   their ids mean nothing here. Everything pointing at them goes too. Songs say so with
 *   `is_local`; albums and artists by an id of digits only, as their `youtube` flag can't be
 *   trusted.
 * - Everything else was YouTube. Every song is in the user's library, like a song that played.
 * - Texts marked `modified:` were typed by the user. They are the user's own values here and
 *   the shared value too, as the original is not known.
 * - `liked_at` stays as it was, a `-1` is a dislike. Albums and artists keep `bookmarked_at`.
 * - The old text for a song's artists is kept as the user's own only when the user typed it or
 *   the song has no artist rows to show.
 * - Playlists become local playlists, the link to YouTube is dropped. One left without a name is
 *   called `Playlist-` and its place in the old list, from 1.
 * - The saved queue and the lyrics are not imported: the queue has no order here, and the lyrics
 *   don't say which source they came from.
 */
internal fun mapLegacyLibrary(
    userId: String,
    userName: String,
    library: LegacyLibrary,
    now: Instant,
): MappedProfile {
    // The old `youtube` flag of artists and albums is 0 on nearly all of them, even for channel
    // ids (`UC…`) and album ids (`MPRE…`). Device ones have the numeric ids of the media index
    val artistRows = library.artists.filter { it.isYouTube || !it.id.all(Char::isDigit) }
    val albumRows = library.albums.filter { it.isYouTube || !it.id.all(Char::isDigit) }
    val songRows = library.songs.filter { !it.isLocal }

    val artistIds = artistRows.mapTo(HashSet()) { it.id }
    val albumIds = albumRows.mapTo(HashSet()) { it.id }
    val songIds = songRows.mapTo(HashSet()) { it.id }

    val artists = ArrayList<ArtistEntity>()
    val userArtists = ArrayList<UserArtistEntity>()
    for (row in artistRows) {
        val name = row.name.cleaned()
        val thumbnail = row.thumbnail.cleaned()
        artists += ArtistEntity(
            id = row.id,
            origin = Origin.YOUTUBE,
            name = name.text,
            thumbnail = thumbnail.text?.let(Uri::parse),
            createdAt = row.createdAt?.toInstant() ?: now,
            updatedAt = now,
        )
        if (row.bookmarkedAt != null || name.isModified || thumbnail.isModified)
            userArtists += UserArtistEntity(
                userId = userId,
                artistId = row.id,
                name = name.text.takeIf { name.isModified },
                thumbnail = thumbnail.text?.takeIf { thumbnail.isModified }?.let(Uri::parse),
                followSince = row.bookmarkedAt?.toInstant(),
                createdAt = now,
                updatedAt = now,
            )
    }

    val albums = ArrayList<AlbumEntity>()
    val userAlbums = ArrayList<UserAlbumEntity>()
    for (row in albumRows) {
        val title = row.title.cleaned()
        val thumbnail = row.thumbnail.cleaned()
        val authors = row.artists.cleaned()
        albums += AlbumEntity(
            id = row.id,
            origin = Origin.YOUTUBE,
            name = title.text,
            thumbnail = thumbnail.text?.let(Uri::parse),
            year = row.year?.let { YEAR.find(it)?.value?.toInt() },
            artists = authors.text?.let(::listOf),
            url = row.url?.takeIf(String::isNotBlank),
            createdAt = row.createdAt?.toInstant() ?: now,
            updatedAt = now,
        )
        if (row.bookmarkedAt != null || title.isModified || thumbnail.isModified || authors.isModified)
            userAlbums += UserAlbumEntity(
                userId = userId,
                albumId = row.id,
                name = title.text.takeIf { title.isModified },
                thumbnail = thumbnail.text?.takeIf { thumbnail.isModified }?.let(Uri::parse),
                artists = authors.text?.takeIf { authors.isModified }?.let(::listOf),
                bookmarkedAt = row.bookmarkedAt?.toInstant(),
                createdAt = now,
                updatedAt = now,
            )
    }

    val songArtists = library.songArtists
        .filter { it.songId in songIds && it.artistId in artistIds }
        .map { SongArtistMapEntity(it.songId, it.artistId) }
    val songsWithArtists = songArtists.mapTo(HashSet()) { it.songId }

    // The first album a song is in
    val albumOfSong = library.albumEntries
        .filter { it.songId in songIds && it.albumId in albumIds }
        .distinctBy { it.songId }
        .associateBy { it.songId }

    val songs = ArrayList<SongEntity>()
    val userSongs = ArrayList<UserSongEntity>()
    for (row in songRows) {
        val title = row.title.cleaned()
        val authors = row.artists.cleaned()
        val thumbnail = row.thumbnail.cleaned()
        val album = albumOfSong[row.id]
        songs += SongEntity(
            id = row.id,
            origin = Origin.YOUTUBE,
            name = title.text,
            thumbnail = thumbnail.text?.let(Uri::parse),
            duration = parseLegacyDuration(row.duration),
            // The old flag can't tell false from unknown
            isExplicit = true.takeIf { row.isExplicit },
            albumId = album?.albumId,
            albumPosition = album?.position,
            createdAt = now,
            updatedAt = now,
        )
        userSongs += UserSongEntity(
            userId = userId,
            songId = row.id,
            name = title.text.takeIf { title.isModified },
            artists = authors.text?.takeIf { authors.isModified || row.id !in songsWithArtists }
                ?.let(::listOf),
            thumbnail = thumbnail.text?.takeIf { thumbnail.isModified }?.let(Uri::parse),
            likedAt = row.likedAt?.toInstant(),
            createdAt = now,
            updatedAt = now,
        )
    }

    val entriesOf = library.playlistEntries
        .filter { it.songId in songIds }
        .groupBy { it.playlistId }
    val playlists = library.playlists.mapIndexed { index, row ->
        MappedPlaylist(
            playlist = UserPlaylistEntity(
                userId = userId,
                origin = Origin.LOCAL,
                name = row.name.cleaned().text ?: "Playlist-${index + 1}",
                isMonthly = row.isMonthly,
                isPinned = row.isPinned,
                createdAt = now,
                updatedAt = now,
            ),
            songs = entriesOf[row.id].orEmpty()
                .sortedBy { it.position }
                .map { it.songId to it.position },
        )
    }

    val formats = library.formats.mapNotNull { row ->
        if (row.songId !in songIds || row.itag == null || row.mimeType.isNullOrBlank())
            return@mapNotNull null
        SongFormatEntity(
            songId = row.songId,
            // YouTube formats are told apart by their itag
            tag = row.itag.toString(),
            mimetype = row.mimeType,
            bitrate = row.bitrate?.toInt(),
            length = row.length,
            gain = row.loudness,
            createdAt = now,
            updatedAt = now,
        )
    }

    val plays = library.plays
        .filter { it.songId in songIds }
        .map {
            PlaybackHistoryEntity(
                songId = it.songId,
                userId = userId,
                timeSpent = it.timeSpent.milliseconds,
                createdAt = it.createdAt.toInstant(),
            )
        }

    // The old table keeps no time, only the order: spread the searches over the last milliseconds
    val searches = library.searches.sortedBy { it.id }.let { sorted ->
        sorted.mapIndexed { index, search ->
            SearchHistoryEntity(
                query = search.query,
                userId = userId,
                lastUsed = now - (sorted.size - index).milliseconds,
            )
        }
    }

    return MappedProfile(
        user = UserEntity(id = userId, name = userName, createdAt = now, updatedAt = now),
        albums = albums,
        artists = artists,
        songs = songs,
        songArtists = songArtists,
        userSongs = userSongs,
        userAlbums = userAlbums,
        userArtists = userArtists,
        playlists = playlists,
        formats = formats,
        plays = plays,
        searches = searches,
    )
}
