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

import java.net.URI
import java.net.URISyntaxException
import java.net.URLDecoder

/**
 * Public links to items of YouTube Music, made from their ids. They don't expire and need no
 * request, anyone can open them.
 */
object YouTubeLinks {

    private const val BASE = "https://music.youtube.com"
    private const val HOST = "music.youtube.com"

    /** Start of the id of an album's page. */
    private const val ALBUM_PAGE = "MPRE"

    /** Start of the id of a playlist's page, before the playlist's own id. */
    private const val PLAYLIST_PAGE = "VL"

    /** Start of the id of a channel, an artist's page. */
    private const val CHANNEL_PAGE = "UC"

    private val LINK = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    fun song(songId: String): String = "$BASE/watch?v=$songId"

    fun album(albumId: String): String = "$BASE/browse/$albumId"

    fun artist(artistId: String): String = "$BASE/channel/$artistId"

    /** @param playlistId id of the playlist, without `VL` */
    fun playlist(playlistId: String): String = "$BASE/playlist?list=$playlistId"

    /**
     * What a YouTube Music link points to: the links above, and pages of the app opened under
     * `browse/` (an album, an artist or a playlist). Anything else, like a link of YouTube itself,
     * is `null`. A song played from a playlist (`watch?v=…&list=…`) is the song.
     */
    fun parse(link: String): YouTubeLink? {
        val uri = try {
            URI(link.trim())
        } catch (_: URISyntaxException) {
            return null
        }
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return null
        if (uri.host?.lowercase() != HOST) return null

        val path = uri.path.orEmpty().trim('/').split('/')
        val query = uri.rawQuery.orEmpty().split('&').mapNotNull { pair ->
            val parts = pair.split('=', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            parts[0] to URLDecoder.decode(parts[1], Charsets.UTF_8.name())
        }.toMap()
        // The id of a page is all its path holds after its kind
        val id = path.getOrNull(1)?.takeIf { it.isNotBlank() && path.size == 2 }

        return when (path.first()) {
            "watch" -> query["v"]?.takeIf { it.isNotBlank() }?.let(YouTubeLink::Song)
            "playlist" -> query["list"]?.takeIf { it.isNotBlank() }
                ?.let { YouTubeLink.Playlist(it.removePrefix(PLAYLIST_PAGE)) }

            "channel" -> id?.let(YouTubeLink::Artist)
            "browse" -> when {
                id == null -> null
                id.startsWith(ALBUM_PAGE) -> YouTubeLink.Album(id)
                id.startsWith(PLAYLIST_PAGE) ->
                    YouTubeLink.Playlist(id.removePrefix(PLAYLIST_PAGE))

                id.startsWith(CHANNEL_PAGE) -> YouTubeLink.Artist(id)
                else -> null
            }

            else -> null
        }
    }

    /** The first YouTube Music link in [text] that [parse] knows, like the text an app shares. */
    fun find(text: String): YouTubeLink? =
        LINK.findAll(text).firstNotNullOfOrNull { parse(it.value) }
}

/** An item of YouTube Music a link points to, see [YouTubeLinks.parse]. */
sealed interface YouTubeLink {

    data class Song(val songId: String) : YouTubeLink

    data class Album(val albumId: String) : YouTubeLink

    data class Artist(val artistId: String) : YouTubeLink

    /** @property playlistId id of the playlist, without `VL` */
    data class Playlist(val playlistId: String) : YouTubeLink
}

private val VIDEO_PICTURE = Regex("""^https?://i\d*\.ytimg\.com/(?:vi|vi_webp)/([^/]+)/""")

/** A picture of Google's servers, sized by what follows the last `=`. */
private val SIZED_PICTURE =
    Regex("""^(https?://[^/]+\.(?:googleusercontent|ggpht)\.com/[^=]+)(?:=[^/]*)?$""")

/**
 * The address of the picture at [url] at another size. Covers and artist pictures are cut to a
 * square of [size], never larger than the original, as a JPEG of quality 80: a third smaller
 * than 90 and hard to tell apart. A frame of a video is the closest size YouTube has. Other
 * addresses are left as they are.
 *
 * @param size edge in pixels, `null` for the largest picture
 */
internal fun resizeArtwork(url: String, size: Int?): String {
    VIDEO_PICTURE.find(url)?.let {
        val name = if (size != null && size <= MEDIUM_FRAME) "mqdefault" else "hqdefault"
        return "https://i.ytimg.com/vi/${it.groupValues[1]}/$name.jpg"
    }
    SIZED_PICTURE.matchEntire(url)?.let {
        val base = it.groupValues[1]
        return if (size == null) "$base=s0" else "$base=w$size-h$size-p-nu-l80-rj"
    }
    return url
}

/** Whether [url] is a cover or an artist picture, which come at any size, not a frame of a video. */
internal fun isSizedArtwork(url: String): Boolean = SIZED_PICTURE.matches(url)

/** Width of `mqdefault`, the largest frame smaller than `hqdefault`. */
internal const val MEDIUM_FRAME = 320
