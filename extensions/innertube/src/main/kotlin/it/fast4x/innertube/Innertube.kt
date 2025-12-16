package it.fast4x.innertube

import com.zionhuang.innertube.pages.LibraryContinuationPage
import com.zionhuang.innertube.pages.LibraryPage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.userAgent
import it.fast4x.innertube.clients.YouTubeLocale
import it.fast4x.innertube.models.AccountInfo
import it.fast4x.innertube.models.AccountMenuResponse
import it.fast4x.innertube.models.BrowseResponse
import it.fast4x.innertube.models.Context
import it.fast4x.innertube.models.Context.Client
import it.fast4x.innertube.models.Context.Companion.DefaultWeb
import it.fast4x.innertube.models.GridRenderer
import it.fast4x.innertube.models.MusicNavigationButtonRenderer
import it.fast4x.innertube.models.MusicShelfRenderer
import it.fast4x.innertube.models.NavigationEndpoint
import it.fast4x.innertube.models.PlaylistPanelVideoRenderer
import it.fast4x.innertube.models.Runs
import it.fast4x.innertube.models.Thumbnail
import it.fast4x.innertube.models.bodies.AccountMenuBody
import it.fast4x.innertube.models.bodies.Action
import it.fast4x.innertube.models.bodies.BrowseBody
import it.fast4x.innertube.models.bodies.CreatePlaylistBody
import it.fast4x.innertube.models.bodies.EditPlaylistBody
import it.fast4x.innertube.models.bodies.LikeBody
import it.fast4x.innertube.models.bodies.PlaylistDeleteBody
import it.fast4x.innertube.models.bodies.SubscribeBody
import it.fast4x.innertube.utils.YoutubePreferences
import it.fast4x.innertube.utils.parseCookieString
import it.fast4x.innertube.utils.sha1
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import java.net.Proxy
import java.util.Locale

object Innertube {

    private const val YOUTUBE_MUSIC_HOST = "music.youtube.com"

    @OptIn(ExperimentalSerializationApi::class)
    lateinit var client: HttpClient

    var proxy: Proxy? = null
        set(value) {
            field = value
            client.close()
            client
        }

    var locale = YouTubeLocale(
        gl = Locale.getDefault().country,
        hl = Locale.getDefault().toLanguageTag()
    )
    var visitorData: String = YoutubePreferences.preference?.visitordata.toString()
    var dataSyncId: String? = YoutubePreferences.preference?.dataSyncId.toString()

    var cookieMap = emptyMap<String, String>()
    var cookie: String? = YoutubePreferences.preference?.cookie
        set(value) {
            field = value
            cookieMap = if (value == null) emptyMap() else parseCookieString(value)
        }

    private var poTokenChallengeRequestKey = "O43z0dpjhgX20SCx4KAo"

    internal const val browse = "/youtubei/v1/browse"
    internal const val next = "/youtubei/v1/next"
    internal const val player = "/youtubei/v1/player"
    internal const val queue = "/youtubei/v1/music/get_queue"
    internal const val search = "/youtubei/v1/search"
    internal const val searchSuggestions = "/youtubei/v1/music/get_search_suggestions"
    internal const val accountMenu = "/youtubei/v1/account/account_menu"
    internal const val playlistCreate = "/youtubei/v1/playlist/create"
    internal const val playlistDelete = "/youtubei/v1/playlist/delete"
    internal const val playlistEdit = "/youtubei/v1/browse/edit_playlist"
    internal const val subscribe = "/youtubei/v1/subscription/subscribe"
    internal const val unsubscribe = "/youtubei/v1/subscription/unsubscribe"
    internal const val like = "/youtubei/v1/like/like"
    internal const val removelike = "/youtubei/v1/like/removelike"

    internal const val musicResponsiveListItemRendererMask = "musicResponsiveListItemRenderer(flexColumns,fixedColumns,thumbnail,navigationEndpoint,badges)"
    internal const val musicTwoRowItemRendererMask = "musicTwoRowItemRenderer(thumbnailRenderer,title,subtitle,navigationEndpoint)"
    const val playlistPanelVideoRendererMask = "playlistPanelVideoRenderer(title,navigationEndpoint,longBylineText,shortBylineText,thumbnail,lengthText,badges)"

