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

package me.knighthat.kreate3.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactionTest {

    @Test
    fun queryParametersOfCredentialsAreHidden() {
        val text = "GET https://music.example/rest/stream?id=42&u=alice&t=abc123&s=salt&format=raw"
        val redacted = redact(text)

        assertEquals(
            "GET https://music.example/rest/stream?id=42&u=<redacted>&t=<redacted>&s=<redacted>&format=raw",
            redacted
        )
    }

    @Test
    fun streamAddressesLoseTheirTokens() {
        val redacted =
            redact("https://r1.googlevideo.com/videoplayback?itag=251&sig=AAA&pot=BBB&ip=1.2.3.4")

        assertTrue("itag=251" in redacted)
        assertFalse("AAA" in redacted)
        assertFalse("BBB" in redacted)
    }

    @Test
    fun userInfoOfAnAddressIsHidden() {
        assertEquals(
            "via http://<redacted>@proxy.lan:8080/",
            redact("via http://bob:secret@proxy.lan:8080/")
        )
    }

    @Test
    fun headersOfCredentialsAreHidden() {
        val text = "-> Authorization: Bearer xyz\n-> Accept: */*\n-> Cookie: SID=1; HSID=2"
        val lines = redact(text).lines()

        assertEquals("-> Authorization: <redacted>", lines[0])
        assertEquals("-> Accept: */*", lines[1])
        assertEquals("-> Cookie: <redacted>", lines[2])
    }

    @Test
    fun plainTextIsUntouched() {
        val text = "Couldn't play song 42 (OPEN_SUBSONIC) after 1200 ms"
        assertEquals(text, redact(text))
    }

    @Test
    fun secretHeadersAreRecognizedByName() {
        assertTrue(isSecretHeader("Authorization"))
        assertTrue(isSecretHeader("cookie"))
        assertTrue(isSecretHeader("X-Goog-Visitor-Id"))
        assertFalse(isSecretHeader("Content-Type"))
    }
}
