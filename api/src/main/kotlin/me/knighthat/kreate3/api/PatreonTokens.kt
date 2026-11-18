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

package me.knighthat.kreate3.api

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.io.encoding.Base64

/**
 * What a Patreon sign in gives: the [token] Kreate's API takes (a JWT, 24 hours) and the
 * [refresh] blob that gets the next pair. The module keeps neither, the app stores both in its
 * secrets and replaces the blob after every refresh.
 */
class PatreonTokens(val token: String, val refresh: String) {

    /** What [token] says about the user, read without checking it. */
    val claims: PatreonClaims by lazy { PatreonClaims.of(token) }

    override fun toString(): String = "PatreonTokens(hidden)"
}

/** Patreon's membership status when the token was issued. */
enum class PatronStatus {
    ACTIVE_PATRON,
    DECLINED_PATRON,
    FORMER_PATRON,

    /** No membership, an old token without the claim, or a value Patreon added since. */
    NONE,
}

/**
 * The token's payload, only to adjust the UI (e.g. know when to refresh, or gray out what needs a
 * higher tier): the API decides what the token allows, so it's never verified here.
 *
 * @property userId Patreon's user id, `null` when missing
 * @property tier membership tier, 0 for non-patrons; file features need 3 or higher
 * @property expiresAt when the token expires, in seconds since the Unix epoch, 0 when unknown
 */
data class PatreonClaims(
    val userId: String?,
    val tier: Int,
    val status: PatronStatus,
    val expiresAt: Long,
) {

    override fun toString(): String =
        "PatreonClaims(tier=$tier, status=$status, expiresAt=$expiresAt)"

    companion object {

        private val json = Json { ignoreUnknownKeys = true }
        private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

        /** Nothing known: tier 0, no membership, unknown expiry. */
        val UNKNOWN = PatreonClaims(null, 0, PatronStatus.NONE, 0)

        /** The claims of the JWT [token], [UNKNOWN] when it can't be read. */
        fun of(token: String): PatreonClaims {
            val payload = token.split('.').getOrNull(1) ?: return UNKNOWN
            val claims = try {
                json.parseToJsonElement(base64.decode(payload).decodeToString()).jsonObject
            } catch (_: IllegalArgumentException) {
                return UNKNOWN
            } catch (_: SerializationException) {
                return UNKNOWN
            }
            return PatreonClaims(
                userId = claims.text("sub"),
                tier = claims.number("tier")?.toInt() ?: 0,
                status = when (claims.text("patron_status")) {
                    "active_patron" -> PatronStatus.ACTIVE_PATRON
                    "declined_patron" -> PatronStatus.DECLINED_PATRON
                    "former_patron" -> PatronStatus.FORMER_PATRON
                    else -> PatronStatus.NONE
                },
                expiresAt = claims.number("exp") ?: 0,
            )
        }

        private fun JsonObject.number(name: String): Long? =
            try {
                get(name)?.jsonPrimitive?.longOrNull
            } catch (_: IllegalArgumentException) {
                null
            }
    }
}
