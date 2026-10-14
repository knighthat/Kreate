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

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/*
 * Reading the answers of InnerTube. They are deeply nested and change often, so every step may
 * be missing: each helper answers `null` instead of throwing.
 */

internal val JsonElement?.asObject: JsonObject? get() = this as? JsonObject

internal val JsonElement?.asArray: JsonArray? get() = this as? JsonArray

internal val JsonElement?.asString: String? get() = (this as? JsonPrimitive)?.contentOrNull

/**
 * The element at [keys], names for objects and indexes for arrays. A negative index counts from
 * the end, `-1` is the last element.
 */
internal fun JsonElement?.path(vararg keys: Any): JsonElement? {
    var current = this
    for (key in keys) {
        current = when (key) {
            is String -> current.asObject?.get(key)
            is Int -> current.asArray?.let { it.getOrNull(if (key < 0) it.size + key else key) }
            else -> throw IllegalArgumentException("A path holds names and indexes")
        } ?: return null
    }
    return current
}

/**
 * A piece of text, with where tapping it leads.
 *
 * @property videoId the song or video it plays
 * @property videoType `MUSIC_VIDEO_TYPE_*` of what it plays, `ATV` is a song without video
 * @property browse the page it opens
 * @property pageType `MUSIC_PAGE_TYPE_*` of the page it opens
 */
internal data class Run(
    val text: String,
    val videoId: String? = null,
    val videoType: String? = null,
    val browse: YouTubeBrowse? = null,
    val pageType: String? = null,
) {

    val isArtist: Boolean
        get() = browse != null && when (pageType) {
            null -> browse.browseId.startsWith("UC")
            else -> pageType == PAGE_ARTIST || pageType == PAGE_USER_CHANNEL
        }

    val isAlbum: Boolean
        get() = browse != null &&
                (pageType == PAGE_ALBUM || browse.browseId.startsWith(ALBUM_PREFIX))

    /** Text that only separates others, like ` • ` or ` & `. */
    val isSeparator: Boolean get() = browse == null && videoId == null && text.trim() in SEPARATORS
}

/** The pieces of a text object, empty when it has none. */
internal fun JsonElement?.runs(): List<Run> =
    path("runs").asArray.orEmpty().mapNotNull { element ->
        val text = element.path("text").asString ?: return@mapNotNull null
        val navigation = element.path("navigationEndpoint")
        val watch = navigation.path("watchEndpoint")
        Run(
            text = text,
            videoId = watch.path("videoId").asString,
            videoType = watch.videoType(),
            browse = navigation.browse(),
            pageType = navigation.pageType(),
        )
    }

/** The whole text of a text object, `null` when it is empty. */
internal fun JsonElement?.text(): String? =
    runs().joinToString("") { it.text }.ifEmpty { null } ?: path("simpleText").asString

internal fun List<Run>.joinText(): String? = joinToString("") { it.text }.ifEmpty { null }

/** The page a navigation endpoint opens, `null` when it opens none. */
internal fun JsonElement?.browse(): YouTubeBrowse? {
    val browse = path("browseEndpoint") ?: return null
    val id = browse.path("browseId").asString ?: return null
    return YouTubeBrowse(id, browse.path("params").asString)
}

/** `MUSIC_PAGE_TYPE_*` of the page a navigation endpoint opens. */
internal fun JsonElement?.pageType(): String? =
    path(
        "browseEndpoint",
        "browseEndpointContextSupportedConfigs",
        "browseEndpointContextMusicConfig",
        "pageType",
    ).asString

/** `MUSIC_VIDEO_TYPE_*` of a watch endpoint. */
internal fun JsonElement?.videoType(): String? =
    path(
        "watchEndpointMusicSupportedConfigs",
        "watchEndpointMusicConfig",
        "musicVideoType",
    ).asString

/**
 * Address of the largest picture in any of the shapes a thumbnail comes in, `null` when there is
 * none.
 */
internal fun JsonElement?.thumbnailUrl(): String? {
    val thumbnails = path("musicThumbnailRenderer", "thumbnail", "thumbnails")
        ?: path("croppedSquareThumbnailRenderer", "thumbnail", "thumbnails")
        ?: path("thumbnail", "thumbnails")
        ?: path("thumbnails")
    return thumbnails.asArray
        ?.maxByOrNull { it.path("width").asPrimitiveInt() ?: 0 }
        ?.path("url").asString
}

private fun JsonElement?.asPrimitiveInt(): Int? = (this as? JsonPrimitive)?.intOrNull

/** `true` when [badges] hold the explicit badge, `null` otherwise as YouTube never marks clean. */
internal fun explicit(badges: JsonElement?): Boolean? =
    badges.asArray?.any {
        it.path("musicInlineBadgeRenderer", "icon", "iconType").asString == EXPLICIT_BADGE
    }?.takeIf { it }

private val DURATION = Regex("""^(?:(\d+):)?(\d{1,2}):(\d{2})$""")

/** A length written like `3:05` or `1:02:03`, `null` for any other text. */
internal fun parseDuration(text: String): Duration? {
    val match = DURATION.matchEntire(text.trim()) ?: return null
    val (hours, minutes, seconds) = match.destructured
    return ((hours.toIntOrNull() ?: 0) * 3600 + minutes.toInt() * 60 + seconds.toInt()).seconds
}

private val YEAR = Regex("""^\d{4}$""")

/** The last run that is a year, like the `2013` of `Album • Daft Punk • 2013`. */
internal fun List<Run>.year(): Int? =
    lastOrNull { YEAR.matches(it.text.trim()) }?.text?.trim()?.toInt()

internal const val PAGE_ARTIST = "MUSIC_PAGE_TYPE_ARTIST"
internal const val PAGE_USER_CHANNEL = "MUSIC_PAGE_TYPE_USER_CHANNEL"
internal const val PAGE_ALBUM = "MUSIC_PAGE_TYPE_ALBUM"
internal const val PAGE_PLAYLIST = "MUSIC_PAGE_TYPE_PLAYLIST"

/** Browse id of an album, the only kind of album id this provider uses. */
internal const val ALBUM_PREFIX = "MPREb"

/** Browse id of a playlist is this followed by the playlist id. */
internal const val PLAYLIST_BROWSE_PREFIX = "VL"

/** Browse id of the page with every album or single of an artist. */
internal const val DISCOGRAPHY_PREFIX = "MPAD"

/** Browse id of the lyrics of a song. */
internal const val LYRICS_PREFIX = "MPLYt"

/** Browse id of the songs, albums and artists related to a song. */
internal const val RELATED_PREFIX = "MPTRt"

/** `MUSIC_VIDEO_TYPE_*` of a song published as audio only. */
internal const val VIDEO_TYPE_AUDIO = "MUSIC_VIDEO_TYPE_ATV"

private const val EXPLICIT_BADGE = "MUSIC_EXPLICIT_BADGE"

private val SEPARATORS = setOf("•", "&", ",", "")