    internal fun HttpRequestBuilder.mask(value: String = "*") =
        header("X-Goog-FieldMask", value)


    @Serializable
    data class Info<T : NavigationEndpoint.Endpoint>(
        val name: String?,
        val endpoint: T?
    ) {
        @Suppress("UNCHECKED_CAST")
        constructor(run: Runs.Run) : this(
            name = run.text,
            endpoint = run.navigationEndpoint?.endpoint as T?
        )
    }

    @JvmInline
    value class SearchFilter(val value: String) {
        companion object {
            val Song = SearchFilter("EgWKAQIIAWoKEAkQBRAKEAMQBA%3D%3D")
            val Video = SearchFilter("EgWKAQIQAWoKEAkQChAFEAMQBA%3D%3D")
            val Album = SearchFilter("EgWKAQIYAWoKEAkQChAFEAMQBA%3D%3D")
            val Artist = SearchFilter("EgWKAQIgAWoKEAkQChAFEAMQBA%3D%3D")
            val CommunityPlaylist = SearchFilter("EgeKAQQoAEABagoQAxAEEAoQCRAF")
            val FeaturedPlaylist = SearchFilter("EgeKAQQoADgBagwQDhAKEAMQBRAJEAQ%3D")
            val Podcast = SearchFilter("EgWKAQJQAWoIEBAQERADEBU%3D")
        }
    }

    @Serializable
    sealed class Item {
        abstract val thumbnail: Thumbnail?
        abstract val key: String
        abstract val title: String?
    }

    @Serializable
    data class SongItem(
        val info: Info<NavigationEndpoint.Endpoint.Watch>?,
        val authors: List<Info<NavigationEndpoint.Endpoint.Browse>>?,
        val album: Info<NavigationEndpoint.Endpoint.Browse>?,
        val durationText: String?,
        override val thumbnail: Thumbnail?,
        val explicit: Boolean = false,
        val setVideoId: String? = null
    ) : Item() {
        override val key get() = info?.endpoint?.videoId ?: ""
        override val title get() = info?.name

        companion object {

            fun parse( plRenderer: PlaylistPanelVideoRenderer ): SongItem {
                val watchEndpoint = plRenderer.navigationEndpoint?.watchEndpoint
                requireNotNull( watchEndpoint?.videoId )

                //<editor-fold defaultstate="collapsed" desc="Author & album parser">
                val authors = mutableListOf<Info<NavigationEndpoint.Endpoint.Browse>>()
                var album: Info<NavigationEndpoint.Endpoint.Browse>? = null
                plRenderer.longBylineText
                          ?.runs
                          ?.filter { it.navigationEndpoint != null }
                          ?.groupBy {
                              it.navigationEndpoint
                                  ?.browseEndpoint
                                  ?.browseEndpointContextSupportedConfigs
                                  ?.browseEndpointContextMusicConfig
                                  ?.pageType
                          }
                          ?.mapNotNull { (pageType, runs) ->
                              when (pageType) {
                                  "MUSIC_PAGE_TYPE_ARTIST" -> authors.addAll( runs.map( ::Info ) )
                                  "MUSIC_PAGE_TYPE_ALBUM"  -> album = runs.firstOrNull()?.let( ::Info )
                                  else -> return@mapNotNull
                              } 
                          }
                //</editor-fold>
                
                return SongItem(
                    info = Info(
                        // Mustn't add [EXPLICIT_KEY_PREFIX] here
                        name = plRenderer.title?.text.orEmpty(),
                        endpoint = watchEndpoint
                    ),
                    authors = authors,
                    album = album,
                    durationText = plRenderer.lengthText?.text,
                    thumbnail = plRenderer.thumbnail
                                          ?.thumbnails
                                          ?.maxByOrNull {
                                              it.height ?: 0
                                          },
                    explicit = plRenderer.badges
                                         .any {
                                             it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                                         }
                )
            }
        }
    }

