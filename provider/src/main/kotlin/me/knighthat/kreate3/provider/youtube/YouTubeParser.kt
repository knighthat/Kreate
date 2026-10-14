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

import android.net.Uri
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import me.knighthat.kreate3.database.model.Album
import me.knighthat.kreate3.database.model.Artist
import me.knighthat.kreate3.database.model.Lyrics
import me.knighthat.kreate3.database.model.Origin
import me.knighthat.kreate3.database.model.Song
import me.knighthat.kreate3.database.model.UserPlaylist
import me.knighthat.kreate3.database.repository.RemoteSong
import me.knighthat.kreate3.provider.youtube.YouTubeItem.AlbumItem
import me.knighthat.kreate3.provider.youtube.YouTubeItem.ArtistItem
import me.knighthat.kreate3.provider.youtube.YouTubeItem.PlaylistItem
import me.knighthat.kreate3.provider.youtube.YouTubeItem.SongItem
import kotlin.time.Duration

/**
 * Turns the answers of InnerTube into the models of the database. Ids are YouTube's own: the
 * video id of a song, the `MPREb` browse id of an album, the channel id of an artist and the
 * playlist id, without `VL`, of a playlist. Pictures stay the addresses YouTube gives.
 *
 * An answer of an unexpected shape gives empty lists or `null`, never an exception.
 *
 * @param uri makes the address of a picture into a [Uri]
 */
internal class YouTubeParser(private val uri: (String) -> Uri?) {

    //<editor-fold defaultstate="collapsed" desc="Items">
    /** The item an `*Renderer` wrapper holds, `null` for kinds that aren't music. */
    fun item(wrapper: JsonElement?): YouTubeItem? {
        val renderer = wrapper.asObject ?: return null
        renderer["musicResponsiveListItemRenderer"].asObject?.let { return listItem(it) }
        renderer["musicTwoRowItemRenderer"].asObject?.let { return cardItem(it) }
        return queueSong(wrapper)?.let { SongItem(it.first, it.second) }
    }

    /** A row of a list, like a search result or a song of an album. */
    private fun listItem(renderer: JsonObject): YouTubeItem? {
        val columns = renderer["flexColumns"].asArray.orEmpty().map {
            it.path("musicResponsiveListItemFlexColumnRenderer", "text").runs()
        }
        val fixed = renderer["fixedColumns"].asArray.orEmpty().flatMap {
            it.path("musicResponsiveListItemFixedColumnRenderer", "text").runs()
        }
        val title = columns.firstOrNull().orEmpty()
        val name = title.joinText()
        val thumbnail = renderer["thumbnail"].thumbnailUrl()

        val videoId = renderer.path("playlistItemData", "videoId").asString
            ?: title.firstNotNullOfOrNull { it.videoId }
            ?: renderer.path(
                "overlay", "musicItemThumbnailOverlayRenderer", "content",
                "musicPlayButtonRenderer", "playNavigationEndpoint", "watchEndpoint", "videoId",
            ).asString
        if (videoId != null) {
            val details = columns.drop(1).flatten() + fixed
            val videoType = title.firstNotNullOfOrNull { it.videoType }
            val song = song(
                id = videoId,
                name = name,
                thumbnail = thumbnail,
                details = details,
                duration = (fixed + details).firstNotNullOfOrNull { parseDuration(it.text) },
                explicit = explicit(renderer["badges"]),
                position = renderer["index"].text()?.trim()?.toIntOrNull(),
                videoType = videoType,
            )
            return SongItem(song, isVideo(videoType))
        }

        val navigation = renderer["navigationEndpoint"]
        val browse = navigation.browse() ?: return null
        val details = columns.drop(1).flatten()
        return browseItem(
            browse,
            navigation.pageType(),
            name,
            thumbnail,
            details,
            renderer["badges"]
        )
    }

