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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import me.knighthat.kreate3.database.model.Artist
import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.provider.youtube.YouTubeItem.AlbumItem
import me.knighthat.kreate3.provider.youtube.YouTubeItem.ArtistItem
import me.knighthat.kreate3.provider.youtube.YouTubeItem.PlaylistItem
import me.knighthat.kreate3.provider.youtube.YouTubeItem.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Reads answers YouTube Music gave on 2026-10-13, trimmed, from `resources/youtube`.
 */
class YouTubeParserTest {

    // Uri can't be made on the JVM, the addresses handed to it are checked instead
    private val pictures = mutableListOf<String>()
    private val parser = YouTubeParser { pictures += it; null as Uri? }

    private fun fixture(name: String): JsonElement = Json.parseToJsonElement(
        javaClass.getResource("/youtube/$name.json")!!.readText()
    )

    private val daftPunk = Artist("UCRr1xG_2WIDs18a6cIiCxeA", Origin.YOUTUBE, "Daft Punk")

    @Test
    fun songSearchGivesSongsWithTheirAlbumAndArtists() {
        val page = parser.searchPage(fixture("search_songs"))

        val first = page.items.first() as SongItem
        val (song, album, artists, starredAt) = first.song
        assertEquals("a7Wask21bew", song.id)
        assertEquals(Origin.YOUTUBE, song.origin)
        assertEquals("Derezzed (From \"TRON: Legacy\"/Score)", song.name)
        assertEquals(105.seconds, song.duration)
        assertEquals("MPREb_BQHrCwSBM37", song.albumId)
        assertNull(song.isExplicit)
        assertFalse(first.isVideo)
        assertNull(starredAt)

        assertEquals(listOf(daftPunk), artists)
        assertEquals("MPREb_BQHrCwSBM37", album!!.id)
        assertEquals("TRON: Legacy", album.name)
        assertEquals(listOf("Daft Punk"), album.artists)
        assertEquals("https://music.youtube.com/browse/MPREb_BQHrCwSBM37", album.url)

        // The largest picture, the album shares it as the song isn't a video
        assertTrue(pictures[0].endsWith("=w120-h120-l90-rj"))
        assertEquals(pictures[0], pictures[1])
        assertNotNull(page.continuation)
    }

    @Test
    fun searchContinuationGivesMoreSongs() {
        val page = parser.searchPage(fixture("search_cont"))

        assertEquals(6, page.items.size)
        assertTrue(page.items.all { it is SongItem })
        assertNotNull(page.continuation)
    }

    @Test
    fun explicitSongsAreMarked() {
        val songs = parser.searchPage(fixture("search_explicit")).items
            .map { (it as SongItem).song.song }

        assertEquals(true, songs.first { it.id == "zlJ0Aj9y67c" }.isExplicit)
        assertNull(songs.first { it.id == "Yv2LK0XRqzE" }.isExplicit)
    }

    @Test
    fun albumSearchGivesYearAndArtists() {
        val album = (parser.searchPage(fixture("search_albums")).items.first() as AlbumItem).album

        assertEquals("MPREb_K8qWMWVqXGi", album.id)
        assertEquals("Random Access Memories", album.name)
        assertEquals(2013, album.year)
        assertEquals(listOf("Daft Punk"), album.artists)
        assertTrue(pictures.first().endsWith("=w544-h544-l90-rj"))
    }

    @Test
    fun artistSearchGivesArtists() {
        val artist =
            (parser.searchPage(fixture("search_artists")).items.first() as ArtistItem).artist

        assertEquals(daftPunk, artist)
    }

    @Test
    fun playlistsAreKeptWithoutTheBrowsePrefix() {
        val playlist =
            (parser.searchPage(fixture("search_playlists")).items.first() as PlaylistItem).playlist

        assertEquals("RDCLAK5uy_n20FRYQXNt1p1wS55Nj2r14IouO5weaYU", playlist.remoteId)
        assertEquals("Presenting Daft Punk", playlist.name)
        assertEquals(Origin.YOUTUBE, playlist.origin)
        assertEquals(0L, playlist.id)
    }

