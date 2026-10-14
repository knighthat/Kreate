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

package me.knighthat.kreate3.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import me.knighthat.kreate3.database.dao.AlbumDao
import me.knighthat.kreate3.database.dao.ArtistDao
import me.knighthat.kreate3.database.dao.LyricsDao
import me.knighthat.kreate3.database.dao.PersistentQueueDao
import me.knighthat.kreate3.database.dao.PlaybackHistoryDao
import me.knighthat.kreate3.database.dao.SearchHistoryDao
import me.knighthat.kreate3.database.dao.SongDao
import me.knighthat.kreate3.database.dao.SongFormatDao
import me.knighthat.kreate3.database.dao.UserAlbumDao
import me.knighthat.kreate3.database.dao.UserArtistDao
import me.knighthat.kreate3.database.dao.UserDao
import me.knighthat.kreate3.database.dao.UserPlaylistDao
import me.knighthat.kreate3.database.dao.UserSongDao
import me.knighthat.kreate3.database.entity.AlbumEntity
import me.knighthat.kreate3.database.entity.ArtistEntity
import me.knighthat.kreate3.database.entity.LyricsEntity
import me.knighthat.kreate3.database.entity.PersistentQueueEntity
import me.knighthat.kreate3.database.entity.PlaybackHistoryEntity
import me.knighthat.kreate3.database.entity.SearchHistoryEntity
import me.knighthat.kreate3.database.entity.SongArtistMapEntity
import me.knighthat.kreate3.database.entity.SongEntity
import me.knighthat.kreate3.database.entity.SongFormatEntity
import me.knighthat.kreate3.database.entity.SongPlaylistMapEntity
import me.knighthat.kreate3.database.entity.UserAlbumEntity
import me.knighthat.kreate3.database.entity.UserArtistEntity
import me.knighthat.kreate3.database.entity.UserEntity
import me.knighthat.kreate3.database.entity.UserPlaylistEntity
import me.knighthat.kreate3.database.entity.UserSongEntity
import me.knighthat.kreate3.database.view.UserAlbumViewEntity
import me.knighthat.kreate3.database.view.UserArtistViewEntity
import me.knighthat.kreate3.database.view.UserSongViewEntity

@Database(
    entities = [
        AlbumEntity::class,
        ArtistEntity::class,
        LyricsEntity::class,
        PersistentQueueEntity::class,
        PlaybackHistoryEntity::class,
        SearchHistoryEntity::class,
        SongEntity::class,
        SongArtistMapEntity::class,
        SongFormatEntity::class,
        SongPlaylistMapEntity::class,
        UserEntity::class,
        UserAlbumEntity::class,
        UserArtistEntity::class,
        UserPlaylistEntity::class,
        UserSongEntity::class,
    ],
    views = [
        UserAlbumViewEntity::class,
        UserArtistViewEntity::class,
        UserSongViewEntity::class,
    ],
    version = 1,
)
@TypeConverters(Converters::class)
internal abstract class AppDatabase : RoomDatabase() {

    abstract fun albumDao(): AlbumDao

    abstract fun artistDao(): ArtistDao

    abstract fun lyricsDao(): LyricsDao

    abstract fun persistentQueueDao(): PersistentQueueDao

    abstract fun playbackHistoryDao(): PlaybackHistoryDao

    abstract fun searchHistoryDao(): SearchHistoryDao

    abstract fun songDao(): SongDao

    abstract fun songFormatDao(): SongFormatDao

    abstract fun userAlbumDao(): UserAlbumDao

    abstract fun userArtistDao(): UserArtistDao

    abstract fun userDao(): UserDao

    abstract fun userPlaylistDao(): UserPlaylistDao

    abstract fun userSongDao(): UserSongDao
}
