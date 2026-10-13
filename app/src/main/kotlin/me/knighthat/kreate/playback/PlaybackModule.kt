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

package me.knighthat.kreate.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module


val playbackModule = module {

    single<PlaybackController> {
        PlaybackControllerImpl(
            context = androidContext(),
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        )
    }

    // Not a singleton: PlaybackService owns the player and releases it when the service ends,
    // a released player can't be used again. Screens talk to it through a MediaController.
    factory {
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
                // Keeps the CPU awake while playing with the screen off
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .build()
        )
    }
}