    @Serializable
    data class VideoItem(
        val info: Info<NavigationEndpoint.Endpoint.Watch>?,
        val authors: List<Info<NavigationEndpoint.Endpoint.Browse>>?,
        val viewsText: String?,
        val durationText: String?,
        override val thumbnail: Thumbnail?
    ) : Item() {
        override val key get() = info!!.endpoint!!.videoId!!
        override val title get() = info?.name

        val isOfficialMusicVideo: Boolean
            get() = info
                ?.endpoint
                ?.watchEndpointMusicSupportedConfigs
                ?.watchEndpointMusicConfig
                ?.musicVideoType == "MUSIC_VIDEO_TYPE_OMV"

        val isUserGeneratedContent: Boolean
            get() = info
                ?.endpoint
                ?.watchEndpointMusicSupportedConfigs
                ?.watchEndpointMusicConfig
                ?.musicVideoType == "MUSIC_VIDEO_TYPE_UGC"

        companion object
    }

    @Serializable
    data class AlbumItem(
        val info: Info<NavigationEndpoint.Endpoint.Browse>?,
        val authors: List<Info<NavigationEndpoint.Endpoint.Browse>>?,
        val year: String?,
        val playlistId: String? = null,
        override val thumbnail: Thumbnail?
    ) : Item() {
        override val key get() = info!!.endpoint!!.browseId!!
        override val title get() = info?.name

        companion object
    }

    @Serializable
    data class ArtistItem(
        val info: Info<NavigationEndpoint.Endpoint.Browse>?,
        val subscribersCountText: String?,
        val channelId: String? = null,
        override val thumbnail: Thumbnail?
    ) : Item() {
        override val key get() = info!!.endpoint!!.browseId!!
        override val title get() = info?.name

        companion object
    }

    @Serializable
    data class PlaylistItem(
        val info: Info<NavigationEndpoint.Endpoint.Browse>?,
        val channel: Info<NavigationEndpoint.Endpoint.Browse>?,
        val songCount: Int?,
        val isEditable: Boolean?,
        override val thumbnail: Thumbnail?
    ) : Item() {
        override val key get() = info!!.endpoint!!.browseId!!
        override val title get() = info?.name

        companion object
    }

    data class ArtistInfoPage(
        val name: String?,
        val description: String?,
        val subscriberCountText: String?,
        val thumbnail: Thumbnail?,
        val shuffleEndpoint: NavigationEndpoint.Endpoint.Watch?,
        val radioEndpoint: NavigationEndpoint.Endpoint.Watch?,
        val songs: List<SongItem>?,
        val songsEndpoint: NavigationEndpoint.Endpoint.Browse?,
        val albums: List<AlbumItem>?,
        val albumsEndpoint: NavigationEndpoint.Endpoint.Browse?,
        val singles: List<AlbumItem>?,
        val singlesEndpoint: NavigationEndpoint.Endpoint.Browse?,
        val playlists: List<PlaylistItem>?,
    )

    data class PlaylistOrAlbumPage(
        val title: String?,
        val authors: List<Info<NavigationEndpoint.Endpoint.Browse>>?,
        val year: String?,
        val thumbnail: Thumbnail?,
        val url: String?,
        val songsPage: ItemsPage<SongItem>?,
        val otherVersions: List<AlbumItem>?,
        val description: String?,
        val otherInfo: String?
    )

    data class NextPage(
        val itemsPage: ItemsPage<SongItem>?,
        val playlistId: String?,
        val params: String? = null,
        val playlistSetVideoId: String? = null
    )

    @Serializable
    data class RelatedPage(
        val songs: List<SongItem>? = null,
        val playlists: List<PlaylistItem>? = null,
        val albums: List<AlbumItem>? = null,
        val artists: List<ArtistItem>? = null,
    )
    data class RelatedSongs(
        val songs: List<SongItem>? = null
    )

    @Serializable
    data class DiscoverPage(
        val newReleaseAlbums: List<AlbumItem>,
        val moods: List<Mood.Item>
    )

    data class DiscoverPageAlbums(
        val newReleaseAlbums: List<AlbumItem>

    )

    @Serializable
    data class Mood(
        val title: String,
        val items: List<Item>
    ) {
        @Serializable
        data class Item(
            val title: String,
            val stripeColor: Long,
            val endpoint: NavigationEndpoint.Endpoint.Browse
        )
    }

    data class ItemsPage<T : Item>(
        var items: List<T>?,
        val continuation: String?
    )

