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
package me.knighthat.kreate3.ui.screen.detail

import androidx.lifecycle.SavedStateHandle
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import me.knighthat.kreate3.database.model.Album
import me.knighthat.kreate3.database.model.Artist
import me.knighthat.kreate3.database.repository.UserSongRepository
import me.knighthat.kreate3.notification.Notifier
import me.knighthat.kreate3.provider.youtube.YouTubeProvider
import me.knighthat.kreate3.source.DeviceReactions
import me.knighthat.kreate3.ui.navigation.LocalAlbum
import me.knighthat.kreate3.ui.navigation.LocalArtist
import me.knighthat.kreate3.ui.navigation.YouTubePlaylist
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.Proxy
import java.util.Collections

/**
 * The YouTube detail screens fetch live, the answers here are the smallest YouTube would give.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class YouTubeDetailViewModelTest {

    private val requests: MutableList<String> = Collections.synchronizedList(mutableListOf())

    /** Bookmarks and follows kept on the device, by id. */
    private class Reactions : DeviceReactions {
        val bookmarked = Collections.synchronizedMap(mutableMapOf<String, Boolean>())
        val followed = Collections.synchronizedMap(mutableMapOf<String, Boolean>())

        override suspend fun isBookmarked(albumId: String) = bookmarked[albumId] == true

        override suspend fun setBookmarked(album: Album, on: Boolean) {
            bookmarked[album.id] = on
        }

        override suspend fun isFollowed(artistId: String) = followed[artistId] == true

        override suspend fun setFollowed(artist: Artist, on: Boolean) {
            followed[artist.id] = on
        }
    }

    private val device = Reactions()

    /** Ids of the songs the user disliked. */
    private val disliked = MutableStateFlow(emptySet<String>())

    // The screens only ask the library which songs are disliked
    private val library = Proxy.newProxyInstance(
        UserSongRepository::class.java.classLoader,
        arrayOf(UserSongRepository::class.java),
    ) { _, method, _ ->
        if (method.name == "dislikedIds") disliked
        else throw UnsupportedOperationException(method.name)
    } as UserSongRepository

    private fun provider(
        status: HttpStatusCode = HttpStatusCode.OK,
        answer: (body: String) -> String,
    ) = YouTubeProvider(
        HttpClient(MockEngine { request ->
            val body = request.body.toByteArray().decodeToString()
            requests += body
            respond(
                content = answer(body),
                status = status,
                headers = Headers.build { append(HttpHeaders.ContentType, "application/json") },
            )
        }),
        Json,
        // Nobody is signed in
        cookies = { "" },
        pageId = { "" },
    )

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun albumShowsItsSongsInOrder() = runTest {
        val viewModel = YouTubeAlbumDetailViewModel(
            SavedStateHandle(mapOf(LocalAlbum.ID to "MPREb_1")),
            provider { ALBUM },
            device,
            library,
            backgroundScope,
            Notifier(),
        )

        // The answer comes on the client's own thread
        val state = viewModel.state.first { it !is LiveState.Loading }

        val album = (state as LiveState.Ready).value
        assertEquals("Album", album.name)
        assertEquals("Artist · 2001", album.subtitle)
        assertEquals(listOf("v1", "v2"), album.songs.map { it.mediaId })
        assertTrue(requests.single().contains("\"browseId\":\"MPREb_1\""))
        assertEquals("https://music.youtube.com/browse/MPREb_1", viewModel.shareLink().getOrNull())
    }

    @Test
    fun dislikedSongsAreLeftOutAlsoWhenDislikedWhileShown() = runTest {
        disliked.value = setOf("v1")
        val viewModel = YouTubeAlbumDetailViewModel(
            SavedStateHandle(mapOf(LocalAlbum.ID to "MPREb_1")),
            provider { ALBUM },
            device,
            library,
            backgroundScope,
            Notifier(),
        )

        val album = (viewModel.state.first { it !is LiveState.Loading } as LiveState.Ready).value
        assertEquals(listOf("v2"), album.songs.map { it.mediaId })

        disliked.value = setOf("v1", "v2")
        viewModel.state.first { (it as LiveState.Ready).value.songs.isEmpty() }
        // Nothing asked again
        assertEquals(1, requests.size)
    }

    @Test
    fun dislikedSongsOfAPlaylistAreLeftOut() = runTest {
        disliked.value = setOf("v2")
        val viewModel = YouTubePlaylistDetailViewModel(
            SavedStateHandle(mapOf(YouTubePlaylist.ID to "PL1")),
            provider { if (it.contains("\"continuation\"")) PLAYLIST_MORE else PLAYLIST },
            library,
        )

        assertEquals(listOf("v1"), viewModel.songs.first { it.isNotEmpty() }.map { it.mediaId })
        viewModel.loadMore()
        assertEquals(listOf("v1", "v3"), viewModel.songs.first { it.size > 1 }.map { it.mediaId })
    }

    @Test
    fun aBookmarkWhileSignedOutStaysOnTheDevice() = runTest {
        val viewModel = YouTubeAlbumDetailViewModel(
            SavedStateHandle(mapOf(LocalAlbum.ID to "MPREb_1")),
            provider { ALBUM },
            device,
            library,
            backgroundScope,
            Notifier(),
        )
        viewModel.state.first { it !is LiveState.Loading }
        assertEquals(false, viewModel.bookmark.isOn.value)

        viewModel.bookmark.toggle()
        runCurrent()

        assertEquals(true, viewModel.bookmark.isOn.value)
        assertEquals(mapOf("MPREb_1" to true), device.bookmarked)
        // Only the page was asked, nothing went to YouTube
        assertEquals(1, requests.size)
    }

    @Test
    fun aFollowWhileSignedOutStaysOnTheDevice() = runTest {
        device.followed["UC1"] = true
        val viewModel = YouTubeArtistDetailViewModel(
            SavedStateHandle(mapOf(LocalArtist.ID to "UC1")),
            provider { ARTIST },
            device,
            library,
            backgroundScope,
            Notifier(),
        )
        viewModel.state.first { it !is LiveState.Loading }
        assertEquals(true, viewModel.follow.isOn.value)

        viewModel.follow.toggle()
        runCurrent()

        assertEquals(false, viewModel.follow.isOn.value)
        assertEquals(mapOf("UC1" to false), device.followed)
        assertEquals(1, requests.size)
    }

    @Test
    fun unknownAlbumSaysSo() = runTest {
        val viewModel = YouTubeAlbumDetailViewModel(
            SavedStateHandle(mapOf(LocalAlbum.ID to "MPREb_x")),
            provider { "{}" },
            device,
            library,
            backgroundScope,
            Notifier(),
        )

        val state = viewModel.state.first { it !is LiveState.Loading }

        assertEquals(LiveState.Failed("YouTube doesn't have this album"), state)
    }

    @Test
    fun failedRequestCanBeTriedAgain() = runTest {
        val viewModel = YouTubeAlbumDetailViewModel(
            SavedStateHandle(mapOf(LocalAlbum.ID to "MPREb_1")),
            provider(HttpStatusCode.TooManyRequests) { "{}" },
            device,
            library,
            backgroundScope,
            Notifier(),
        )

        assertTrue(viewModel.state.first { it !is LiveState.Loading } is LiveState.Failed)
        viewModel.load()
        viewModel.state.first { it is LiveState.Loading }
        assertTrue(viewModel.state.first { it !is LiveState.Loading } is LiveState.Failed)
        assertEquals(2, requests.size)
    }

    @Test
    fun playlistSongsComePartAfterPart() = runTest {
        val viewModel = YouTubePlaylistDetailViewModel(
            SavedStateHandle(mapOf(YouTubePlaylist.ID to "PL1")),
            provider { if (it.contains("\"continuation\"")) PLAYLIST_MORE else PLAYLIST },
            library,
        )

        val playlist = (viewModel.state.first { it !is LiveState.Loading } as LiveState.Ready).value
        assertEquals("Playlist", playlist.name)
        assertEquals(
            listOf("v1", "v2"),
            viewModel.songs.first { it.isNotEmpty() }.map { it.mediaId })
        assertTrue(viewModel.hasMore.value)
        assertTrue(requests.single().contains("\"browseId\":\"VLPL1\""))

        viewModel.loadMore()
        viewModel.hasMore.first { !it }

        // v2 came twice, it shows once
        assertEquals(
            listOf("v1", "v2", "v3"),
            viewModel.songs.first { it.size > 2 }.map { it.mediaId })
        assertFalse(viewModel.hasMore.value)
        viewModel.loadMore()
        assertEquals(2, requests.size)
    }

    private companion object {

        fun song(id: String) = """{"musicResponsiveListItemRenderer":{"flexColumns":[
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Song $id"}]}}}],
            "playlistItemData":{"videoId":"$id"}}}"""

        fun header(title: String) = """{"musicResponsiveHeaderRenderer":{
            "title":{"runs":[{"text":"$title"}]},
            "subtitle":{"runs":[{"text":"Album"},{"text":" • "},{"text":"2001"}]},
            "straplineTextOne":{"runs":[{"text":"Artist","navigationEndpoint":{"browseEndpoint":{"browseId":"UC1"}}}]}}}"""

        fun page(title: String, shelf: String) = """{"contents":{"twoColumnBrowseResultsRenderer":{
            "tabs":[{"tabRenderer":{"content":{"sectionListRenderer":{"contents":[${header(title)}]}}}}],
            "secondaryContents":{"sectionListRenderer":{"contents":[$shelf]}}}}}"""

        val ALBUM =
            page("Album", """{"musicShelfRenderer":{"contents":[${song("v1")},${song("v2")}]}}""")

        const val ARTIST =
            """{"header":{"musicImmersiveHeaderRenderer":{"title":{"runs":[{"text":"Artist"}]}}}}"""

        val PLAYLIST = page(
            "Playlist",
            """{"musicPlaylistShelfRenderer":{"contents":[${song("v1")},${song("v2")},
            {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"T"}}}}]}}""",
        )

        val PLAYLIST_MORE = """{"onResponseReceivedActions":[{"appendContinuationItemsAction":{
            "continuationItems":[${song("v2")},${song("v3")}]}}]}"""
    }
}
