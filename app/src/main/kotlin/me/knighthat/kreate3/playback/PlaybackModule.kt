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
import androidx.media3.datasource.DataSourceException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import me.knighthat.kreate3.auto.AutoLibrary
import me.knighthat.kreate3.auto.VoiceSearch
import me.knighthat.kreate3.database.repository.SongRepository
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.preferences.Preferences
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

    single { QueueContinuationState() }
    single { Radio(get<UserSongRepository>(), get<OpenSubsonicProvider>(), get<YouTubeProvider>()) }
    single { OpenedFiles(androidContext(), get<UserSongRepository>()) }
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
        VoiceSearch(
            songs = get(),
            albums = get(),
            artists = get(),
            playlists = get(),
            statistics = get(),
            openSubsonic = get(),
            youTube = get(),
            radio = get(),
            library = get(),
        )
    }

    single {
        PlaybackControllerImpl(
            context = androidContext(),
            radio = get(),
            continuationState = get(),
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
            onQueueReplaced = get<QueueContinuationState>()::replaced,
            player = ExoPlayer.Builder(androidContext())
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
 * 128 kbps stream: this lets loading stop within about 8 seconds of what
 * [Preferences.PLAYBACK_BUFFER_AHEAD_SECONDS] asks.
 */
private const val LOADING_CHECK_INTERVAL_BYTES = 128 * 1024

/** Every song is a single file or stream, none is DASH, HLS or another manifest. */
private fun songMediaSourceFactory(songs: DataSource.Factory): MediaSource.Factory =
    ProgressiveMediaSource.Factory(songs)
        .setContinueLoadingCheckIntervalBytes(LOADING_CHECK_INTERVAL_BYTES)
        .setLoadErrorHandlingPolicy(SongLoadErrorPolicy)

/**
 * Media3's retries, except for a song its source says is unavailable
 * ([ERROR_CODE_UNAVAILABLE]): loading it again gets the same answer, each try asking every
 * YouTube client, until [PlaybackRecovery]'s stall limit stops it as too slow. It fails at once
 * instead, so [PlaybackRecovery] sees why.
 */
@OptIn(UnstableApi::class)
private object SongLoadErrorPolicy : DefaultLoadErrorHandlingPolicy() {

    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val unavailable = generateSequence<Throwable>(loadErrorInfo.exception) { it.cause }
            .any { it is DataSourceException && it.reason == ERROR_CODE_UNAVAILABLE }
        return if (unavailable) C.TIME_UNSET else super.getRetryDelayMsFor(loadErrorInfo)
    }
}