    @Test
    fun albumPageGivesTheAlbumAndItsSongsInOrder() {
        val page = parser.albumPage("MPREb_BQHrCwSBM37", fixture("album"))!!

        assertEquals("TRON: Legacy", page.album.name)
        assertEquals(2010, page.album.year)
        assertEquals(listOf("Daft Punk"), page.album.artists)
        assertEquals(listOf(daftPunk), page.artists)
        assertEquals("OLAK5uy_k8XkxQFGZhSX8gENqaH6pg1xz8vOTnzQs", page.playlistId)
        assertTrue(page.description!!.startsWith("Tron: Legacy is the soundtrack"))
        assertEquals(listOf("Other versions", "Releases for you"), page.sections.map { it.title })

        assertEquals(listOf(1, 2, 3, 4, 5, 22), page.songs.map { it.song.albumPosition })
        val (song, album, artists) = page.songs.first()
        assertEquals("mGCnOLGwzg0", song.id)
        assertEquals("Overture (From \"TRON: Legacy\"/Score)", song.name)
        assertEquals(149.seconds, song.duration)
        assertEquals("MPREb_BQHrCwSBM37", song.albumId)
        // The songs have no picture of their own, they show the album's
        assertNull(song.thumbnail)
        assertEquals(page.album, album)
        // Songs of the album's artists name nobody and are credited to them
        assertEquals(listOf(daftPunk), artists)
    }

    @Test
    fun aSavedAlbumIsToldByItsLibraryButton() {
        fun album(vararg buttons: String) = Json.parseToJsonElement(
            """{"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[{"tabRenderer":{"content":{
            "sectionListRenderer":{"contents":[{"musicResponsiveHeaderRenderer":{
            "title":{"runs":[{"text":"Album"}]},"buttons":[${buttons.joinToString(",")}]}}]}}}}]}}}"""
        )

        fun toggle(icon: String, toggled: Boolean) =
            """{"toggleButtonRenderer":{"isToggled":$toggled,"defaultIcon":{"iconType":"$icon"}}}"""

        // Only the library button counts, not "More" of the description
        assertTrue(parser.albumPage("MPREb_1", album(toggle("BOOKMARK_BORDER", true)))!!.saved)
        assertTrue(parser.albumPage("MPREb_1", album(toggle("LIBRARY_ADD", true)))!!.saved)
        assertFalse(
            parser.albumPage(
                "MPREb_1",
                album(toggle("EXPAND", true), toggle("BOOKMARK_BORDER", false)),
            )!!.saved
        )
        // Outside the session the page has no such button
        assertFalse(parser.albumPage("MPREb_BQHrCwSBM37", fixture("album"))!!.saved)
    }

    @Test
    fun anArtistIsSubscribedToByAChannelOfTheirOwn() {
        val page = parser.artistPage(
            daftPunk.id,
            Json.parseToJsonElement(
                """{"header":{"musicImmersiveHeaderRenderer":{"title":{"runs":[{"text":"Daft Punk"}]},
                "subscriptionButton":{"subscribeButtonRenderer":{"subscribed":true,"channelId":"UC_channel"}}}}}"""
            ),
        )!!

        assertEquals("UC_channel", page.channelId)
        assertTrue(page.subscribed)
    }

    @Test
    fun artistPageGivesTopSongsAndRows() {
        val page = parser.artistPage(daftPunk.id, fixture("artist"))!!

        assertEquals("Daft Punk", page.artist.name)
        assertTrue(page.description!!.startsWith("Daft Punk were"))
        assertEquals("OLAK5uy_lNVBcjNtiCwq-n95uoxJ-Gd4vsfMkyZNs", page.songsPlaylistId)

        assertEquals(5, page.topSongs.size)
        val top = page.topSongs.first()
        assertEquals("khnokW3Mw24", top.song.id)
        assertEquals(
            listOf("UCRr1xG_2WIDs18a6cIiCxeA", "UCWhpbdGLnR8XtXLr0yl2nYA"),
            top.artists.map { it.id },
        )
        assertEquals("MPREb_K8qWMWVqXGi", top.album?.id)

        assertEquals(
            listOf("Albums", "Singles & EPs", "Videos", "Live performances"),
            page.sections.map { it.title },
        )
        val albums = page.sections[0]
        assertNull(albums.more)
        val album = (albums.items.first() as AlbumItem).album
        assertEquals("MPREb_OgeEnoHTsCm", album.id)
        assertEquals(2023, album.year)
        // Named nowhere on the card, it is the artist's
        assertEquals(listOf("Daft Punk"), album.artists)

        assertEquals(
            YouTubeBrowse("MPADUCRr1xG_2WIDs18a6cIiCxeA", "ggMIegYIAhoCAQI%3D"),
            page.sections[1].more,
        )

        val video = page.sections[2].items.first() as SongItem
        assertTrue(video.isVideo)
        assertEquals("a5uQMwRMHcs", video.song.song.id)
        assertNull(video.song.album)
    }

    @Test
    fun discographyGivesEveryAlbum() {
        val page = parser.browsePage(fixture("disco"))

        assertEquals(6, page.items.size)
        val album = (page.items.first() as AlbumItem).album
        assertEquals("MPREb_X1DQ1j0PPrX", album.id)
        assertEquals("GLBTM (Studio Outtakes)", album.name)
        assertEquals(2023, album.year)
        assertNull(page.continuation)
    }