    /** A card of a row, like an album of an artist or a playlist on the home page. */
    private fun cardItem(renderer: JsonObject): YouTubeItem? {
        val name = renderer["title"].text()
        val subtitle = renderer["subtitle"].runs()
        val thumbnail = renderer["thumbnailRenderer"].thumbnailUrl()
        val navigation = renderer["navigationEndpoint"]

        val watch = navigation.path("watchEndpoint")
        watch.path("videoId").asString?.let { videoId ->
            val videoType = watch.videoType()
            val song = song(
                id = videoId,
                name = name,
                thumbnail = thumbnail,
                details = subtitle,
                duration = null,
                explicit = explicit(renderer["subtitleBadges"]),
                position = null,
                videoType = videoType,
            )
            return SongItem(song, isVideo(videoType))
        }

        val browse = navigation.browse() ?: return null
        return browseItem(
            browse,
            navigation.pageType(),
            name,
            thumbnail,
            subtitle,
            renderer["subtitleBadges"]
        )
    }

    /** An item that opens a page: an album, an artist or a playlist. */
    private fun browseItem(
        browse: YouTubeBrowse,
        pageType: String?,
        name: String?,
        thumbnail: String?,
        details: List<Run>,
        badges: JsonElement?,
    ): YouTubeItem? {
        val id = browse.browseId
        return when {
            pageType == PAGE_ALBUM || id.startsWith(ALBUM_PREFIX) -> AlbumItem(
                Album(
                    id = id,
                    origin = Origin.YOUTUBE,
                    name = name,
                    thumbnail = thumbnail?.let(uri),
                    year = details.year(),
                    artists = details.filter { it.isArtist }.map { it.text }.ifEmpty { null },
                    url = YouTubeLinks.album(id),
                    isExplicit = explicit(badges),
                )
            )

            pageType == PAGE_ARTIST || pageType == PAGE_USER_CHANNEL ||
                    (pageType == null && id.startsWith("UC")) ->
                ArtistItem(Artist(id, Origin.YOUTUBE, name, thumbnail?.let(uri)))

            pageType == PAGE_PLAYLIST || id.startsWith(PLAYLIST_BROWSE_PREFIX) -> PlaylistItem(
                UserPlaylist(
                    origin = Origin.YOUTUBE,
                    // A playlist always has a name, YouTube may still send none
                    name = name.orEmpty(),
                    thumbnail = thumbnail?.let(uri),
                    remoteId = id.removePrefix(PLAYLIST_BROWSE_PREFIX),
                )
            )

            else -> null
        }
    }

    /**
     * A song with the album and artists [details] link to. The album is given the song's
     * picture only when the song isn't a video, a video's picture is a frame of it.
     */
    private fun song(
        id: String,
        name: String?,
        thumbnail: String?,
        details: List<Run>,
        duration: Duration?,
        explicit: Boolean?,
        position: Int?,
        videoType: String?,
    ): RemoteSong {
        val artists = details.filter { it.isArtist }
            .map { Artist(it.browse!!.browseId, Origin.YOUTUBE, it.text) }
            .distinctBy { it.id }
        val albumRun = details.firstOrNull { it.isAlbum }
        val album = albumRun?.browse?.browseId?.let { albumId ->
            Album(
                id = albumId,
                origin = Origin.YOUTUBE,
                name = albumRun.text,
                thumbnail = thumbnail?.takeUnless { isVideo(videoType) }?.let(uri),
                year = details.year(),
                artists = artists.mapNotNull { it.name }.ifEmpty { null },
                url = YouTubeLinks.album(albumId),
            )
        }
        val song = Song(
            id = id,
            origin = Origin.YOUTUBE,
            name = name,
            thumbnail = thumbnail?.let(uri),
            duration = duration,
            isExplicit = explicit,
            albumId = album?.id,
            albumPosition = position,
        )
        return RemoteSong(song, album, artists, starredAt = null)
    }

