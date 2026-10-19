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

package me.knighthat.kreate3.playback

import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import me.knighthat.kreate3.database.repository.SongRepository
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.provider.Providers
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module


val playbackModule = module {

    single { SongFormatRecorder(get<SongRepository>(), get<OpenSubsonicProvider>()) }
    single { SongCaches(androidContext()) }
    single {
        DataSourceResolver(
            get<Providers>(),
            get<YouTubeProvider>(),
            get<SongFormatRecorder>(),
            get<SongCaches>(),
        )
    }

    single { Radio(get<UserSongRepository>(), get<OpenSubsonicProvider>(), get<YouTubeProvider>()) }

    single {
        PlaybackControllerImpl(
            context = androidContext(),
            radio = get(),
            notifier = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        )
    } bind PlaybackController::class
    single { PlaybackLifecycle(get()) }

    // Not a singleton: PlaybackService owns the player and releases it when the service ends,
    // a released player can't be used again. Screens talk to it through a MediaController.
    factory {
        val openSubsonic = get<OpenSubsonicProvider>()
        KreatePlayer(
            ExoPlayer.Builder(androidContext())
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    // Pauses for calls and other apps, ducks for short sounds
                    true,
                )
                // Pauses when headphones are unplugged
                .setHandleAudioBecomingNoisy(true)
                // Keeps the CPU and Wi-Fi awake while playing with the screen off
                .setWakeMode(C.WAKE_MODE_NETWORK)
                // Items carry no address, it is found when a song is read
                .setMediaSourceFactory(
                    songMediaSourceFactory(
                        songDataSourceFactory(
                            context = androidContext(),
                            client = get(),
                            // Asked for each request, it changes with what the user trusts
                            openSubsonic = { openSubsonic.streamClient().newCall(it) },
                            resolver = get(),
                            caches = get(),
                        )
                    )
                )
                .build()
        )
    }
}

@OptIn(UnstableApi::class)
private fun songMediaSourceFactory(songs: DataSource.Factory): MediaSource.Factory =
    DefaultMediaSourceFactory(songs)