    @Test
    fun playlistPageGivesFirstSongsAndContinuation() {
        val page =
            parser.playlistPage("OLAK5uy_lNVBcjNtiCwq-n95uoxJ-Gd4vsfMkyZNs", fixture("pl_top"))!!

        assertEquals("Top songs", page.playlist.name)
        assertEquals("OLAK5uy_lNVBcjNtiCwq-n95uoxJ-Gd4vsfMkyZNs", page.playlist.remoteId)
        assertEquals(5, page.songs.size)
        assertNotNull(page.continuation)
    }

    @Test
    fun playlistContinuationGivesTheRest() {
        val page = parser.playlistSongs(fixture("pl_cont"))

        assertEquals(6, page.items.size)
        assertNull(page.continuation)
    }

    @Test
    fun watchPageGivesTheSongAndItsTabs() {
        val response = fixture("next")
        val song = parser.queue(response).items.first().song

        assertEquals("khnokW3Mw24", song.song.id)
        assertEquals("Instant Crush (feat. Julian Casablancas)", song.song.name)
        assertEquals(338.seconds, song.song.duration)
        assertEquals(2, song.artists.size)
        assertEquals("Random Access Memories", song.album?.name)
        assertEquals(2013, song.album?.year)

        assertEquals("MPLYt_K8qWMWVqXGi-5", parser.tabBrowseId(response, LYRICS_PREFIX))
        assertEquals("MPTRt_K8qWMWVqXGi-5", parser.tabBrowseId(response, RELATED_PREFIX))
    }

    @Test
    fun radioGoesOn() {
        val first = parser.queue(fixture("radio"))
        assertEquals("a7Wask21bew", first.items.first().song.song.id)
        assertEquals(6, first.items.size)
        assertNotNull(first.continuation)

        val next = parser.queue(fixture("radio_cont"))
        assertEquals(6, next.items.size)
        assertNotNull(next.continuation)
    }

    @Test
    fun homeGivesRowsAndMoreRows() {
        val home = parser.homePage(fixture("home"))
        assertEquals(
            listOf("Quick picks", "Trending community playlists"),
            home.items.map { it.title })
        val pick = home.items[0].items.first() as SongItem
        assertEquals("xukbqwRuN5w", pick.song.song.id)
        assertEquals(listOf("UCecnyZYofHiBVDJpx1XNYOQ"), pick.song.artists.map { it.id })
        assertEquals("MPREb_QC0L98k1tGe", pick.song.album?.id)
        assertTrue(home.items[1].items.all { it is PlaylistItem })
        assertNotNull(home.continuation)

        val more = parser.homePage(fixture("home_cont"))
        assertEquals(
            listOf("Throwbacks", "All-time essentials", "Autumn loading"),
            more.items.map { it.title })
        assertNotNull(more.continuation)
    }

    @Test
    fun relatedGivesRows() {
        val sections = parser.sections(fixture("related"))

        assertEquals(
            listOf(
                "You might also like",
                "Recommended playlists",
                "Other performances",
                "Similar artists",
                "Daft Punk"
            ),
            sections.map { it.title },
        )
        assertTrue(sections[3].items.all { it is ArtistItem })
        // "More from" names the artist, it doesn't list more
        assertEquals("MORE FROM", sections[4].strapline)
        assertNull(sections[4].more)
    }

    @Test
    fun suggestionsGiveQueriesAndItems() {
        val suggestions = parser.suggestions(fixture("sugg"))

        assertTrue("daft punk" in suggestions.queries)
        assertEquals(6, suggestions.queries.size)
        assertEquals(5, suggestions.items.size)
        assertEquals(daftPunk.id, (suggestions.items.first() as ArtistItem).artist.id)
    }

    @Test
    fun lyricsComeWithTheirSource() {
        val lyrics = parser.lyrics(fixture("lyrics"))!!

        assertEquals("First line\nSecond line\n", lyrics.text)
        assertEquals("Source: Example Lyrics", lyrics.credit)
    }

    @Test
    fun unknownIdsGiveNoPage() {
        val empty = fixture("empty")

        assertNull(parser.albumPage("MPREb_x", empty))
        assertNull(parser.artistPage("UCx", empty))
        assertNull(parser.playlistPage("PLx", empty))
        assertNull(parser.lyrics(empty))
        assertTrue(parser.searchPage(empty).items.isEmpty())
        assertTrue(parser.queue(empty).items.isEmpty())
    }
}
