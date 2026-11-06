package com.metrolist.innertubex

import com.metrolist.innertubex.models.response.AccountMenuResponse
import com.metrolist.innertubex.models.response.AccountMenuResponse.Action.OpenPopupAction.Popup.MultiPageMenuRenderer.Header.ActiveAccountHeaderRenderer
import com.metrolist.innertubex.models.response.PlayerResponse
import com.metrolist.innertubex.utils.YouTubeUrlParser
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InnerTubeParsingRegressionTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun musicYoutubePlaylistUrlsAreNotAlwaysAlbums() {
        val parsed = YouTubeUrlParser.parse("https://music.youtube.com/playlist?list=PLabc123")

        assertTrue(parsed is YouTubeUrlParser.ParsedUrl.Playlist)
        assertEquals("PLabc123", parsed.id)
    }

    @Test
    fun musicYoutubeAlbumUrlsUseAlbumIds() {
        val playlistAlbum = YouTubeUrlParser.parse("https://music.youtube.com/playlist?list=OLAK5uy_test")
        val browseAlbum = YouTubeUrlParser.parse("https://music.youtube.com/browse/MPREtest")

        assertTrue(playlistAlbum is YouTubeUrlParser.ParsedUrl.Album)
        assertEquals("OLAK5uy_test", playlistAlbum.id)
        assertTrue(browseAlbum is YouTubeUrlParser.ParsedUrl.Album)
        assertEquals("MPREtest", browseAlbum.id)
        assertEquals(
            YouTubeUrlParser.ParsedUrl.Album("MPREtest"),
            YouTubeUrlParser.parse("https://www.music.youtube.com/browse/MPREtest"),
        )
    }

    @Test
    fun youtubeUrlsRequireAnExactApprovedHostAndShape() {
        val valid = YouTubeUrlParser.parse("youtube.com/watch?v=abcdefghijk")

        assertEquals(YouTubeUrlParser.ParsedUrl.Video("abcdefghijk"), valid)
        assertEquals(
            YouTubeUrlParser.ParsedUrl.Video("abcdefghijk"),
            YouTubeUrlParser.parse("http://youtu.be/abcdefghijk"),
        )
        assertTrue(YouTubeUrlParser.parse("https://www.youtube.com.evil/watch?v=abcdefghijk") == null)
        assertTrue(YouTubeUrlParser.parse("https://www.youtube.com/notv?v=abcdefghijk") == null)
        assertTrue(YouTubeUrlParser.parse("watch this https://www.youtube.com/watch?v=abcdefghijk") == null)
        assertTrue(YouTubeUrlParser.parse("https://youtu.be.evil/abcdefghijk") == null)
    }

    @Test
    fun accountMenuUnknownAndMissingVariantsDecodeSafely() {
        val response =
            json.decodeFromString<AccountMenuResponse>(
                """
                {
                  "actions": [
                    {"unknownAction": {"value": 1}},
                    {"openPopupAction": {"popup": {"multiPageMenuRenderer": {"header": {}}}}},
                    {"openPopupAction": {"popup": {"popupContent": {"multiPageMenuRenderer": {"header": {}}}}}}
                  ],
                  "unknownRoot": true
                }
                """.trimIndent(),
            )

        assertEquals(3, response.actions.size)
        val header =
            response.actions[1]
                .openPopupAction
                ?.popup
                ?.multiPageMenuRenderer
                ?.header
        assertNotNull(header).also { assertNull(it.activeAccountHeaderRenderer) }
        assertNotNull(
            response.actions[2]
                .openPopupAction
                ?.popup
                ?.popupContent
                ?.multiPageMenuRenderer,
        )
        val missingAccount = ActiveAccountHeaderRenderer()
        assertEquals(
            "Unknown",
            missingAccount.toAccountInfo().name,
        )
    }

    @Test
    fun partialPlayerFormatsDoNotDropWholeResponse() {
        val response =
            json.decodeFromString<PlayerResponse>(
                """
                {
                  "playabilityStatus": { "status": "OK" },
                  "streamingData": {
                    "adaptiveFormats": [
                      { "url": "https://example.test/videoplayback" }
                    ]
                  }
                }
                """.trimIndent(),
            )

        val format = assertNotNull(response.streamingData?.adaptiveFormats?.singleOrNull())
        assertEquals(-1, format.itag)
        assertEquals("", format.mimeType)
        assertEquals(0, format.bitrate)
        assertEquals("https://example.test/videoplayback", format.url)
    }
}
