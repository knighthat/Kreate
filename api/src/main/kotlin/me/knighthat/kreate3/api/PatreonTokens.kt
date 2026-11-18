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
 * What a Patreon sign in or a refresh gives: the [token] Kreate's API takes (a JWT, 24 hours), the
 * [refresh] blob that gets the next pair and who the user is ([profile], for display only). The
 * module keeps none of them, the app stores all three in its secrets and replaces them after every
 * refresh.
 *
 * @property profile `null` when the API's answer had none (or one without an `id`)
 */
class PatreonTokens(
    val token: String,
    val refresh: String,
    val profile: PatreonProfile? = null,
) {

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

/** The status the API names [value] (`active_patron`…), [PatronStatus.NONE] for anything else. */
internal fun patronStatusOf(value: String?): PatronStatus =
    when (value) {
        "active_patron" -> PatronStatus.ACTIVE_PATRON
        "declined_patron" -> PatronStatus.DECLINED_PATRON
        "former_patron" -> PatronStatus.FORMER_PATRON
        else -> PatronStatus.NONE
    }

/**
 * The token's payload, only to adjust the UI (e.g. know when to refresh, or gray out what needs a
 * higher tier): the API decides what the token allows, so it's never verified here.
 *
 * @property userId Patreon's user id, `null` when missing
 * @property tier membership tier, 0 for non-patrons; decide with [features] instead
 * @property features ids of what the tier unlocks ([PatreonFeature]), empty for tier 0 and for
 *   tokens issued before the claim existed (it fills in at the next refresh)
 * @property expiresAt when the token expires, in seconds since the Unix epoch, 0 when unknown
 */
data class PatreonClaims(
    val userId: String?,
    val tier: Int,
    val features: Set<String>,
    val status: PatronStatus,
    val expiresAt: Long,
) {

    /** Whether the tier unlocks [feature], one of [PatreonFeature]. */
    fun has(feature: String): Boolean = feature in features

    override fun toString(): String =
        "PatreonClaims(tier=$tier, features=$features, status=$status, expiresAt=$expiresAt)"

    companion object {

        private val json = Json { ignoreUnknownKeys = true }
        private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

        /** Nothing known: tier 0, nothing unlocked, no membership, unknown expiry. */
        val UNKNOWN = PatreonClaims(null, 0, emptySet(), PatronStatus.NONE, 0)

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
                features = claims.texts("features"),
                status = patronStatusOf(claims.text("patron_status")),
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
