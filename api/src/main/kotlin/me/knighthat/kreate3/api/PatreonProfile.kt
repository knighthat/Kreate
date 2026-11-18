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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull

/**
 * Who signed in, as the API answers a sign in or a refresh, to show the user. It isn't signed:
 * never decide access with it, [PatreonClaims] and the API's answers do that. It's personal data,
 * so the app stores it with the token and never logs it.
 *
 * Every field but [id], [tier] and [features] may be `null`, non-patrons have `null` membership
 * fields.
 *
 * @property name full name on Patreon
 * @property username Patreon username
 * @property avatar address of the profile picture, it may stop working after a while: load it
 *   again after each refresh
 * @property tier membership tier, the token's `tier`
 * @property tierTitle the tier's name as set on Patreon, may be set while [tier] is 0 (e.g. after a
 *   declined payment): show what is unlocked from [features] and [status]
 * @property features ids of what the tier unlocks ([PatreonFeature]), the token's `features`; to
 *   show only, [PatreonClaims.features] decides. Empty in a profile kept before it existed
 * @property nextChargeDate when Patreon charges the user next, as ISO 8601
 */
data class PatreonProfile(
    val id: String,
    val name: String?,
    val username: String?,
    val avatar: String?,
    val tier: Int,
    val tierTitle: String?,
    val features: Set<String>,
    val status: PatronStatus,
    val nextChargeDate: String?,
) {

    /** What the profile is kept as, read back with [decode]. */
    fun encode(): String = buildJsonObject {
        put("id", JsonPrimitive(id))
        put("name", JsonPrimitive(name))
        put("username", JsonPrimitive(username))
        put("avatar", JsonPrimitive(avatar))
        put("tier", JsonPrimitive(tier))
        put("tierTitle", JsonPrimitive(tierTitle))
        put("features", JsonArray(features.map(::JsonPrimitive)))
        put("patronStatus", statusName(status))
        put("nextChargeDate", JsonPrimitive(nextChargeDate))
    }.toString()

    override fun toString(): String = "PatreonProfile(hidden)"

    companion object {

        private val json = Json { ignoreUnknownKeys = true }

        /** A profile kept with [encode], `null` when [text] is empty or can't be read. */
        fun decode(text: String): PatreonProfile? {
            if (text.isEmpty()) return null
            return try {
                of(json.parseToJsonElement(text))
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        /** The API's `profile` object, `null` when [element] isn't one or has no `id`. */
        internal fun of(element: JsonElement?): PatreonProfile? {
            val profile = element as? JsonObject ?: return null
            return PatreonProfile(
                id = profile.text("id")?.ifEmpty { null } ?: return null,
                name = profile.text("name"),
                username = profile.text("username"),
                avatar = profile.text("avatar"),
                tier = (profile["tier"] as? JsonPrimitive)?.intOrNull ?: 0,
                tierTitle = profile.text("tierTitle"),
                features = profile.texts("features"),
                status = patronStatusOf(profile.text("patronStatus")),
                nextChargeDate = profile.text("nextChargeDate"),
            )
        }

        /** The `profile` value of a web view's answer, JSON as text. */
        internal fun ofText(text: String?): PatreonProfile? =
            text?.let { decode(it) }

        private fun statusName(status: PatronStatus): JsonElement =
            if (status == PatronStatus.NONE)
                JsonNull
            else
                JsonPrimitive(status.name.lowercase())
    }
}
