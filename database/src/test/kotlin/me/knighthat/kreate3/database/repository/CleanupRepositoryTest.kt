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

import android.net.Uri
import androidx.room.useReaderConnection
import kotlinx.coroutines.runBlocking
import me.knighthat.kreate3.database.DatabaseTest
import me.knighthat.kreate3.database.entity.LyricsEntity
import me.knighthat.kreate3.database.entity.PlaybackHistoryEntity
import me.knighthat.kreate3.database.entity.SongPlaylistMapEntity
import me.knighthat.kreate3.database.entity.UserAlbumEntity
import me.knighthat.kreate3.database.entity.UserArtistEntity
import me.knighthat.kreate3.database.entity.UserPlaylistEntity
import me.knighthat.kreate3.database.entity.UserSongEntity
import me.knighthat.kreate3.model.LyricsProvider
import me.knighthat.kreate3.model.LyricsSync
import me.knighthat.kreate3.model.Origin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

internal class CleanupRepositoryTest : DatabaseTest() {

    private lateinit var repository: CleanupRepositoryImpl

    @Before
    fun setUp() = runBlocking {
        addUser()
        addUser(OTHER)
        repository = CleanupRepositoryImpl(database, database.cleanupDao())
    }

    private suspend fun ids(sql: String): List<String> =
        database.useReaderConnection { connection ->
            connection.usePrepared(sql) { statement ->
                buildList { while (statement.step()) add(statement.getText(0)) }
            }
        }

    private suspend fun keep(
        songId: String,
        user: String = USER,
        edit: (UserSongEntity) -> UserSongEntity = { it }
    ) =
        database.userSongDao()
            .upsert(edit(UserSongEntity(user, songId, createdAt = EPOCH, updatedAt = EPOCH)))

    private suspend fun playlist(
        user: String = USER,
        picture: Uri? = null,
        vararg songs: String
    ): Long {
        val id = database.userPlaylistDao().upsert(
            UserPlaylistEntity(
                userId = user,
                origin = Origin.LOCAL,
                name = "list",
                thumbnail = picture,
                createdAt = EPOCH,
                updatedAt = EPOCH,
            )
        )
        database.userPlaylistDao()
            .addSongs(songs.mapIndexed { i, song -> SongPlaylistMapEntity(song, id, i) })
        return id
    }

    @Test
    fun unusedLibrarySongsAndTheirSongsGo() = runBlocking {
        addSong("bare", Origin.YOUTUBE)
        addSong("liked", Origin.YOUTUBE)
        addSong("renamed", Origin.YOUTUBE)
        addSong("played", Origin.YOUTUBE)
        addSong("listed", Origin.YOUTUBE)
        addSong("device")
        keep("bare")
        keep("liked") { it.copy(likedAt = EPOCH) }
        keep("renamed") { it.copy(name = "mine") }
        keep("played")
        keep("listed")
        keep("device")
        database.playbackHistoryDao().insert(
            PlaybackHistoryEntity(
                songId = "played",
                userId = USER,
                timeSpent = 5.seconds,
                createdAt = EPOCH
            )
        )
        playlist(USER, null, "listed")

        val result = repository.cleanUp()

        assertEquals(1, result.librarySongs)
        assertEquals(1, result.songs)
        assertEquals(
            listOf("device", "liked", "listed", "played", "renamed"),
            ids("SELECT song_id FROM user_song ORDER BY song_id"),
        )
        assertEquals(
            listOf("device", "liked", "listed", "played", "renamed"),
            ids("SELECT id FROM song ORDER BY id"),
        )
    }

    @Test
    fun aSongInAnotherUsersPlaylistLeavesOnlyTheLibraryRow() = runBlocking {
        addSong("shared", Origin.YOUTUBE)
        keep("shared")
        playlist(OTHER, null, "shared")

        repository.cleanUp()

        assertEquals(emptyList<String>(), ids("SELECT song_id FROM user_song"))
        assertEquals(listOf("shared"), ids("SELECT id FROM song"))
    }

    @Test
    fun songsWithImportedLyricsStay() = runBlocking {
        addSong("lyrics", Origin.YOUTUBE)
        addSong("fetched", Origin.YOUTUBE)
        listOf(
            "lyrics" to LyricsProvider.FILE,
            "fetched" to LyricsProvider.LRCLIB
        ).forEach { (song, source) ->
            database.lyricsDao().upsert(
                LyricsEntity(song, source, "la", LyricsSync.NONE, null, EPOCH, EPOCH)
            )
        }

        repository.cleanUp()

        assertEquals(listOf("lyrics"), ids("SELECT id FROM song"))
    }

    @Test
    fun unchangedAlbumsAndArtistsGoWithWhatNoSongUses() = runBlocking {
        addAlbum("kept", Origin.YOUTUBE)
        addAlbum("bare", Origin.YOUTUBE)
        addAlbum("device")
        addArtist("followed", Origin.YOUTUBE)
        addArtist("bare", Origin.YOUTUBE)
        listOf("kept", "bare", "device").forEach {
            database.userAlbumDao().upsert(
                UserAlbumEntity(
                    USER,
                    it,
                    bookmarkedAt = if (it == "kept") EPOCH else null,
                    createdAt = EPOCH,
                    updatedAt = EPOCH,
                )
            )
        }
        listOf("followed", "bare").forEach {
            database.userArtistDao().upsert(
                UserArtistEntity(
                    USER,
                    it,
                    followSince = if (it == "followed") EPOCH else null,
                    createdAt = EPOCH,
                    updatedAt = EPOCH,
                )
            )
        }

        val result = repository.cleanUp()

        assertEquals(1, result.libraryAlbums)
        assertEquals(1, result.libraryArtists)
        assertEquals(listOf("device", "kept"), ids("SELECT album_id FROM user_album ORDER BY 1"))
        assertEquals(listOf("device", "kept"), ids("SELECT id FROM album ORDER BY 1"))
        assertEquals(listOf("followed"), ids("SELECT id FROM artist"))
    }

    @Test
    fun emptyPlaylistsGoWithTheirPictures() = runBlocking {
        addSong("song", Origin.YOUTUBE)
        keep("song") { it.copy(likedAt = EPOCH) }
        val picture = Uri.parse("file:///covers/playlist-1.jpg")
        val empty = playlist(OTHER, picture)
        playlist(USER, null, "song")

        val result = repository.cleanUp()

        assertEquals(
            listOf(CleanupRepository.RemovedPlaylist(OTHER, empty, picture)),
            result.playlists
        )
        assertEquals(1, ids("SELECT playlist_id FROM user_playlist").size)
    }

    @Test
    fun nothingToDeleteIsEmpty() = runBlocking {
        addSong("device")
        keep("device")

        assertTrue(repository.cleanUp().isEmpty)
    }

    private companion object {
        const val OTHER = "other"
    }
}