    @Serializable
    data class ChartsPage(
        val playlists: List<PlaylistItem>? = null,
        val artists: List<ArtistItem>? = null,
        val videos: List<VideoItem>? = null,
        val songs: List<SongItem>? = null,
        val trending: List<SongItem>? = null
    )

    data class Podcast(
        val title: String,
        val author: String?,
        val authorThumbnail: String?,
        val thumbnail: List<Thumbnail>,
        val description: String?,
        val listEpisode: List<EpisodeItem>
    ) {
        data class EpisodeItem(
            val title: String,
            val author: String?,
            val description: String?,
            val thumbnail: List<Thumbnail>,
            val createdDay: String?,
            val durationString: String?,
            val videoId: String
        )
    }

    data class SearchSuggestions(
        val queries: List<String>,
        val recommendedSong: SongItem?,
        val recommendedAlbum: AlbumItem?,
        val recommendedArtist: ArtistItem?,
        val recommendedPlaylist: PlaylistItem?,
        val recommendedVideo: VideoItem?,
    )

    fun MusicNavigationButtonRenderer.toMood(): Mood.Item? {
        return Mood.Item(
            title = buttonText.runs.firstOrNull()?.text ?: return null,
            stripeColor = solid?.leftStripeColor ?: return null,
            endpoint = clickCommand.browseEndpoint ?: return null
        )
    }