    /**
     * A song of a queue, with whether it is a video, `null` when [wrapper] isn't one. A song that
     * also exists as a video comes wrapped with it, the song is taken then.
     */
    private fun queueSong(wrapper: JsonElement?): Pair<RemoteSong, Boolean>? {
        val renderer = wrapper.path("playlistPanelVideoRenderer")
            ?: wrapper.path(
                "playlistPanelVideoWrapperRenderer",
                "primaryRenderer",
                "playlistPanelVideoRenderer",
            )
            ?: return null
        val id = renderer.path("videoId").asString ?: return null
        val videoType = renderer.path("navigationEndpoint", "watchEndpoint").videoType()

        val song = song(
            id = id,
            name = renderer.path("title").text(),
            thumbnail = renderer.path("thumbnail").thumbnailUrl(),
            details = renderer.path("longBylineText").runs(),
            duration = renderer.path("lengthText").text()?.let(::parseDuration),
            explicit = explicit(renderer.path("badges")),
            position = null,
            videoType = videoType,
        )
        return song to isVideo(videoType)
    }

    private fun isVideo(videoType: String?): Boolean =
        videoType != null && videoType != VIDEO_TYPE_AUDIO
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Sections">
    /** The row a shelf holds, `null` when [wrapper] isn't a shelf of items. */
    fun section(wrapper: JsonElement?): YouTubeSection? {
        wrapper.path("musicCarouselShelfRenderer")?.let { shelf ->
            val header = shelf.path("header", "musicCarouselShelfBasicHeaderRenderer")
            return YouTubeSection(
                title = header.path("title").text(),
                items = shelf.path("contents").items(),
                strapline = header.path("strapline").text(),
                more = header.path("moreContentButton", "buttonRenderer", "navigationEndpoint")
                    .browse()
                    ?: header.path("title").runs().firstNotNullOfOrNull { it.browse }
                        // A title naming an artist, like "More from <artist>", opens the artist
                        ?.takeUnless { it.params == null && it.browseId.startsWith("UC") },
            )
        }
        wrapper.path("musicShelfRenderer")?.let { shelf ->
            return YouTubeSection(
                title = shelf.path("title").text(),
                items = shelf.path("contents").items(),
                more = shelf.path("bottomEndpoint").browse()
                    ?: shelf.path("title").runs().firstNotNullOfOrNull { it.browse },
            )
        }
        wrapper.path("gridRenderer")?.let { grid ->
            return YouTubeSection(
                title = grid.path("header", "gridHeaderRenderer", "title").text(),
                items = grid.path("items").items(),
            )
        }
        wrapper.path("musicPlaylistShelfRenderer")?.let { shelf ->
            return YouTubeSection(title = null, items = shelf.path("contents").items())
        }
        return null
    }

    private fun JsonElement?.items(): List<YouTubeItem> = asArray.orEmpty().mapNotNull(::item)

    /** Every shelf of a page, in the order of the page, whatever its layout. */
    private fun shelves(response: JsonElement?): List<JsonElement> {
        val contents = response.path("contents")
        val tabs = contents.path("singleColumnBrowseResultsRenderer", "tabs")
            ?: contents.path("twoColumnBrowseResultsRenderer", "tabs")
            ?: contents.path("tabbedSearchResultsRenderer", "tabs")
        return listOfNotNull(
            tabs.path(0, "tabRenderer", "content", "sectionListRenderer", "contents"),
            contents.path(
                "twoColumnBrowseResultsRenderer",
                "secondaryContents",
                "sectionListRenderer",
                "contents"
            ),
            contents.path("sectionListRenderer", "contents"),
        ).flatMap { it.asArray.orEmpty() }
    }

    /** The token of the next part of a shelf or list, in the old and the new way of giving it. */
    private fun continuationOf(container: JsonElement?, items: JsonElement? = null): String? =
        container.path("continuations", 0, "nextContinuationData", "continuation").asString
            ?: container.path(
                "continuations",
                0,
                "nextRadioContinuationData",
                "continuation"
            ).asString
            ?: items.path(
                -1,
                "continuationItemRenderer",
                "continuationEndpoint",
                "continuationCommand",
                "token"
            )
                .asString

