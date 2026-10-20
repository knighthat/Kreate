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

package me.knighthat.kreate3.lyrics

import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import org.koin.dsl.module

/**
 * Pass it to `initKoin` to make [LyricsFinder] available. A new [LyricsSource] is added to its
 * list, which is the order the sources are asked in: the ones timed per word first, so a search
 * stops sooner.
 */
val lyricsModule = module {
    single {
        LyricsFinder(
            listOf(
                BetterLyricsSource(get<HttpClient>(), get<Json>()),
                LyricsPlusSource(get(), get()),
                LrcLibSource(get(), get()),
                YouTubeLyricsSource(get()),
            )
        )
    }
}