    fun List<Thumbnail>.getBestQuality() =
        maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }

    suspend fun accountInfo(): Result<AccountInfo?> = runCatching {
        accountMenu()
            .body<AccountMenuResponse>()
            .actions?.get(0)?.openPopupAction?.popup?.multiPageMenuRenderer
            ?.header?.activeAccountHeaderRenderer
            ?.toAccountInfo()
    }

    suspend fun accountMenu(): HttpResponse {
        val response =
            client.post(accountMenu) {
                setLogin(setLogin = true)
                setBody(AccountMenuBody())
            }

        println("YoutubeLogin Innertube accountMenuBody: ${AccountMenuBody()}")
        println("YoutubeLogin Innertube accountMenu RESPONSE: ${response.bodyAsText()}")

        return response
    }

    fun HttpRequestBuilder.setLogin(clientType: Client = DefaultWeb.client, setLogin: Boolean = false) {
        contentType(ContentType.Application.Json)
        headers {
            append("X-YouTube-Client-Name", "${clientType.xClientName ?: 1}")
            append("X-YouTube-Client-Version", clientType.clientVersion)
            append("X-Origin", "https://$YOUTUBE_MUSIC_HOST")
            if (clientType.referer != null) {
                append("Referer", clientType.referer)
            }
            if (setLogin) {
                cookie?.let { cookie ->
                    cookieMap = parseCookieString(cookie)
                    append("X-Goog-Authuser", "0")
                    append("X-Goog-Visitor-Id", visitorData)
                    append("Cookie", cookie)
                    if ("SAPISID" !in cookieMap || "__Secure-3PAPISID" !in cookieMap) return@let
                    val currentTime = System.currentTimeMillis() / 1000
                    val sapisidCookie = cookieMap["SAPISID"] ?: cookieMap["__Secure-3PAPISID"]
                    val sapisidHash = sha1("$currentTime $sapisidCookie https://$YOUTUBE_MUSIC_HOST")
                    append("Authorization", "SAPISIDHASH ${currentTime}_$sapisidHash")
                }
            }
        }
        clientType.userAgent?.let { userAgent(it) }
        parameter("prettyPrint", false)

    }

    /*******************************************
     * NEW CODE
     */

    suspend fun createPlaylist(
        ytClient: Client,
        title: String,
    ) = client.post(playlistCreate) {
        setLogin(ytClient, true)
        setBody(
            CreatePlaylistBody(
                context = Context.DefaultWebWithLocale,
                title = title
            )
        )
    }

    suspend fun deletePlaylist(
        ytClient: Client,
        playlistId: String,
    ) = client.post(playlistDelete) {
        println("deleting $playlistId")
        setLogin(ytClient, setLogin = true)
        setBody(
            PlaylistDeleteBody(
                context = Context.DefaultWebWithLocale,
                playlistId = playlistId
            )
        )
    }

    suspend fun renamePlaylist(
        ytClient: Client,
        playlistId: String,
        name: String,
    ) = client.post(playlistEdit) {
        setLogin(ytClient, setLogin = true)
        setBody(
            EditPlaylistBody(
                context = Context.DefaultWebWithLocale,
                playlistId = playlistId,
                actions = listOf(
                    Action.RenamePlaylistAction(
                        playlistName = name
                    )
                )
            )
        )
    }

    suspend fun addToPlaylist(
        ytClient: Client,
        playlistId: String,
        videoId: String,
    ) = addToPlaylist(ytClient, playlistId, listOf(videoId))

    suspend fun addToPlaylist(
        ytClient: Client,
        playlistId: String,
        videoIds: List<String>,
    ) = client.post(playlistEdit) {
        setLogin(ytClient, setLogin = true)
        setBody(
            EditPlaylistBody(
                context = Context.DefaultWebWithLocale,
                playlistId = playlistId.removePrefix("VL"),
                actions = videoIds.map{ Action.AddVideoAction(addedVideoId = it)}
            )
        )
    }

    suspend fun removeFromPlaylist(
        ytClient: Client,
        playlistId: String,
        videoId: String,
        setVideoId: String? = null,
    ) = removeFromPlaylist(ytClient, playlistId, videoId, listOf(setVideoId))

    suspend fun removeFromPlaylist(
        ytClient: Client,
        playlistId: String,
        videoId: String,
        setVideoIds: List<String?>,
    ) = client.post(playlistEdit) {
        setLogin(ytClient, setLogin = true)
        setBody(
            EditPlaylistBody(
                context = Context.DefaultWebWithLocale,
                playlistId = playlistId.removePrefix("VL"),
                actions = setVideoIds.map {
                    Action.RemoveVideoAction(
                        removedVideoId = videoId,
                        setVideoId = it,
                    )
                }
            )
        )
    }

    suspend fun addPlaylistToPlaylist(
        ytClient: Client,
        playlistId: String,
        addPlaylistId: String,
    ) = client.post(playlistEdit) {
        setLogin(ytClient, setLogin = true)
        setBody(
            EditPlaylistBody(
                context = Context.DefaultWebWithLocale,
                playlistId = playlistId.removePrefix("VL"),
                actions = listOf(
                    Action.AddPlaylistAction(addedFullListId = addPlaylistId)
                )
            )
        )
    }

    suspend fun subscribeChannel(
        channelId: String,
    ) = client.post(subscribe) {
        setLogin(setLogin = true)
        setBody(
            SubscribeBody(
                context = DefaultWeb,
                channelIds = listOf(channelId)
            )
        )
    }

    suspend fun unsubscribeChannel(
        channelId: String,
    ) = client.post(unsubscribe) {
        setLogin(setLogin = true)
        setBody(
            SubscribeBody(
                context = DefaultWeb,
                channelIds = listOf(channelId)
            )
        )
    }


    suspend fun likePlaylistOrAlbum(
        playlistId: String,
    ) = client.post(like) {
        setLogin(setLogin = true)
        setBody(
            LikeBody(
                context = DefaultWeb,
                target = LikeBody.Target.PlaylistTarget(playlistId = playlistId)
            )
        )
    }

    suspend fun removelikePlaylistOrAlbum(
        playlistId: String,
    ) = client.post(removelike) {
        setLogin(setLogin = true)
        setBody(
            LikeBody(
                context = DefaultWeb,
                target = LikeBody.Target.PlaylistTarget(playlistId = playlistId)
            )
        )
    }

    suspend fun likeVideoOrSong(
        videoId: String,
    ) = client.post(like) {
        setLogin(setLogin = true)
        setBody(
            LikeBody(
                context = DefaultWeb,
                target = LikeBody.Target.VideoTarget(videoId = videoId)
            )
        )
    }

    suspend fun removelikeVideoOrSong(
        videoId: String,
    ) = client.post(removelike) {
        setLogin(setLogin = true)
        setBody(
            LikeBody(
                context = DefaultWeb,
                target = LikeBody.Target.VideoTarget(videoId = videoId)
            )
        )
    }

    suspend fun browse(
        ytClient: Client = DefaultWeb.client,
        browseId: String? = null,
        params: String? = null,
        continuation: String? = null,
        setLogin: Boolean = false,
    ) = client.post(browse) {
        setLogin(ytClient, true)
        setBody(
            BrowseBody(
                context = Context.DefaultWebWithLocale,
                browseId = browseId,
                params = params,
            )
        )
        parameter("continuation", continuation)
        parameter("ctoken", continuation)
        if (continuation != null) {
            parameter("type", "next")
        }
    }

    private fun HttpRequestBuilder.poHeader() {
        headers {
            header("accept", "*/*")
            header("origin", "https://www.youtube.com")
            header("content-type", "application/json+protobuf")
            header("priority", "u=1, i")
            header("referer", "https://www.youtube.com/")
            header("sec-ch-ua", "\"Microsoft Edge\";v=\"131\", \"Chromium\";v=\"131\", \"Not_A Brand\";v=\"24\"")
            header("sec-ch-ua-mobile", "?0")
            header("sec-ch-ua-platform", "\"macOS\"")
            header("sec-fetch-dest", "empty")
            header("sec-fetch-mode", "cors")
            header("sec-fetch-site", "cross-site")
            header(
                "user-agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.0.0",
            )
            header("x-goog-api-key", "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw")
            header("x-user-agent", "grpc-web-javascript/0.1")
        }
    }

    suspend fun createPoTokenChallenge() =
        client.post(
            "https://jnn-pa.googleapis.com/\$rpc/google.internal.waa.v1.Waa/Create",
        ) {
            poHeader()
            setBody("[\"$poTokenChallengeRequestKey\"]")
        }

    suspend fun generatePoToken(challenge: String) =
        client.post(
            "https://jnn-pa.googleapis.com/\$rpc/google.internal.waa.v1.Waa/GenerateIT",
        ) {
            poHeader()
            setBody("[\"$poTokenChallengeRequestKey\", \"$challenge\"]")
        }

    suspend fun library(browseId: String, tabIndex: Int = 0) = runCatching {
        val response = browse(
            browseId = browseId,
            setLogin = true
        ).body<BrowseResponse>()

        val tabs = response.contents?.singleColumnBrowseResultsRenderer?.tabs

        val contents = if (tabs != null && tabs.size >= tabIndex) {
            tabs[tabIndex].tabRenderer?.content?.sectionListRenderer?.contents?.firstOrNull()
        }
        else {
            null
        }

        when {
            contents?.gridRenderer != null -> {
                contents.gridRenderer.items
                    ?.mapNotNull (GridRenderer.Item::musicTwoRowItemRenderer)
                    ?.mapNotNull { LibraryPage.fromMusicTwoRowItemRenderer(it) }?.let {
                        LibraryPage(
                            items = it,
                            continuation = contents.gridRenderer.continuations?.firstOrNull()?.nextContinuationData?.continuation
                        )
                    }
            }

            else -> {
                LibraryPage(
                    items = contents?.musicShelfRenderer?.contents!!
                        .mapNotNull (MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                        .mapNotNull { LibraryPage.fromMusicResponsiveListItemRenderer(it) },
                    continuation = contents.musicShelfRenderer.continuations?.firstOrNull()?.
                    nextContinuationData?.continuation
                )
            }
        }
    }

    suspend fun libraryContinuation(continuation: String) = runCatching {
        val response = browse(
            continuation = continuation,
            setLogin = true
        ).body<BrowseResponse>()

        val contents = response.continuationContents

        when {
            contents?.gridContinuation != null -> {
                contents.gridContinuation.items
                    ?.mapNotNull (GridRenderer.Item::musicTwoRowItemRenderer)
                    ?.mapNotNull { LibraryPage.fromMusicTwoRowItemRenderer(it) }?.let {
                        LibraryContinuationPage(
                            items = it,
                            continuation = contents.gridContinuation.continuations?.firstOrNull()?.nextContinuationData?.continuation
                        )
                    }
            }

            else -> {
                LibraryContinuationPage(
                    items = contents?.musicShelfContinuation?.contents!!
                        .mapNotNull (MusicShelfRenderer.Content::musicResponsiveListItemRenderer)
                        .mapNotNull { LibraryPage.fromMusicResponsiveListItemRenderer(it) },
                    continuation = contents.musicShelfContinuation.continuations?.firstOrNull()?.
                    nextContinuationData?.continuation
                )
            }
        }
    }

}
