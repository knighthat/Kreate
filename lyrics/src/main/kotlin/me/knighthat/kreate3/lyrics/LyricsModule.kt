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
import me.knighthat.kreate3.model.LyricsProvider
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * Pass it to `initKoin` to make [LyricsFinder] available, with the sources of this module. A
 * source of another module, like one needing a music provider, is declared where that module is
 * known, bound to [LyricsSource]: the finder takes every one, in the order of [LyricsProvider].
 */
val lyricsModule = module {
    single { BetterLyricsSource(get<HttpClient>(), get<Json>()) } bind LyricsSource::class
    single { LyricsPlusSource(get(), get()) } bind LyricsSource::class
    single { LrcLibSource(get(), get()) } bind LyricsSource::class

    single { LyricsFinder(getAll<LyricsSource>().sortedBy { it.provider }) }
}
