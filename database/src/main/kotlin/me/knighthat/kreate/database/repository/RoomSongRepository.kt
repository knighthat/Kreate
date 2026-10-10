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

package me.knighthat.kreate.database.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.knighthat.kreate.database.ActiveUser
import me.knighthat.kreate.database.dao.LyricsDao
import me.knighthat.kreate.database.dao.SongDao
import me.knighthat.kreate.database.dao.SongFormatDao
import me.knighthat.kreate.database.dao.UserSongDao
import me.knighthat.kreate.database.entity.ArtistEntity
import me.knighthat.kreate.database.entity.SongArtistMapEntity
import me.knighthat.kreate.database.entity.SongFormatEntity
import me.knighthat.kreate.database.entity.UserSongEntity
import me.knighthat.kreate.database.model.Artist
import me.knighthat.kreate.database.model.Lyrics
import me.knighthat.kreate.database.model.Song
import me.knighthat.kreate.database.model.SongFormat
import me.knighthat.kreate.database.model.UserSong
import kotlin.time.Clock

/**
 * Room backed implementation of [SongRepository].
 */
internal class RoomSongRepository(
    private val songDao: SongDao,
    private val songFormatDao: SongFormatDao,
    private val lyricsDao: LyricsDao,
    private val userSongDao: UserSongDao,
    activeUser: ActiveUser,
    private val clock: Clock,
) : SongRepository {

    private val scope = UserScope(activeUser)

    override fun find(id: String): Flow<Song?> = songDao.find(id).map { it?.toModel() }

    override suspend fun save(song: Song) {
        val now = clock.now()
        val createdAt = songDao.get(song.id)?.createdAt ?: now
        songDao.upsert(song.toEntity(createdAt, now))
    }

    override suspend fun delete(id: String) = songDao.delete(id)

    override fun artistsOf(songId: String): Flow<List<Artist>> =
        songDao.artistsOf(songId).map { it.map(ArtistEntity::toModel) }

    override suspend fun linkArtist(songId: String, artistId: String) =
        songDao.link(SongArtistMapEntity(songId, artistId))

    override suspend fun unlinkArtist(songId: String, artistId: String) =
        songDao.unlink(SongArtistMapEntity(songId, artistId))

    override fun formatsOf(songId: String): Flow<List<SongFormat>> =
        songFormatDao.formatsOf(songId).map { it.map(SongFormatEntity::toModel) }

    override suspend fun saveFormat(format: SongFormat) {
        val now = clock.now()
        val createdAt = songFormatDao.get(format.songId, format.tag)?.createdAt ?: now
        songFormatDao.upsert(format.toEntity(createdAt, now))
    }

    override suspend fun deleteFormats(songId: String) = songFormatDao.deleteAll(songId)

    override fun lyricsOf(songId: String): Flow<Lyrics?> =
        lyricsDao.find(songId).map { it?.toModel() }

    override suspend fun saveLyrics(lyrics: Lyrics) {
        val now = clock.now()
        val createdAt = lyricsDao.get(lyrics.songId)?.createdAt ?: now
        lyricsDao.upsert(lyrics.toEntity(createdAt, now))
    }

    override suspend fun deleteLyrics(songId: String) = lyricsDao.delete(songId)

    override fun library(): Flow<List<UserSong>> =
        scope.observe(emptyList()) { userId ->
            userSongDao.all(userId).map { it.map(UserSongEntity::toModel) }
        }

    override fun liked(): Flow<List<UserSong>> =
        scope.observe(emptyList()) { userId ->
            userSongDao.liked(userId).map { it.map(UserSongEntity::toModel) }
        }

    override fun findInLibrary(songId: String): Flow<UserSong?> =
        scope.observe(null) { userId ->
            userSongDao.find(userId, songId).map { it?.toModel() }
        }

    override suspend fun saveToLibrary(song: UserSong) {
        val userId = scope.requireId()
        val now = clock.now()
        val createdAt = userSongDao.get(userId, song.songId)?.createdAt ?: now
        userSongDao.upsert(song.toEntity(userId, createdAt, now))
    }

    override suspend fun removeFromLibrary(songId: String) =
        userSongDao.delete(scope.requireId(), songId)
}
