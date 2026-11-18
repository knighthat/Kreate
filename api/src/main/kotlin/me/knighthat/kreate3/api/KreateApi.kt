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

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.io.IOException

/**
 * Where the project's API is, given when the app is built (`KREATE_API_URL`, see this module's
 * `build.gradle.kts`), without a trailing `/`. Empty in a build that has none.
 */
val API_URL: String = BuildConfig.API_URL

/** Whether this build was given the API's address; without it every request fails. */
val isApiAvailable: Boolean get() = API_URL.isNotEmpty()

/** The build has no API address, see [API_URL]. An [IOException], so it reads as offline. */
class ApiUnavailableException : IOException("This build has no API address")

/**
 * The API refused the sign in for good: the user has to sign in with Patreon again, and anything
 * kept of the old one (token, refresh blob) is thrown away.
 */
class PatreonSignedOutException(message: String) : IOException(message)

/** The API answered [status] with the error code [error] (`null` when its body had none). */
class ApiException(val status: Int, val error: String?) :
    IOException("The API answered $status ${error.orEmpty()}".trimEnd())

internal fun JsonObject.text(name: String): String? = (get(name) as? JsonPrimitive)?.contentOrNull

/**
 * The texts of the array [name], e.g. `features`; empty when it's missing or isn't an array, and
 * without the values that aren't texts.
 */
internal fun JsonObject.texts(name: String): Set<String> =
    (get(name) as? JsonArray)
        ?.mapNotNullTo(mutableSetOf()) { (it as? JsonPrimitive)?.takeIf { it.isString }?.content }
        .orEmpty()

/**
 * The ids of features a tier unlocks, as the token's and the profile's `features` name them.
 * Which tier gets which can change on the server: check for the id, never compare tiers. Ids not
 * listed here are ones a newer app knows, ignore them.
 */
object PatreonFeature {

    /** Backups kept online, see the cloud backup switch. */
    const val BACKUPS = "backups"
}
