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

package me.knighthat.kreate.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import me.knighthat.kreate.database.repository.AlbumRepository
import me.knighthat.kreate.database.repository.ArtistRepository
import me.knighthat.kreate.database.repository.HistoryRepository
import me.knighthat.kreate.database.repository.PlaylistRepository
import me.knighthat.kreate.database.repository.QueueRepository
import me.knighthat.kreate.database.repository.RoomAlbumRepository
import me.knighthat.kreate.database.repository.RoomArtistRepository
import me.knighthat.kreate.database.repository.RoomHistoryRepository
import me.knighthat.kreate.database.repository.RoomPlaylistRepository
import me.knighthat.kreate.database.repository.RoomQueueRepository
import me.knighthat.kreate.database.repository.RoomSongRepository
import me.knighthat.kreate.database.repository.RoomUserRepository
import me.knighthat.kreate.database.repository.SongRepository
import me.knighthat.kreate.database.repository.UserRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Clock

internal const val DATABASE_FILE_NAME = "kreate.db"

/**
 * Pass it to `startKoin` to make the repositories available, `androidContext` must be set.
 *
 * The app must also provide an [ActiveUser], the repositories read and write that user's data.
 */
val databaseModule = module {
    single<AppDatabase> {
        Room.databaseBuilder<AppDatabase>(context = get<Context>(), name = DATABASE_FILE_NAME)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
    }
    // Injected so tests can replace it with a fixed clock and assert created_at / updated_at
    single<Clock> { Clock.System }

    single { get<AppDatabase>().albumDao() }
    single { get<AppDatabase>().artistDao() }
    single { get<AppDatabase>().lyricsDao() }
    single { get<AppDatabase>().persistentQueueDao() }
    single { get<AppDatabase>().playbackHistoryDao() }
    single { get<AppDatabase>().searchHistoryDao() }
    single { get<AppDatabase>().songDao() }
    single { get<AppDatabase>().songFormatDao() }
    single { get<AppDatabase>().userAlbumDao() }
    single { get<AppDatabase>().userArtistDao() }
    single { get<AppDatabase>().userDao() }
    single { get<AppDatabase>().userPlaylistDao() }
    single { get<AppDatabase>().userSongDao() }

    singleOf(::RoomAlbumRepository) bind AlbumRepository::class
    singleOf(::RoomArtistRepository) bind ArtistRepository::class
    singleOf(::RoomHistoryRepository) bind HistoryRepository::class
    singleOf(::RoomPlaylistRepository) bind PlaylistRepository::class
    singleOf(::RoomQueueRepository) bind QueueRepository::class
    singleOf(::RoomSongRepository) bind SongRepository::class
    singleOf(::RoomUserRepository) bind UserRepository::class
}
