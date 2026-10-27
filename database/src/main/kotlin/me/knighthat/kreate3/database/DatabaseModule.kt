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

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import me.knighthat.kreate3.database.repository.AlbumRepository
import me.knighthat.kreate3.database.repository.AlbumRepositoryImpl
import me.knighthat.kreate3.database.repository.ArtistRepository
import me.knighthat.kreate3.database.repository.ArtistRepositoryImpl
import me.knighthat.kreate3.database.repository.HistoryRepository
import me.knighthat.kreate3.database.repository.HistoryRepositoryImpl
import me.knighthat.kreate3.database.repository.LocalCatalogRepository
import me.knighthat.kreate3.database.repository.LocalCatalogRepositoryImpl
import me.knighthat.kreate3.database.repository.PlaylistRepository
import me.knighthat.kreate3.database.repository.PlaylistRepositoryImpl
import me.knighthat.kreate3.database.repository.QueueRepository
import me.knighthat.kreate3.database.repository.QueueRepositoryImpl
import me.knighthat.kreate3.database.repository.RemoteCatalogRepository
import me.knighthat.kreate3.database.repository.RemoteCatalogRepositoryImpl
import me.knighthat.kreate3.database.repository.SongRepository
import me.knighthat.kreate3.database.repository.SongRepositoryImpl
import me.knighthat.kreate3.database.repository.StatisticsRepository
import me.knighthat.kreate3.database.repository.StatisticsRepositoryImpl
import me.knighthat.kreate3.database.repository.UserAlbumRepository
import me.knighthat.kreate3.database.repository.UserAlbumRepositoryImpl
import me.knighthat.kreate3.database.repository.UserArtistRepository
import me.knighthat.kreate3.database.repository.UserArtistRepositoryImpl
import me.knighthat.kreate3.database.repository.UserRepository
import me.knighthat.kreate3.database.repository.UserRepositoryImpl
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.database.repository.UserSongRepositoryImpl
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Clock

internal const val DATABASE_FILE_NAME = "kreate.db"

/**
 * Schema version of [AppDatabase].
 */
internal const val DATABASE_VERSION = 1

/**
 * Pass it to `startKoin` to make the repositories and [DatabaseBackup] available, `androidContext`
 * must be set.
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
    single { DatabaseBackup(get()) }

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

    singleOf(::AlbumRepositoryImpl) bind AlbumRepository::class
    singleOf(::UserAlbumRepositoryImpl) bind UserAlbumRepository::class
    singleOf(::ArtistRepositoryImpl) bind ArtistRepository::class
    singleOf(::UserArtistRepositoryImpl) bind UserArtistRepository::class
    singleOf(::HistoryRepositoryImpl) bind HistoryRepository::class
    singleOf(::StatisticsRepositoryImpl) bind StatisticsRepository::class
    singleOf(::LocalCatalogRepositoryImpl) bind LocalCatalogRepository::class
    singleOf(::RemoteCatalogRepositoryImpl) bind RemoteCatalogRepository::class
    singleOf(::PlaylistRepositoryImpl) bind PlaylistRepository::class
    singleOf(::QueueRepositoryImpl) bind QueueRepository::class
    singleOf(::SongRepositoryImpl) bind SongRepository::class
    singleOf(::UserSongRepositoryImpl) bind UserSongRepository::class
    singleOf(::UserRepositoryImpl) bind UserRepository::class
}
