package app.kreate.internal.innertube

import app.kreate.exceptions.NotLoggedInException
import app.kreate.gateway.innertube.Account
import app.kreate.gateway.innertube.YouTube
import app.kreate.gateway.innertube.models.AccountInfo
import app.kreate.gateway.innertube.models.InnertubeAlbum
import app.kreate.gateway.innertube.models.InnertubeArtist
import app.kreate.gateway.innertube.models.InnertubeItem
import app.kreate.gateway.innertube.models.InnertubePlaylist
import app.kreate.gateway.innertube.models.InnertubeSearch
import app.kreate.gateway.innertube.models.InnertubeSearchSuggestion
import app.kreate.gateway.innertube.responses.BrowseResponse
import app.kreate.gateway.innertube.responses.Continuation
import app.kreate.gateway.innertube.responses.MusicShelfRenderer
import app.kreate.gateway.innertube.responses.Runs
import app.kreate.gateway.innertube.responses.SectionListRenderer
import app.kreate.gateway.innertube.responses.Tabs
import app.kreate.gateway.innertube.responses.Thumbnails
import app.kreate.internal.innertube.models.createInnertubeAlbumFrom
import app.kreate.internal.innertube.models.createInnertubeArtistFrom
import app.kreate.internal.innertube.models.createInnertubeItemFrom
import app.kreate.internal.innertube.models.createInnertubePlaylistFrom
import app.kreate.internal.innertube.models.createInnertubeSearchSuggestionItemFrom
import app.kreate.internal.innertube.responses.RunsImpl
import app.kreate.internal.innertube.responses.ThumbnailsImpl
import app.kreate.internal.innertube.utils.firstText
import app.kreate.preferences.Preferences
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

internal class YouTubeImpl : YouTube, Account {

    private val json: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    override val account: Account = this

    //region Helper functions
    private fun extractSingleColumnFirstTabRenderer (response: BrowseResponse): Tabs.Tab.Renderer =
        requireNotNull(
            response.contents?.singleColumnBrowseResultsRenderer?.tabs?.firstOrNull()?.tabRenderer
        ) { "BrowseResponse.content.singleColumnBrowseResultsRenderer doesn't have any tabs" }

    private fun extractListContent( tab: Tabs.Tab.Renderer ): SectionListRenderer.Content =
        requireNotNull(
            tab.content?.sectionListRenderer?.contents?.firstOrNull()
        ) { "Tabs.Tab.Renderer doesn't have any sectionListRenderer contents" }

    private fun checkLoginStatus() {
        if( !isLoggedIn() )
            throw NotLoggedInException("YouTube credentials not found")
    }
    //endregion

    override suspend fun getSearchResults(
        query: String?,
        continuation: String?,
        params: String?
    ): Result<InnertubeSearch> = runCatching {
        // Throw error if both are provided
        require( !(!query.isNullOrBlank() && !continuation.isNullOrBlank()) ) {
            "Can't fetch results with both query and continuation provided"
        }
        // Also throw when neither provided
        require( !(query.isNullOrBlank() && continuation.isNullOrBlank()) ) {
            "Can't fetch results with both query and continuation are null"
        }

        searchResults( query, params, continuation )
            .let { response ->
                val renderer = response.contents
                    ?.tabbedSearchResultsRenderer
                    ?.tabs
                    ?.firstOrNull()
                    ?.tabRenderer
                    ?.content
                    ?.sectionListRenderer
                    ?.contents
                    ?.firstNotNullOfOrNull { it.musicShelfRenderer }
                    ?: response.continuationContents?.musicShelfContinuation
                val continuations = renderer?.continuations.orEmpty()
                val items = renderer?.contents?.mapNotNull { it.musicResponsiveListItemRenderer }?.mapNotNull( ::createInnertubeItemFrom ).orEmpty()

                object : InnertubeSearch {
                    override val items: List<InnertubeItem> = items
                    override val continuations: List<Continuation> = continuations
                    override val visitorData: String? = null
                }
            }
    }

    /*

            YouTube

     */

    override fun isLoggedIn(): Boolean =
        Preferences.YOUTUBE_LOGIN.value
            && Preferences.YOUTUBE_COOKIES.value.isNotBlank()
            && Preferences.YOUTUBE_VISITOR_DATA.value.isNotBlank()
            && Preferences.YOUTUBE_SYNC_ID.value.isNotBlank()

    override suspend fun getSearchSuggestions( query: String ): Result<InnertubeSearchSuggestion> =
        runCatching {
            val suggestions = mutableListOf<InnertubeSearchSuggestion.Suggestion>()
            val items = mutableListOf<InnertubeSearchSuggestion.Item>()
            searchSuggestions( query )
                .contents
                .flatMap { it.searchSuggestionsSectionRenderer.contents }
                .forEach { content ->
                    content.searchSuggestionRenderer
                           ?.let { renderer ->
                               val suggestion = renderer.suggestion
                               val query = renderer.navigationEndpoint.searchEndpoint?.query

                               object : InnertubeSearchSuggestion.Suggestion {
                                   override val text: Runs = suggestion
                                   override val query: String = query ?: suggestion.joinToString( "" )
                               }
                           }
                           ?.also( suggestions::add )

                    content.musicResponsiveListItemRenderer
                           ?.let( ::createInnertubeSearchSuggestionItemFrom )
                           ?.also( items::add )
                }

            val immutableSuggestion = suggestions.toList()
            val immutableItems = items.toList()
            object : InnertubeSearchSuggestion {
                override val suggestions: List<InnertubeSearchSuggestion.Suggestion> = immutableSuggestion
                override val items: List<InnertubeSearchSuggestion.Item> = immutableItems
            }
        }

