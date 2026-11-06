package com.metrolist.innertubex.utils

import com.metrolist.innertubex.models.WatchEndpoint
import io.ktor.http.Url

object YouTubeUrlParser {
    sealed class ParsedUrl {
        abstract val id: String

        data class Video(
            override val id: String,
        ) : ParsedUrl()

        data class Playlist(
            override val id: String,
        ) : ParsedUrl()

        data class Album(
            override val id: String,
        ) : ParsedUrl()

        data class Artist(
            override val id: String,
        ) : ParsedUrl()
    }

    private val youtubeHosts = setOf("youtube.com", "www.youtube.com", "music.youtube.com", "www.music.youtube.com")
    private val musicHosts = setOf("music.youtube.com", "www.music.youtube.com")
    private val videoId = Regex("[a-zA-Z0-9_-]{11}")
    private val playlistId = Regex("[a-zA-Z0-9_-]+")
    private val artistId = Regex("[a-zA-Z0-9_-]+")

    fun isYouTubeUrl(text: String): Boolean = parse(text) != null

    fun parse(url: String): ParsedUrl? {
        val parsed = parseUrl(url) ?: return null
        val path = parsed.encodedPath
        val host = parsed.host

        if (host == "youtu.be") {
            val id = path.removePrefix("/").takeIf { it.matches(videoId) }
            return id?.let(::video)
        }
        if (host !in youtubeHosts) return null

        if (path == "/watch") {
            return parsed.parameters["v"]?.takeIf { it.matches(videoId) }?.let(::video)
        }
        if (path.startsWith("/shorts/")) {
            val id = path.removePrefix("/shorts/").takeIf { it.matches(videoId) }
            return id?.let(::video)
        }
        if (path == "/playlist") {
            val id = parsed.parameters["list"]?.takeIf { it.matches(playlistId) }
            return id?.let {
                if (host in musicHosts && it.isAlbumLikeId()) {
                    ParsedUrl.Album(it)
                } else {
                    ParsedUrl.Playlist(it)
                }
            }
        }
        if (host !in musicHosts) return null
        if (path.startsWith("/browse/")) {
            val id = path.removePrefix("/browse/").takeIf { it.startsWith("MPRE") && it.matches(playlistId) }
            return id?.let(ParsedUrl::Album)
        }
        if (path.startsWith("/channel/")) {
            val id = path.removePrefix("/channel/").takeIf { it.matches(artistId) }
            return id?.let(ParsedUrl::Artist)
        }
        return null
    }

    fun extractVideoId(url: String): String? = (parse(url) as? ParsedUrl.Video)?.id

    fun extractPlaylistId(url: String): String? =
        when (val parsed = parse(url)) {
            is ParsedUrl.Playlist -> parsed.id
            is ParsedUrl.Album -> parsed.id
            else -> null
        }

    fun createWatchEndpoint(url: String): WatchEndpoint? = extractVideoId(url)?.let(::WatchEndpoint)

    private fun parseUrl(value: String): Url? {
        val input = value.trim()
        if (input.isEmpty() || input.any(Char::isWhitespace)) return null
        val candidate = if (input.startsWith("https://") || input.startsWith("http://")) input else "https://$input"
        return runCatching { Url(candidate) }
            .getOrNull()
            ?.takeIf { it.protocol.name in setOf("http", "https") && !it.hasUserInfo() }
    }

    private fun Url.hasUserInfo(): Boolean = user != null || password != null

    private fun video(id: String) = ParsedUrl.Video(id)

    private fun String.isAlbumLikeId(): Boolean = startsWith("OLAK5uy_") || startsWith("MPRE")
}
