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
package me.knighthat.kreate3.provider.youtube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.io.encoding.Base64

/** BotGuard's challenge, in the shapes `Create` answers with. */
class BotGuardTest {

    private val challenge =
        """["msg",[null,"var vm = 1"],null,"hash","program","vmName",null,"blob"]"""

    private fun JsonObject.string(key: String) = this[key].asString

    private fun parsed(raw: String): JsonObject =
        Json.parseToJsonElement(parseChallengeData(raw)).jsonObject

    @Test
    fun plainChallengeIsTheFirstItem() {
        val data = parsed("[$challenge]")

        assertEquals("msg", data.string("messageId"))
        assertEquals("hash", data.string("interpreterHash"))
        assertEquals("program", data.string("program"))
        assertEquals("vmName", data.string("globalName"))
        assertEquals("blob", data.string("clientExperimentsStateBlob"))
        val script = data["interpreterJavascript"]!!.jsonObject
        assertEquals("var vm = 1", script.string("privateDoNotAccessOrElseSafeScriptWrappedValue"))
        assertEquals(JsonNull, script["privateDoNotAccessOrElseTrustedResourceUrlWrappedValue"])
    }

    @Test
    fun scrambledChallengeIsDescrambled() {
        // Base64 of the text with 97 taken off each byte, in YouTube's alphabet
        val scrambled =
            Base64.UrlSafe.encode(challenge.encodeToByteArray().map { (it - 97).toByte() }
                .toByteArray())
                .replace('=', '.')

        val data = parsed("""[null,"$scrambled"]""")

        assertEquals("msg", data.string("messageId"))
        assertEquals("vmName", data.string("globalName"))
    }

    @Test
    fun anythingElseIsRefused() {
        assertThrows(PoTokenException::class.java) { parseChallengeData("{}") }
        assertThrows(PoTokenException::class.java) { parseChallengeData("""[["msg"]]""") }
        assertThrows(PoTokenException::class.java) { parseChallengeData("""[null,"%%%"]""") }
    }
}
