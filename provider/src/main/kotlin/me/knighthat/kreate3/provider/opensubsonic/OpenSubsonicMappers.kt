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
import me.knighthat.kreate3.database.model.Song
import me.knighthat.kreate3.database.model.SongFormat
import me.knighthat.kreate3.database.model.UserPlaylist
import me.knighthat.kreate3.database.repository.RemoteItem
import me.knighthat.kreate3.database.repository.RemoteSong
import me.knighthat.kreate3.model.Origin
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/*
 * Turns the items of the OpenSubsonic API into the models of the database. Ids are the server's,
 * and covers become addresses made by `cover`, see OpenSubsonicProvider.
 */

internal fun JsonObject.array(name: String): JsonArray? = get(name) as? JsonArray

private fun JsonObject.string(name: String): String? = get(name)?.jsonPrimitive?.contentOrNull

/** Line breaks of HTML: `<br>` and the end of a paragraph. */
private val HTML_BREAK = Regex("""(?i)<br\s*/?>|</p>""")
private val HTML_TAG = Regex("""<[^>]*>""")
private val HTML_NUMBER = Regex("""&#(\d+);""")
private val BLANK_LINES = Regex("""\n{3,}""")

/**
 * The plain text of [html], like the notes Last.fm gives a server: tags dropped, a new line for
 * `<br>` and each paragraph, the common entities decoded. A link keeps its text, like "Read more
 * on Last.fm", which credits the text.
 *
 * @return `null` when nothing but blanks is left
 */
internal fun htmlToText(html: String): String? =
    html.replace(HTML_BREAK, "\n")
        .replace(HTML_TAG, "")
        .replace(HTML_NUMBER) { match ->
            match.groupValues[1].toIntOrNull()?.takeIf(Character::isValidCodePoint)
                ?.let { String(Character.toChars(it)) }.orEmpty()
        }
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        // Last, so an escaped entity stays as it is written
        .replace("&amp;", "&")
        .lines()
        .joinToString("\n") { it.trim() }
        .replace(BLANK_LINES, "\n\n")
        .trim()
        .ifEmpty { null }

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
 * The format of the song's file, `Child` in the API, tagged with the name of the origin: a song
 * of the server is one file. Its gain is the server's ReplayGain of the track as given, or of the
 * album when the track has none: the change in dB that brings the song to the loudness the files
 * were tagged against (`-7` for a song 7 dB louder), which only the user knows, so it is applied
 * when the song plays. `null` when the file has no ReplayGain tags or the server is plain
 * Subsonic.
 */
internal fun JsonObject.toSongFormat(): SongFormat {
    val replayGain = get("replayGain") as? JsonObject
    val adjustment = (replayGain?.number("trackGain") ?: replayGain?.number("albumGain"))
    return SongFormat(
        songId = string("id")!!,
        tag = RAW_FORMAT,
        mimetype = string("contentType") ?: "audio/*",
        // In kbps
        bitrate = get("bitRate")?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }?.times(1000),
        length = get("size")?.jsonPrimitive?.longOrNull?.takeIf { it > 0 },
        gain = adjustment?.toFloat(),
    )
}

private fun JsonObject.number(name: String): Double? = get(name)?.jsonPrimitive?.doubleOrNull

/**
 * The format a `transcodeStream` of a transcode decision describes, tagged `{codec}-{kbps}` like
 * `opus-96`, its media type the container's with the codec. It has no gain, the song's is on its
 * [RAW_FORMAT].
 *
 * @return `null` without a codec or a bitrate
 */
internal fun JsonObject.toTranscodedFormat(songId: String): SongFormat? {
    val codec = string("codec")?.lowercase()?.takeIf { it.isNotBlank() } ?: return null
    val bitrate = get("audioBitrate")?.jsonPrimitive?.intOrNull?.takeIf { it > 0 } ?: return null
    val container = string("container")?.lowercase()?.takeIf { it.isNotBlank() } ?: codec
    val type = when (container) {
        "mp3" -> "audio/mpeg"
        "m4a", "mp4" -> "audio/mp4"
        else -> "audio/$container"
    }
    return SongFormat(
        songId = songId,
        tag = transcodedTag(codec, (bitrate + 500) / 1000),
        mimetype = "$type; codecs=\"$codec\"",
        bitrate = bitrate,
    )
}

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
