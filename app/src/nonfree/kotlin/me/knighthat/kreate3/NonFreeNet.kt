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

package me.knighthat.kreate3

import me.knighthat.kreate3.presence.presenceModule
import me.knighthat.kreate3.recognition.ShazamSongRecognizer
import me.knighthat.kreate3.recognition.SongRecognizer
import me.knighthat.kreate3.shazam.shazamModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * What talks to non-free network services, left out of the foss build: Discord's presence and
 * Shazam's song recognition.
 *
 * @param isSetup whether the setup wizard was finished, see [presenceModule]
 */
fun nonFreeNetModules(isSetup: Boolean): List<Module> = listOf(
    presenceModule(isSetup),
    shazamModule,
    module {
        // Hold the Songs tab to name a song, see SongRecognitionViewModel
        single<SongRecognizer> { ShazamSongRecognizer(androidContext(), get()) }
    },
)
