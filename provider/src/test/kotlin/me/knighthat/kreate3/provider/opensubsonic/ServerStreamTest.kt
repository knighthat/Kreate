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
package me.knighthat.kreate3.provider.opensubsonic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import me.knighthat.kreate3.database.model.SongFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerStreamTest {

    private fun target(json: String) = Json.parseToJsonElement(json).jsonObject

    @Test
    fun decidedFormatIsTaggedWithItsCodecAndKbps() {
        // As Navidrome 0.64 answers for a FLAC at most 96 kbps
        val format = target(
            """{"protocol":"http","container":"ogg","codec":"opus","audioChannels":2,"audioBitrate":96000,"audioSamplerate":48000}"""
        ).toTranscodedFormat("s1")

        assertEquals(SongFormat("s1", "opus-96", "audio/ogg; codecs=\"opus\"", 96_000), format)
    }

    @Test
    fun decidedMp3IsOfItsMediaType() {
        val format = target("""{"container":"mp3","codec":"mp3","audioBitrate":127600}""")
            .toTranscodedFormat("s1")

        assertEquals(SongFormat("s1", "mp3-128", "audio/mpeg; codecs=\"mp3\"", 127_600), format)
    }

    @Test
    fun decisionWithoutCodecOrBitrateHasNoFormat() {
        assertNull(target("""{"container":"ogg","audioBitrate":96000}""").toTranscodedFormat("s1"))
        assertNull(target("""{"container":"ogg","codec":"opus"}""").toTranscodedFormat("s1"))
    }

    @Test
    fun answeredFormatIsNamedByItsMediaType() {
        assertEquals(
            SongFormat("s1", "mp3-128", "audio/mpeg", 128_000),
            answeredFormat("s1", 128, "audio/mpeg", "audio/flac"),
        )
        // Opus or Vorbis, Ogg doesn't say
        assertEquals("ogg-64", answeredFormat("s1", 64, "audio/ogg", "audio/flac")?.tag)
        assertEquals("aac-64", answeredFormat("s1", 64, "audio/x-aac", "audio/flac")?.tag)
    }

    @Test
    fun answerOfTheSourceTypeIsTheFileAsItIs() {
        assertNull(answeredFormat("s1", 320, "audio/MPEG; charset=binary", "audio/mpeg"))
        assertNull(answeredFormat("s1", 320, "application/octet-stream", "audio/flac"))
        assertNull(answeredFormat("s1", 320, null, "audio/flac"))
    }
}
