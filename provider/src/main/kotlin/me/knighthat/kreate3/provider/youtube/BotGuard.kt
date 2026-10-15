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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64

/**
 * A step of making a PO token went wrong.
 *
 * @property badWebView whether the device's WebView can't run BotGuard at all, so trying again
 * is pointless
 */
internal class PoTokenException(message: String, val badWebView: Boolean = false) :
    Exception(message)

/**
 * The challenge in an answer of BotGuard's `Create`, as the object `runBotGuard` of
 * `po_token.html` takes, written as JavaScript. The challenge is either the first item of the
 * answer, or its second item scrambled: base64 of the text with 97 taken off each byte.
 *
 * From NewPipe's and Metrolist's `JavaScriptUtil.parseChallengeData` (GPL-3.0).
 *
 * @throws PoTokenException when the answer isn't a challenge
 */
internal fun parseChallengeData(rawChallengeData: String): String {
    val challenge = try {
        val scrambled = Json.parseToJsonElement(rawChallengeData) as JsonArray
        val second = scrambled.getOrNull(1) as? JsonPrimitive
        if (second != null && second.isString)
            Json.parseToJsonElement(descramble(second.content)) as JsonArray
        else
            scrambled[0] as JsonArray
    } catch (e: PoTokenException) {
        throw e
    } catch (_: Exception) {
        throw PoTokenException("BotGuard's challenge can't be read")
    }

    fun string(index: Int): String = challenge.getOrNull(index).asString
        ?: throw PoTokenException("BotGuard's challenge has no item $index")

    /** The first text of a list wrapping a value, like the interpreter's script. */
    fun wrapped(index: Int): JsonElement =
        challenge.getOrNull(index).asArray
            ?.firstOrNull { (it as? JsonPrimitive)?.isString == true }
            ?: JsonNull

    return buildJsonObject {
        put("messageId", string(0))
        put(
            "interpreterJavascript", JsonObject(
                mapOf(
                    "privateDoNotAccessOrElseSafeScriptWrappedValue" to wrapped(1),
                    "privateDoNotAccessOrElseTrustedResourceUrlWrappedValue" to wrapped(2),
                )
            )
        )
        put("interpreterHash", string(3))
        put("program", string(4))
        put("globalName", string(5))
        put("clientExperimentsStateBlob", string(7))
    }.toString()
}

private fun descramble(scrambled: String): String {
    val normalized = scrambled.replace('+', '-').replace('/', '_').replace('.', '=')
    val bytes = try {
        Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(normalized)
    } catch (_: IllegalArgumentException) {
        throw PoTokenException("BotGuard's challenge can't be decoded")
    }
    return bytes.map { (it + 97).toByte() }.toByteArray().decodeToString()
}