    /*

            Account

     */

    override suspend fun getLikedAlbums(
        params: String?
    ): Result<List<InnertubeAlbum>> = runCatching {
        checkLoginStatus()

        val response = browse(
            browseId = "FEmusic_liked_albums",
            params = params,
            continuation = null,
            setLogin = true
        )
        val tabRenderer = extractSingleColumnFirstTabRenderer( response )

        extractListContent( tabRenderer )
            .gridRenderer
            ?.items
            ?.map { it.musicTwoRowItemRenderer }
            ?.map( ::createInnertubeAlbumFrom )
            .orEmpty()
    }

    override suspend fun getLikedArtists(
        params: String?
    ): Result<List<InnertubeArtist>> = runCatching {
        checkLoginStatus()

        val artists = mutableListOf<InnertubeArtist>()

        // This CoroutineScope allows us to make 2 requests concurrently
        coroutineScope {
            // Get artists (YTM-created artists)
            val getFollowingArtists = async {
                browse( "FEmusic_library_corpus_track_artists", params, null, true )
                    .let( ::extractSingleColumnFirstTabRenderer )
                    .let( ::extractListContent )
                    .musicShelfRenderer
                    ?.contents
                    ?.mapNotNull( MusicShelfRenderer.Content::musicResponsiveListItemRenderer )
                    ?.map( ::createInnertubeArtistFrom )
                    .orEmpty()
            }
            // Get subscribed channels (artist self-made channels, can be viewed on YT)
            val getSubscribedChannels = async {
                browse( "FEmusic_library_corpus_artists", params, null, true )
                    .let( ::extractSingleColumnFirstTabRenderer )
                    .let( ::extractListContent )
                    .musicShelfRenderer
                    ?.contents
                    ?.mapNotNull( MusicShelfRenderer.Content::musicResponsiveListItemRenderer )
                    ?.map( ::createInnertubeArtistFrom )
                    .orEmpty()
            }

            val onlineArtists = getFollowingArtists.await() + getSubscribedChannels.await()
            artists.addAll( onlineArtists.distinctBy(InnertubeItem::id) )
        }

        // Must return immutable list to prevent modification
        artists.toList()
    }

    override suspend fun getLikedPlaylists(
        params: String?
    ): Result<List<InnertubePlaylist>> = runCatching {
        checkLoginStatus()

        val response = browse(
            browseId = "FEmusic_liked_playlists",
            params = params,
            continuation = null,
            setLogin = true
        )
        val tabRenderer = extractSingleColumnFirstTabRenderer( response )

        extractListContent( tabRenderer )
            .gridRenderer
            ?.items
            ?.map { it.musicTwoRowItemRenderer }
            ?.map( ::createInnertubePlaylistFrom )
            .orEmpty()
    }

    // Because account menu and AccountInfo are only created here
    // there's no need to create a serializable data class
    override suspend fun getAccountDetails(): Result<AccountInfo> = runCatching {
        checkLoginStatus()

        val actions = accountMenu()["actions"]?.jsonArray
        require( !actions.isNullOrEmpty() ) { "Account menu doesn't have any actions" }
        val account = requireNotNull(
            actions[0]
                .jsonObject["openPopupAction"]
                ?.jsonObject["popup"]
                ?.jsonObject["multiPageMenuRenderer"]
                ?.jsonObject["header"]
                ?.jsonObject["activeAccountHeaderRenderer"]
                ?.jsonObject
        ) { "Account menu doesn't have popup header" }
        val accountName: RunsImpl? = account["accountName"]?.let( json::decodeFromJsonElement )
        requireNotNull( accountName ) { "Account doesn't have a name" }
        val accountThumbnails: ThumbnailsImpl? = account["accountPhoto"]?.let( json::decodeFromJsonElement )
        requireNotNull( accountThumbnails ) { "Account doesn't have any thumbnails" }
        val accountEmail: RunsImpl? = account["email"]?.let( json::decodeFromJsonElement )
        // Channel can be null because some users don't create channel for their accounts
        val accountChannel: RunsImpl? = account["channelHandle"]?.let( json::decodeFromJsonElement )

        object : AccountInfo {
            override val name: String = accountName.firstText
            override val email: String? = accountEmail?.firstText
            override val channelHandle: String? = accountChannel?.firstText
            override val thumbnailUrl: List<Thumbnails.Thumbnail> = accountThumbnails.thumbnails
        }
    }
}