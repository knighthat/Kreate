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

package me.knighthat.kreate3.playback

import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import me.knighthat.kreate3.auto.AutoLibrary
import me.knighthat.kreate3.database.repository.SongRepository
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.provider.Providers
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Duration.Companion.seconds


val playbackModule = module {

    single { SongFormatRecorder(get<SongRepository>(), get<OpenSubsonicProvider>()) }
    single { SongCaches(androidContext()) }
    single {
        val context = androidContext()
        val youTube = get<YouTubeProvider>()
        DataSourceResolver(
            get<Providers>(),
            youTube,
            get<OpenSubsonicProvider>(),
            get<SongFormatRecorder>(),
            get<SongCaches>(),
            { origin -> context.streamQuality(origin, youTube) },
        )
    }
    single { CacheCopies(get<SongCaches>(), androidContext(), get<YouTubeProvider>()) }

    single { Radio(get<UserSongRepository>(), get<OpenSubsonicProvider>(), get<YouTubeProvider>()) }
    single {
        AutoLibrary(
            context = androidContext(),
            songs = get(),
            albums = get(),
            artists = get(),
            playlists = get(),
            openSubsonic = get(),
            youTube = get(),
            radio = get(),
        )
    }

    single {
        PlaybackControllerImpl(
            context = androidContext(),
            radio = get(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        )
    } bind PlaybackController::class
    single { PlaybackLifecycle(get(), get()) }

    // Not a singleton: PlaybackService owns the player and releases it when the service ends,
    // a released player can't be used again. Screens talk to it through a MediaController.
    factory { (build: PlayerBuild) ->
        val openSubsonic = get<OpenSubsonicProvider>()
        val youTube = get<YouTubeProvider>()
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
                // Buffers by time, as the settings ask
                .setLoadControl(
                    SongLoadControl(
                        keptPlayedMs = if (build.keepPlayed)
                            SongLoadControl.KEPT_PLAYED_MS
                        else
                            0
                    )
                )
                // Items carry no address, it is found when a song is read
                .setMediaSourceFactory(
                    songMediaSourceFactory(
                        songDataSourceFactory(
                            context = androidContext(),
                            client = get(),
                            // Asked for each request, they change with what the user trusts and
                            // the proxies. Called on the loader's thread, which waits for the
                            // stream anyway: the proxy's sign in may still be read from disk
                            openSubsonic = { runBlocking { openSubsonic.streamClient() }.newCall(it) },
                            youTube = { runBlocking { youTube.streamClient() }.newCall(it) },
                            resolver = get(),
                            caches = get(),
                            copies = get(),
                        )
                    )
                )
                .build()
        )
    }
}

/**
 * Bytes a song loads between two checks of whether to go on. Media3's 1 MiB is a minute of a
 * 128 kbps stream, so a preload of a few seconds would load that much anyway: this lets it stop
 * within about 8 seconds of what [Preferences.PLAYBACK_PRELOAD_SECONDS] asks.
 */
private const val LOADING_CHECK_INTERVAL_BYTES = 128 * 1024

/** Every song is a single file or stream, none is DASH, HLS or another manifest. */
private fun songMediaSourceFactory(songs: DataSource.Factory): MediaSource.Factory =
    ProgressiveMediaSource.Factory(songs)
        .setContinueLoadingCheckIntervalBytes(LOADING_CHECK_INTERVAL_BYTES)

/** Loads [seconds] of the next song ahead, nothing when it isn't positive. */
internal fun preloadConfiguration(seconds: Int): ExoPlayer.PreloadConfiguration =
    if (seconds <= 0) ExoPlayer.PreloadConfiguration.DEFAULT
    else ExoPlayer.PreloadConfiguration(seconds.seconds.inWholeMicroseconds)