    /**
     * What the answer to a continuation request holds, and the token of the part after it.
     */
    private fun continued(response: JsonElement?): Pair<List<JsonElement>, String?> {
        response.path("continuationContents").asObject?.values?.firstOrNull()?.let { container ->
            val items = container.path("contents") ?: container.path("items")
            return items.asArray.orEmpty() to continuationOf(container, items)
        }
        val items = response.path("onResponseReceivedActions").asArray.orEmpty().flatMap {
            it.path("appendContinuationItemsAction", "continuationItems").asArray.orEmpty()
        }
        return items to items.lastOrNull()
            .path(
                "continuationItemRenderer",
                "continuationEndpoint",
                "continuationCommand",
                "token"
            )
            .asString
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Pages">
    /** The results of a search, or of its continuation. */
    fun searchPage(response: JsonElement?): YouTubePage<YouTubeItem> {
        if (response.path("continuationContents") != null || response.path("onResponseReceivedActions") != null) {
            val (items, token) = continued(response)
            return YouTubePage(items.mapNotNull(::item), token)
        }
        val shelf = shelves(response).firstNotNullOfOrNull { it.path("musicShelfRenderer") }
        return YouTubePage(
            shelf.path("contents").items(),
            continuationOf(shelf, shelf.path("contents"))
        )
    }

    /**
     * The items of a page of any kind, like the list behind "See all" or its continuation. A
     * playlist gives its songs.
     */
    fun browsePage(response: JsonElement?): YouTubePage<YouTubeItem> {
        if (response.path("continuationContents") != null || response.path("onResponseReceivedActions") != null) {
            val (items, token) = continued(response)
            return YouTubePage(items.mapNotNull(::item), token)
        }
        val shelves = shelves(response)
        val items = shelves.mapNotNull(::section).flatMap { it.items }
        val token = shelves.firstNotNullOfOrNull { shelf ->
            val inner = shelf.asObject?.values?.firstOrNull()
            continuationOf(inner, inner.path("contents") ?: inner.path("items"))
        }
        return YouTubePage(items, token)
    }

    /** The home page's rows, or the rows of its continuation. */
    fun homePage(response: JsonElement?): YouTubePage<YouTubeSection> {
        if (response.path("continuationContents") != null) {
            val (items, token) = continued(response)
            return YouTubePage(items.mapNotNull(::section), token)
        }
        val list = response.path(
            "contents", "singleColumnBrowseResultsRenderer", "tabs", 0, "tabRenderer", "content",
            "sectionListRenderer",
        )
        return YouTubePage(shelves(response).mapNotNull(::section), continuationOf(list))
    }

    /** The rows of a page made of rows only, like the items related to a song. */
    fun sections(response: JsonElement?): List<YouTubeSection> =
        shelves(response).mapNotNull(::section)

    /** The header of an album or playlist page, `null` when the page has none. */
    private fun responsiveHeader(response: JsonElement?): JsonElement? =
        shelves(response).firstNotNullOfOrNull {
            it.path("musicResponsiveHeaderRenderer")
                ?: it.path(
                    "musicEditablePlaylistDetailHeaderRenderer",
                    "header",
                    "musicResponsiveHeaderRenderer"
                )
        } ?: response.path("header", "musicDetailHeaderRenderer")

    /** @return `null` when [response] is not an album, like for an id YouTube doesn't know */
    fun albumPage(albumId: String, response: JsonElement?): YouTubeAlbumPage? {
        val header = responsiveHeader(response) ?: return null
        val credits = header.path("straplineTextOne").runs()
            .ifEmpty { header.path("subtitle").runs().filter { it.isArtist } }
        val artists = credits.filter { it.isArtist }
            .map { Artist(it.browse!!.browseId, Origin.YOUTUBE, it.text) }
            .distinctBy { it.id }
        val album = Album(
            id = albumId,
            origin = Origin.YOUTUBE,
            name = header.path("title").text(),
            thumbnail = header.path("thumbnail").thumbnailUrl()?.let(uri),
            year = header.path("subtitle").runs().year(),
            // Names without a page, like "Various Artists", are kept too
            artists = credits.filterNot { it.isSeparator }.map { it.text.trim() }
                .filter { it.isNotEmpty() }
                .ifEmpty { null },
            url = YouTubeLinks.album(albumId),
            isExplicit = explicit(header.path("subtitleBadge")),
        )

        val shelves = shelves(response)
        val tracks = shelves.firstNotNullOfOrNull { it.path("musicShelfRenderer") }
        val songs = tracks.path("contents").items().filterIsInstance<SongItem>()
            .mapIndexed { index, item ->
                val remote = item.song
                RemoteSong(
                    song = remote.song.copy(
                        albumId = albumId,
                        albumPosition = remote.song.albumPosition ?: (index + 1),
                    ),
                    album = album,
                    // A song of the album's artists names nobody
                    artists = remote.artists.ifEmpty { artists },
                    starredAt = null,
                )
            }
        val playlistId = tracks.path("contents").asArray.orEmpty().firstNotNullOfOrNull {
            it.path(
                "musicResponsiveListItemRenderer", "flexColumns", 0,
                "musicResponsiveListItemFlexColumnRenderer", "text", "runs", 0,
                "navigationEndpoint", "watchEndpoint", "playlistId"
            ).asString
        } ?: header.path("buttons").asArray.orEmpty().firstNotNullOfOrNull {
            it.path(
                "musicPlayButtonRenderer",
                "playNavigationEndpoint",
                "watchPlaylistEndpoint",
                "playlistId"
            )
                .asString
        }

        return YouTubeAlbumPage(
            album = album,
            artists = artists,
            songs = songs,
            playlistId = playlistId,
            description = header.path("description", "musicDescriptionShelfRenderer", "description")
                .text()
                ?: header.path("description").text(),
            sections = shelves.filter { it.path("musicShelfRenderer") == null }
                .mapNotNull(::section),
        )
    }

    /** @return `null` when [response] is not an artist, like for an id YouTube doesn't know */
    fun artistPage(artistId: String, response: JsonElement?): YouTubeArtistPage? {
        val header = response.path("header").let {
            it.path("musicImmersiveHeaderRenderer") ?: it.path("musicVisualHeaderRenderer")
        } ?: return null
        val name = header.path("title").text()
        val artist = Artist(
            id = artistId,
            origin = Origin.YOUTUBE,
            name = name,
            thumbnail = (header.path("thumbnail").thumbnailUrl()
                ?: header.path("foregroundThumbnail").thumbnailUrl())?.let(uri),
        )

        val shelves = shelves(response)
        val top = shelves.firstNotNullOfOrNull { it.path("musicShelfRenderer") }
        val songsBrowse = top.path("bottomEndpoint").browse()
            ?: top.path("title").runs().firstNotNullOfOrNull { it.browse }

        return YouTubeArtistPage(
            artist = artist,
            description = header.path("description").text()
                ?: shelves.firstNotNullOfOrNull {
                    it.path("musicDescriptionShelfRenderer", "description").text()
                },
            topSongs = top.path("contents").items().filterIsInstance<SongItem>().map { it.song },
            songsPlaylistId = songsBrowse?.browseId
                ?.takeIf { it.startsWith(PLAYLIST_BROWSE_PREFIX) }
                ?.removePrefix(PLAYLIST_BROWSE_PREFIX),
            sections = shelves.filter { it.path("musicCarouselShelfRenderer") != null }
                .mapNotNull(::section)
                .map { it.copy(items = it.items.map { item -> item.creditedTo(name) }) },
        )
    }

    /** @return `null` when [response] is not a playlist, like for an id YouTube doesn't know */
    fun playlistPage(playlistId: String, response: JsonElement?): YouTubePlaylistPage? {
        val header = responsiveHeader(response) ?: return null
        val shelf = shelves(response).firstNotNullOfOrNull { it.path("musicPlaylistShelfRenderer") }
        val contents = shelf.path("contents")

        return YouTubePlaylistPage(
            playlist = UserPlaylist(
                origin = Origin.YOUTUBE,
                name = header.path("title").text().orEmpty(),
                thumbnail = header.path("thumbnail").thumbnailUrl()?.let(uri),
                remoteId = playlistId,
            ),
            author = (header.path("straplineTextOne") ?: header.path(
                "facepile",
                "avatarStackViewModel",
                "text"
            ))
                .let { it.text() ?: it.path("content").asString },
            description = header.path("description", "musicDescriptionShelfRenderer", "description")
                .text()
                ?: header.path("description").text(),
            songs = contents.items().filterIsInstance<SongItem>().map { it.song },
            continuation = continuationOf(shelf, contents),
        )
    }

    /** The next songs of a playlist. */
    fun playlistSongs(response: JsonElement?): YouTubePage<RemoteSong> {
        val (items, token) = continued(response)
        return YouTubePage(
            items.mapNotNull(::item).filterIsInstance<SongItem>().map { it.song },
            token
        )
    }

    /** The songs of a queue or of its continuation, with whether each is a video. */
    fun queue(response: JsonElement?): YouTubePage<SongItem> {
        val panel = response.path("continuationContents", "playlistPanelContinuation")
            ?: watchTabs(response).path(
                0, "tabRenderer", "content", "musicQueueRenderer", "content",
                "playlistPanelRenderer"
            )
        val songs = panel.path("contents").asArray.orEmpty()
            .mapNotNull { queueSong(it)?.let { (song, isVideo) -> SongItem(song, isVideo) } }
        return YouTubePage(songs, continuationOf(panel))
    }

    private fun watchTabs(response: JsonElement?): JsonElement? =
        response.path(
            "contents", "singleColumnMusicWatchNextResultsRenderer", "tabbedRenderer",
            "watchNextTabbedResultsRenderer", "tabs",
        )

    /** Browse id of the tab of the player starting with [prefix], `null` when it is disabled. */
    fun tabBrowseId(response: JsonElement?, prefix: String): String? =
        watchTabs(response).asArray.orEmpty().firstNotNullOfOrNull { tab ->
            val renderer = tab.path("tabRenderer")
            renderer.path("endpoint").browse()?.browseId
                ?.takeIf { it.startsWith(prefix) && renderer.path("unselectable").asString != "true" }
        }

    /** @return `null` when the page has no lyrics */
    fun lyrics(songId: String, response: JsonElement?): YouTubeLyrics? {
        val shelf =
            shelves(response).firstNotNullOfOrNull { it.path("musicDescriptionShelfRenderer") }
                ?: return null
        val text = shelf.path("description").text()?.takeIf { it.isNotBlank() } ?: return null
        return YouTubeLyrics(Lyrics(songId, text), shelf.path("footer").text())
    }

    fun suggestions(response: JsonElement?): YouTubeSuggestions {
        val entries = response.path("contents").asArray.orEmpty().flatMap {
            it.path("searchSuggestionsSectionRenderer", "contents").asArray.orEmpty()
        }
        return YouTubeSuggestions(
            queries = entries.mapNotNull {
                it.path(
                    "searchSuggestionRenderer",
                    "navigationEndpoint",
                    "searchEndpoint",
                    "query"
                ).asString
                    ?: it.path("searchSuggestionRenderer", "suggestion").text()
            },
            items = entries.mapNotNull(::item),
        )
    }
    //</editor-fold>
}

/** An album without artists named on it is given [artist], the one whose page it is on. */
internal fun YouTubeItem.creditedTo(artist: String?): YouTubeItem =
    if (this is AlbumItem && album.artists == null && artist != null)
        AlbumItem(album.copy(artists = listOf(artist)))
    else this
