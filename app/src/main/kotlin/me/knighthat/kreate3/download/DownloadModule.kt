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

@file:OptIn(UnstableApi::class)

package me.knighthat.kreate3.download

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.scheduler.Requirements
import kotlinx.coroutines.runBlocking
import me.knighthat.kreate3.notification.DownloadNotifications
import me.knighthat.kreate3.playback.SongCaches
import me.knighthat.kreate3.playback.downloadDataSourceFactory
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val downloadModule = module {

    single { DownloadNotifications(androidContext(), get()) }
    single {
        val caches = get<SongCaches>()
        val openSubsonic = get<OpenSubsonicProvider>()
        val youTube = get<YouTubeProvider>()
        DownloadManager(
            androidContext(),
            caches.database,
            caches.downloads,
            downloadDataSourceFactory(
                context = androidContext(),
                client = get(),
                // Asked for each request like the player's, on the downloader's thread
                openSubsonic = { runBlocking { openSubsonic.streamClient() }.newCall(it) },
                youTube = { runBlocking { youTube.streamClient() }.newCall(it) },
                resolver = get(),
                caches = caches,
            ),
            // Each song on its own thread
            Runnable::run,
        ).apply {
            // A cached song moves without a network, any other fails offline. How many run at
            // once and their retries follow the settings, see SongDownloads
            requirements = Requirements(0)
        }
    }
    single {
        DownloadedSongStore(
            context = androidContext(),
            store = get(),
            formats = get(),
            songs = get(),
            userSongs = get(),
            lyrics = get(),
            covers = get(),
        )
    }
    single {
        SongDownloads(
            context = androidContext(),
            manager = get(),
            caches = get(),
            youTube = get(),
            notifications = get(),
            stored = get(),
            userSongs = get(),
            notifier = get(),
            scope = get(),
        )
    }
}
