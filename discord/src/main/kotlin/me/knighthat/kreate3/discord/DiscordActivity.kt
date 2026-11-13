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

package me.knighthat.kreate3.discord

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** How Discord words an activity: "Playing …", "Listening to …". */
enum class ActivityType(internal val code: Int) {
    PLAYING(0),
    LISTENING(2),
    WATCHING(3),
}

/** Which of an activity's texts the member list shows after its [ActivityType]. */
enum class StatusText(internal val code: Int) {
    NAME(0),
    STATE(1),
    DETAILS(2),
}

/**
 * A picture of an activity: one of the app's assets on the developer portal, or an address
 * anyone can open, which [DiscordClient] has Discord proxy before it's sent.
 */
sealed interface ActivityImage {

    /** @property key the asset's name on the developer portal */
    data class Asset(val key: String) : ActivityImage

    /** @property url `http`/`https` address Discord fetches, never one that needs signing in */
    data class Link(val url: String) : ActivityImage
}

/** A button under the activity, others see it, never the user themselves. */
data class ActivityButton(val label: String, val url: String)

/**
 * What the user is doing, as Discord shows it on their profile.
 *
 * @property name the app's name, "Listening to [name]"
 * @property details first line, like a song's title
 * @property state second line, like its artists
 * @property start when it started, in milliseconds since the epoch; Discord counts from it
 * @property end when it ends, in milliseconds since the epoch; with [start] Discord shows a
 * progress bar
 * @property largeText shown over [largeImage]
 * @property smallText shown over [smallImage]
 * @property buttons two at most
 */
data class DiscordActivity(
    val name: String,
    val type: ActivityType,
    val details: String? = null,
    val state: String? = null,
    val statusText: StatusText? = null,
    val start: Long? = null,
    val end: Long? = null,
    val largeImage: ActivityImage? = null,
    val largeText: String? = null,
    val smallImage: ActivityImage? = null,
    val smallText: String? = null,
    val buttons: List<ActivityButton> = emptyList(),
)

/** Longest text Discord takes in a field of an activity. */
private const val MAX_TEXT = 128

/** Longest label of a button. */
private const val MAX_LABEL = 32

/** Longest address of a button. */
private const val MAX_URL = 512

/**
 * Text Discord takes: 2 to [max] characters. A blank one is left out, a single character gets an
 * invisible one after it.
 */
internal fun fit(text: String?, max: Int = MAX_TEXT): String? {
    val trimmed = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (trimmed.length == 1) return "$trimmed⠀"
    if (trimmed.length <= max) return trimmed
    return trimmed.take(max - 1).trimEnd() + "…"
}

/**
 * The activity as the gateway takes it, with [large] and [small] in place of its pictures: what
 * the gateway names them, an asset key or an `mp:` path.
 *
 * @param appId the app's id on Discord: a click on a button asks Discord for its address by it,
 * without one the buttons show and do nothing
 */
internal fun DiscordActivity.toJson(large: String?, small: String?, appId: String): JsonObject =
    buildJsonObject {
        put("name", fit(name) ?: "Kreate")
        put("application_id", appId)
        put("type", type.code)
        fit(details)?.let { put("details", it) }
        fit(state)?.let { put("state", it) }
        statusText?.let { put("status_display_type", it.code) }
        if (start != null || end != null)
            putJsonObject("timestamps") {
                start?.let { put("start", it) }
                end?.let { put("end", it) }
            }
        if (large != null || small != null)
            putJsonObject("assets") {
                large?.let {
                    put("large_image", it)
                    fit(largeText)?.let { text -> put("large_text", text) }
                }
                small?.let {
                    put("small_image", it)
                    fit(smallText)?.let { text -> put("small_text", text) }
                }
            }
        val shown = buttons
            .filter { it.url.length <= MAX_URL && it.url.startsWith("http") }
            .mapNotNull { button -> fit(button.label, MAX_LABEL)?.let { it to button.url } }
            .take(2)
        if (shown.isNotEmpty()) {
            // The gateway takes the labels here and their addresses apart
            putJsonArray("buttons") { shown.forEach { add(it.first) } }
            putJsonObject("metadata") {
                putJsonArray("button_urls") {
                    shown.forEach { add(it.second) }
                }
            }
        }
    }
