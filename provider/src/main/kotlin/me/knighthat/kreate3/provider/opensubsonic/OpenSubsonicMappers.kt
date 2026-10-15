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

package me.knighthat.kreate3.provider.opensubsonic

import android.net.Uri
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import me.knighthat.kreate3.database.model.Album
import me.knighthat.kreate3.database.model.Artist
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.database.model.Song
import me.knighthat.kreate3.database.model.SongFormat
import me.knighthat.kreate3.database.model.UserPlaylist
import me.knighthat.kreate3.database.repository.RemoteItem
import me.knighthat.kreate3.database.repository.RemoteSong
import kotlin.time.Duration.Companion.seconds
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
        isExplicit = explicit(),
    )
    return RemoteItem(album, starred())
}

/**
 * A song, `Child` in the API. Its album and artists only carry what the song knows about them.
 */
internal fun JsonObject.toSong(cover: (String) -> Uri?): RemoteSong {
    val artists = array("artists")
        ?.mapNotNull { it.jsonObject.toArtistOrNull() }
        ?.ifEmpty { null }
        ?: listOfNotNull(string("artistId")?.let {
            Artist(
                it,
                Origin.OPEN_SUBSONIC,
                string("artist")
            )
        })
    val albumId = string("albumId")

    val song = Song(
        id = string("id")!!,
        origin = Origin.OPEN_SUBSONIC,
        name = string("title"),
        thumbnail = string("coverArt")?.let(cover),
        duration = get("duration")?.jsonPrimitive?.intOrNull?.seconds,
        isExplicit = explicit(),
        albumId = albumId,
        albumPosition = get("track")?.jsonPrimitive?.intOrNull?.takeIf { it > 0 },
    )
    val album = albumId?.let {
        Album(
            id = it,
            origin = Origin.OPEN_SUBSONIC,
            name = string("album"),
            thumbnail = string("coverArt")?.let(cover),
            year = get("year")?.jsonPrimitive?.intOrNull?.takeIf { year -> year > 0 },
            artists = listOfNotNull(
                string("displayAlbumArtist") ?: string("artist")
            ).ifEmpty { null },
        )
    }
    return RemoteSong(song, album, artists, starred())
}

/**
 * Loudness ReplayGain 2 brings songs to, in LUFS. A ReplayGain is the change that gets a song
 * there, `-7` for a song 7 dB louder.
 */
private const val REPLAY_GAIN_LUFS = -18.0

/** Loudness a gain of [SongFormat] is relative to, in LUFS, the app measures songs against it. */
private const val GAIN_REFERENCE_LUFS = -14.0

/**
 * The format of the song's file, `Child` in the API, tagged with the name of the origin: a song
 * of the server is one file. Its gain comes from the ReplayGain of the track, or of the album
 * when the track has none, `null` when the file has no ReplayGain tags or the server is plain
 * Subsonic.
 */
internal fun JsonObject.toSongFormat(): SongFormat {
    val replayGain = get("replayGain") as? JsonObject
    val adjustment = (replayGain?.number("trackGain") ?: replayGain?.number("albumGain"))
    return SongFormat(
        songId = string("id")!!,
        tag = Origin.OPEN_SUBSONIC.name,
        mimetype = string("contentType") ?: "audio/*",
        // In kbps
        bitrate = get("bitRate")?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }?.times(1000),
        length = get("size")?.jsonPrimitive?.longOrNull?.takeIf { it > 0 },
        gain = adjustment?.let { (REPLAY_GAIN_LUFS - it - GAIN_REFERENCE_LUFS).toFloat() },
    )
}

private fun JsonObject.number(name: String): Double? = get(name)?.jsonPrimitive?.doubleOrNull

/** An artist as other items credit it, `null` without an id. */
private fun JsonObject.toArtistOrNull(): Artist? =
    string("id")?.let { Artist(it, Origin.OPEN_SUBSONIC, string("name")) }

private fun JsonObject.explicit(): Boolean? = when (string("explicitStatus")) {
    "explicit" -> true
    "clean" -> false
    else -> null
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
