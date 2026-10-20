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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Reads the JSON of [LyricsPlusSource]: `lyrics` holds the lines, each with its `time`,
 * `duration` and `text`, and its words or syllables in `syllabus`, all in milliseconds. A
 * syllable with `isBackground` is sung by backing vocals, they become a line of their own placed
 * by the time it starts. `type` is `Word`, `Line` or `None`.
 *
 * @return `null` when it has no line
 */
internal fun parseLyricsPlus(text: String): LyricsContent? {
    val answer = Json.parseToJsonElement(text).jsonObject
    val entries = (answer["lyrics"] as? JsonArray)?.map { it.jsonObject }.orEmpty()
    if (entries.isEmpty()) return null

    val type = answer["type"]?.jsonPrimitive?.contentOrNull
    if (type.equals("none", ignoreCase = true))
        return PlainLyrics(entries.joinToString("\n") { it.string("text").orEmpty().trim() })

    val lines = entries.flatMap { entry ->
        val start = entry.millis("time") ?: return@flatMap emptyList()
        val end = entry.millis("duration")?.let { start + it }
        val syllables = (entry["syllabus"] as? JsonArray)?.map { it.jsonObject }.orEmpty()
        val (backing, sung) = syllables.partition {
            it["isBackground"]?.jsonPrimitive?.booleanOrNull == true
        }

        val words = sung.toWords()
        val main = LyricsLine(
            start = start,
            end = end,
            text = if (words.isEmpty()) entry.string("text").orEmpty().trim()
            else words.joinToString("") { it.text }.trim(),
            words = words,
        )
        val backingWords = backing.toWords()
        if (backingWords.isEmpty()) return@flatMap listOf(main)

        listOf(
            main,
            LyricsLine(
                start = backingWords.first().start,
                end = backingWords.last().end,
                text = backingWords.joinToString("") { it.text }.trim(),
                words = backingWords,
                background = true,
            ),
        )
    }
    return lines.takeIf { it.isNotEmpty() }?.let { SyncedLyrics(it.withEnds()) }
}

private fun List<JsonObject>.toWords(): List<LyricsWord> = mapIndexedNotNull { i, syllable ->
    val start = syllable.millis("time") ?: return@mapIndexedNotNull null
    var spoken = syllable.string("text") ?: return@mapIndexedNotNull null
    if (i == 0) spoken = spoken.trimStart()
    if (spoken.isEmpty()) return@mapIndexedNotNull null
    LyricsWord(start, syllable.millis("duration")?.let { start + it }, spoken)
}

private fun JsonObject.millis(key: String): Duration? =
    this[key]?.jsonPrimitive?.longOrNull?.milliseconds

private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
