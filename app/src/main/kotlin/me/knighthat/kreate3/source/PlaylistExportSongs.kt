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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.knighthat.kreate3.database.repository.SongRepository
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.source.youtube.SONG_ARTWORK
import me.knighthat.kreate3.source.youtube.artwork

/**
 * The songs of one of the app's playlists to export, with the values their sources gave (the
 * `song` rows): a song's title, artists and picture are never the ones the user set in the app.
 * Disliked songs are left out, like everywhere else.
 */
internal class PlaylistExportSongs(
    private val library: UserSongRepository,
    private val songs: SongRepository,
    private val youTube: YouTubeProvider,
) {

    /** The songs of the app's playlist [playlistId], in its own order. */
    suspend fun of(playlistId: Long): List<ExportedSong> = withContext(Dispatchers.Default) {
        // The user's list gives the order and which songs show, the shared rows the values
        val ids = library.listOfPlaylist(playlistId).map { it.songId }
        val disliked = library.listDislikedIds()
        songs.sourceValues(ids)
            .filterNot { it.song.id in disliked }
            .distinctBy { it.song.origin to it.song.id }
            .map { source ->
                val song = source.song
                ExportedSong(
                    id = song.id,
                    origin = song.origin,
                    title = song.name.orEmpty(),
                    // A nameless artist keeps its place, the ids pair with the names by place
                    artists = source.artists.map { it.name.orEmpty() },
                    artistIds = source.artists.map { it.id },
                    albumId = song.albumId,
                    albumName = source.albumName,
                    // 0 is how some sources say they don't know
                    albumYear = source.albumYear?.takeIf { it > 0 },
                    albumPosition = song.albumPosition?.takeIf { it > 0 },
                    duration = song.duration,
                    thumbnail = (if (song.origin == Origin.YOUTUBE)
                        youTube.artwork(source.thumbnail, SONG_ARTWORK)
                    else
                        source.thumbnail)?.toString(),
                )
            }
    }
}
