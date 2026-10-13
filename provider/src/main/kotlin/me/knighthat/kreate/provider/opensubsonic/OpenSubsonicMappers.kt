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

package me.knighthat.kreate.provider.opensubsonic

import android.net.Uri
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.knighthat.kreate.database.model.Album
import me.knighthat.kreate.database.model.Artist
import me.knighthat.kreate.database.model.Origin
import me.knighthat.kreate.database.model.UserPlaylist
import me.knighthat.kreate.database.repository.RemoteItem
import kotlin.time.Instant

/*
 * Turns the items of the OpenSubsonic API into the models of the database. Ids are the server's,
 * and covers become addresses made by `cover`, see OpenSubsonicProvider.
 */

internal fun JsonObject.array(name: String): JsonArray? = get(name) as? JsonArray

private fun JsonObject.string(name: String): String? = get(name)?.jsonPrimitive?.contentOrNull

/** When the item was starred, `null` when it isn't or the date can't be read. */
private fun JsonObject.starred(): Instant? =
    string("starred")?.let { runCatching { Instant.parse(it) }.getOrNull() }

internal fun JsonObject.toAlbum(cover: (String) -> Uri?): RemoteItem<Album> {
    // OpenSubsonic lists every artist, Subsonic only has one name
    val artists = array("artists")?.mapNotNull { it.jsonObject.string("name") }?.ifEmpty { null }
        ?: listOfNotNull(string("artist"))

    val album = Album(
        id = string("id")!!,
        origin = Origin.OPEN_SUBSONIC,
        name = string("name") ?: string("title"),
        thumbnail = string("coverArt")?.let(cover),
        year = get("year")?.jsonPrimitive?.intOrNull?.takeIf { it > 0 },
        artists = artists.ifEmpty { null },
        isExplicit = when (string("explicitStatus")) {
            "explicit" -> true
            "clean" -> false
            else -> null
        },
    )
    return RemoteItem(album, starred())
}

internal fun JsonObject.toArtist(cover: (String) -> Uri?): RemoteItem<Artist> {
    val artist = Artist(
        id = string("id")!!,
        origin = Origin.OPEN_SUBSONIC,
        name = string("name"),
        thumbnail = string("coverArt")?.let(cover),
    )
    return RemoteItem(artist, starred())
}

internal fun JsonObject.toPlaylist(cover: (String) -> Uri?): UserPlaylist =
    UserPlaylist(
        origin = Origin.OPEN_SUBSONIC,
        // A playlist always has a name, the server may still send none
        name = string("name").orEmpty(),
        thumbnail = string("coverArt")?.let(cover),
        remoteId = string("id")!!,
    )
