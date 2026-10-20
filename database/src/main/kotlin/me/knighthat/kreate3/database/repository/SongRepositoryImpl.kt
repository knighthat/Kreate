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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.knighthat.kreate3.database.AppDatabase
import me.knighthat.kreate3.database.dao.LyricsDao
import me.knighthat.kreate3.database.dao.SongDao
import me.knighthat.kreate3.database.dao.SongFormatDao
import me.knighthat.kreate3.database.entity.ArtistEntity
import me.knighthat.kreate3.database.entity.SongArtistMapEntity
import me.knighthat.kreate3.database.entity.SongFormatEntity
import me.knighthat.kreate3.database.inTransaction
import me.knighthat.kreate3.database.model.Artist
import me.knighthat.kreate3.database.model.Lyrics
import me.knighthat.kreate3.database.model.Song
import me.knighthat.kreate3.database.model.SongFormat
import me.knighthat.kreate3.model.LyricsProvider
import me.knighthat.kreate3.model.Origin
import kotlin.time.Clock

/**
 * Room backed implementation of [SongRepository].
 */
internal class SongRepositoryImpl(
    private val database: AppDatabase,
    private val songDao: SongDao,
    private val songFormatDao: SongFormatDao,
    private val lyricsDao: LyricsDao,
    private val clock: Clock,
) : SongRepository {

    override fun find(songId: String): Flow<Song?> = songDao.find(songId).map { it?.toModel() }

    override suspend fun save(song: Song) {
        val now = clock.now()
        val createdAt = songDao.get(song.id)?.createdAt ?: now
        songDao.upsert(song.toEntity(createdAt, now))
    }

    override suspend fun delete(songId: String) = songDao.delete(songId)

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

    override suspend fun formatLengths(tag: String): Map<String, Long> =
        songFormatDao.lengths(tag).associate { it.songId to it.length }

    override suspend fun idsWithoutGain(origin: Origin, tag: String): List<String> =
        songFormatDao.idsWithoutGain(origin, tag)

    override suspend fun deleteFormats(songId: String) = songFormatDao.deleteAll(songId)

    override fun lyricsOf(songId: String, source: LyricsProvider): Flow<Lyrics?> =
        lyricsDao.find(songId, source).map { it?.toModel() }

    override suspend fun saveLyrics(lyrics: Lyrics) {
        val now = clock.now()
        val createdAt = lyricsDao.get(lyrics.songId, lyrics.source)?.createdAt ?: now
        lyricsDao.upsert(lyrics.toEntity(createdAt, now))
    }

    override suspend fun deleteLyrics(songId: String) = lyricsDao.delete(songId)

    override suspend fun update(songId: String, transform: (Song) -> Song) {
        database.inTransaction {
            val current = requireNotNull(songDao.get(songId)) { "Song $songId doesn't exist" }
            val updated = transform(current.toModel())
            require(updated.id == songId) { "The id of a song cannot be changed" }

            songDao.upsert(updated.toEntity(current.createdAt, clock.now()))
        }
    }
}
