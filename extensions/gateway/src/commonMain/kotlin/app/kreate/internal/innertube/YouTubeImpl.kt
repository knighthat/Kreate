package app.kreate.internal.innertube

import app.kreate.exceptions.NotLoggedInException
import app.kreate.gateway.innertube.Account
import app.kreate.gateway.innertube.YouTube
import app.kreate.gateway.innertube.models.AccountInfo
import app.kreate.gateway.innertube.models.ContinuedPlaylist
import app.kreate.gateway.innertube.models.InnertubeAlbum
import app.kreate.gateway.innertube.models.InnertubeArtist
import app.kreate.gateway.innertube.models.InnertubeCharts
import app.kreate.gateway.innertube.models.InnertubeItem
import app.kreate.gateway.innertube.models.InnertubePlaylist
import app.kreate.gateway.innertube.models.InnertubeSearch
import app.kreate.gateway.innertube.models.InnertubeSearchSuggestion
import app.kreate.gateway.innertube.models.InnertubeSong
import app.kreate.gateway.innertube.models.InnertubeSongDetails
import app.kreate.gateway.innertube.models.Section
import app.kreate.gateway.innertube.responses.BrowseResponse
import app.kreate.gateway.innertube.responses.Continuation
import app.kreate.gateway.innertube.responses.MusicPlaylistShelfRenderer
import app.kreate.gateway.innertube.responses.MusicShelfRenderer
import app.kreate.gateway.innertube.responses.PrimaryResults
import app.kreate.gateway.innertube.responses.Runs
import app.kreate.gateway.innertube.responses.SectionListRenderer
import app.kreate.gateway.innertube.responses.Tabs
import app.kreate.gateway.innertube.responses.Thumbnails
import app.kreate.internal.innertube.models.createInnertubeAlbumFrom
import app.kreate.internal.innertube.models.createInnertubeArtistFrom
import app.kreate.internal.innertube.models.createInnertubeCharsFrom
import app.kreate.internal.innertube.models.createInnertubeItemFrom
import app.kreate.internal.innertube.models.createInnertubePlaylistFrom
import app.kreate.internal.innertube.models.createInnertubeSearchSuggestionItemFrom
import app.kreate.internal.innertube.models.createInnertubeSongFrom
import app.kreate.internal.innertube.models.createInnertubeSongsFrom
import app.kreate.internal.innertube.models.createSectionFrom
import app.kreate.internal.innertube.models.year
import app.kreate.internal.innertube.responses.RunsImpl
import app.kreate.internal.innertube.responses.ThumbnailsImpl
import app.kreate.internal.innertube.utils.containsExplicitBadge
import app.kreate.internal.innertube.utils.extractArtistAndAlbum
import app.kreate.internal.innertube.utils.firstText
import app.kreate.internal.innertube.utils.toThumbnailList
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

    override suspend fun getSongBasicInfo( songId: String ): Result<InnertubeSong> = runCatching {
        getNext( songId, null, null, null )
            .let( ::createInnertubeSongsFrom )
            .first()        // createInnertubeSongsFrom already checked whether response contain anything or not
    }

    override suspend fun getSongDetails( songId: String ): Result<InnertubeSongDetails> = runCatching {
        val content = requireNotNull(
            getYouTubeNext( songId, null )
                .contents
                .twoColumnWatchNextResults
                ?.results
                ?.results
                ?.contents
        ) { "NextResponse doesn't contain any content" }
        //<editor-fold defaultstate="collapsed" desc="Primary renderer">
        val primaryRenderer = content.firstNotNullOfOrNull( PrimaryResults.Results.Content::videoPrimaryInfoRenderer )
        requireNotNull( primaryRenderer ) { "NextResponse has no primary renderer" }
        val name = primaryRenderer.title.firstText
        val releaseDate = primaryRenderer.dateText.simpleText
        val relativeReleaseDate = primaryRenderer.relativeDateText.simpleText
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="Secondary renderer">
        val secondaryRenderer = content.firstNotNullOfOrNull( PrimaryResults.Results.Content::videoSecondaryInfoRenderer )
        requireNotNull( secondaryRenderer ) { "NextResponse has no secondary renderer" }
        val description = secondaryRenderer.attributedDescription.content
        val artist = secondaryRenderer.owner.videoOwnerRenderer.let( ::createInnertubeArtistFrom )
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="View count">
        val viewCountRenderer = primaryRenderer.viewCount.videoViewCountRenderer
        val viewCount = object : InnertubeSongDetails.ViewCount {
            override val short: String = viewCountRenderer.shortViewCount.simpleText
            override val long: String = viewCountRenderer.viewCount.simpleText
        }
        //</editor-fold>

        object : InnertubeSongDetails {
            override val title: String = name
            override val viewCount: InnertubeSongDetails.ViewCount = viewCount
            override val releaseDate: String = releaseDate
            override val relativeReleaseDate: String = relativeReleaseDate
            override val description: String = description
            override val artist: InnertubeArtist = artist
        }
    }

    override suspend fun getAlbum(
        albumId: String,
        params: String?,
        useLogin: Boolean
    ): Result<InnertubeAlbum> = runCatching {
        val response = browse( albumId, params, setLogin = useLogin )
        val urlCanonical = response.microformat?.microformatDataRenderer?.urlCanonical
        val sections = response.contents
            ?.twoColumnBrowseResultsRenderer
            ?.secondaryContents
            ?.sectionListRenderer
            ?.contents
            ?.mapNotNull { it.musicCarouselShelfRenderer }
            ?.map( ::createSectionFrom )
            .orEmpty()
        //<editor-fold desc="Renderer">
        val renderer = requireNotNull(
            response.contents
                ?.twoColumnBrowseResultsRenderer
                ?.tabs
                ?.firstNotNullOfOrNull( Tabs.Tab::tabRenderer )
                ?.content
                ?.sectionListRenderer
                ?.contents
                ?.firstNotNullOfOrNull( SectionListRenderer.Content::musicResponsiveHeaderRenderer )
        ) { "BrowseResponse doesn't contain any contents" }
        val thumbnails = renderer.thumbnail.toThumbnailList()
        val artists = renderer.straplineTextOne?.extractArtistAndAlbum()?.artists.orEmpty()
        val description = renderer.description?.musicDescriptionShelfRenderer?.description?.joinToString( "" )
        val isExplicit = renderer.subtitleBadge.containsExplicitBadge
        val name = renderer.title.firstText
        val subtitle = renderer.secondSubtitle
        val year = renderer.subtitle.year
        //</editor-fold>
        val songs = response.contents
            ?.twoColumnBrowseResultsRenderer
            ?.secondaryContents
            ?.sectionListRenderer
            ?.contents
            ?.firstOrNull()
            ?.musicShelfRenderer
            ?.contents
            ?.mapNotNull { it.musicResponsiveListItemRenderer }
            ?.map( ::createInnertubeSongFrom )
        require( !songs.isNullOrEmpty() ) { "BrowseResponse doesn't contain any songs" }

        object : InnertubeAlbum {
            override val artists: List<Runs.Run> = artists
            override val year: Int = year
            override val urlCanonical: String? = urlCanonical
            override val songs: List<InnertubeSong> = songs
            override val subtitle: Runs? = subtitle
            override val id: String = albumId
            override val name: String = name
            override val thumbnails: List<Thumbnails.Thumbnail> = thumbnails
            override val isExplicit: Boolean = isExplicit
            override val description: String? = description
            override val sections: List<Section> = sections
        }
    }

    override suspend fun getArtist(
        artistId: String,
        params: String?,
        useLogin: Boolean
    ): Result<InnertubeArtist> = runCatching {
        val response = browse( artistId, null, null, useLogin )
        //<editor-fold desc="Header">
        val header = response.header?.musicImmersiveHeaderRenderer ?: response.header?.musicVisualHeaderRenderer
        requireNotNull( header ) { "BrowseResponse doesn't contain header" }
        val name = requireNotNull(
            (response.header?.musicImmersiveHeaderRenderer?.title ?: response.header?.musicVisualHeaderRenderer?.title)?.firstText
        ) { "Header doesn't contain title" }
        val thumbnails = (response.header?.musicImmersiveHeaderRenderer?.thumbnail ?: response.header?.musicVisualHeaderRenderer?.thumbnail)?.toThumbnailList().orEmpty()
        //</editor-fold>
        val immersiveHeader = response.header?.musicImmersiveHeaderRenderer
        val shortNumMonthlyAudience = immersiveHeader?.monthlyListenerCount?.firstText
        var description = immersiveHeader?.description?.firstText
        //<editor-fold desc="Subscribe button">
        val subscribeButton = immersiveHeader?.subscriptionButton?.subscribeButtonRenderer
        val shortNumSubscribers = subscribeButton?.shortSubscriberCountText?.firstText
        val longNumSubscribers = subscribeButton?.longSubscriberCountText?.firstText
        //</editor-fold>
        val contents = requireNotNull(
            response.contents
                ?.singleColumnBrowseResultsRenderer
                ?.tabs
                ?.firstNotNullOfOrNull( Tabs.Tab::tabRenderer )
                ?.content
                ?.sectionListRenderer
                ?.contents
        ) { "BrowseResponse doesn't contain any contents" }
        val sections = ArrayList<Section>(7)
        for( content in contents ) {
            content.musicShelfRenderer
                   ?.let( ::createSectionFrom )
                   ?.also( sections::add )

            content.musicDescriptionShelfRenderer
                   ?.description
                   ?.firstText
                   ?.also {
                       if( description == null )
                           description = it
                   }

            // This section contains Albums, Single & EPs, related Artists, and Playlists.
            content.musicCarouselShelfRenderer
                   ?.let( ::createSectionFrom )
                   ?.also( sections::add )
        }

        val immutableSections = sections.toList()
        object : InnertubeArtist {
            override val shortNumSubscribers: String? = shortNumSubscribers
            override val longNumSubscribers: String? = longNumSubscribers
            override val shortNumMonthlyAudience: String? = shortNumMonthlyAudience
            override val subtitle: Runs? = null
            override val id: String = artistId
            override val name: String = name
            override val thumbnails: List<Thumbnails.Thumbnail> = thumbnails
            override val description: String? = description
            override val sections: List<Section> = immutableSections
        }
    }

    override suspend fun getPlaylist(
        playlistId: String?,
        continuation: String?,
        params: String?,
        useLogin: Boolean
    ): Result<InnertubePlaylist> = runCatching {
        // Helper function to create ContinuedPlaylist
        fun createContinuedPlaylistFrom( items: List<MusicPlaylistShelfRenderer.Content> ): ContinuedPlaylist {
            var continuation: String? = null
            val songs = ArrayList<InnertubeSong>(items.size)

            for( item in items ) {
                item.continuationItemRenderer
                    ?.continuationEndpoint
                    ?.continuationCommand
                    ?.token
                    ?.also { continuation = it }

                item.musicResponsiveListItemRenderer
                    ?.let( ::createInnertubeSongFrom )
                    ?.also( songs::add )
            }

            val immutableSongs = songs.toList()
            return object : ContinuedPlaylist {

                override val continuation: String? = continuation
                override val songs: List<InnertubeSong> = immutableSongs
            }
        }

        require( !(playlistId.isNullOrBlank() && continuation.isNullOrBlank()) ) {
            "Can't get playlist with either playlistId or continuation provided"
        }
        require( !(!playlistId.isNullOrBlank() && !continuation.isNullOrBlank()) ) {
            "Can't get playlist with both playlistId and continuation provided"
        }

        val subtitle: Runs?
        val id: String
        val name: String
        val thumbnails: List<Thumbnails.Thumbnail>
        val description: String?
        val continuedPlaylist: ContinuedPlaylist?
        val playlistContinuation: List<Continuation>

        if( playlistId != null ) {
            id = if( playlistId.startsWith("VL") ) playlistId else "VL$playlistId"
            val renderer = browse( id, params, null, useLogin ).contents?.twoColumnBrowseResultsRenderer
            val content = renderer?.tabs?.firstNotNullOfOrNull( Tabs.Tab::tabRenderer )?.content?.sectionListRenderer?.contents?.firstOrNull()
            //<editor-fold defaultstate="collapsed" desc="Header">
            val headerRenderer = requireNotNull(
                content?.musicResponsiveHeaderRenderer
                    ?: content?.musicEditablePlaylistDetailHeaderRenderer?.header?.musicResponsiveHeaderRenderer
            ) { "BrowseResponse doesn't have any headers" }
            name = headerRenderer.title.firstText
            description = headerRenderer.description?.musicDescriptionShelfRenderer?.description?.firstText
            thumbnails = headerRenderer.thumbnail.toThumbnailList()
            subtitle = headerRenderer.secondSubtitle
            //</editor-fold>
            //<editor-fold defaultstate="collapsed" desc="Contents">
            val sectionListRenderer = renderer?.secondaryContents?.sectionListRenderer
            requireNotNull( sectionListRenderer ) { "BrowseResponse doesn't have any contents" }
            continuedPlaylist = sectionListRenderer.contents.firstOrNull()?.musicPlaylistShelfRenderer?.contents?.let( ::createContinuedPlaylistFrom )
            playlistContinuation = sectionListRenderer.continuations
            //</editor-fold>
        } else {
            val response = browse( null, params, continuation, useLogin )

            subtitle = null
            id = requireNotNull(
                response.responseContext.serviceTrackingParams.firstOrNull()?.params["browse_id"]
            ) { "BrowseResponse doesn't contain browse_id in serviceTrackingParams" }
            name = ""
            thumbnails = emptyList()
            description = null
            continuedPlaylist = response.onResponseReceivedActions.firstOrNull()?.appendContinuationItemsAction?.continuationItems?.let( ::createContinuedPlaylistFrom )
            playlistContinuation = emptyList()
        }

        object : InnertubePlaylist {
            override val subtitleText: String? = subtitle?.joinToString( "" )
            override val songs: List<InnertubeSong> = continuedPlaylist?.songs.orEmpty()
            override val songContinuation: String? = continuedPlaylist?.continuation
            override val subtitle: Runs? = subtitle
            override val id: String = id
            override val name: String = name
            override val thumbnails: List<Thumbnails.Thumbnail> = thumbnails
            override val description: String? = description
            override val continuations: List<Continuation> = playlistContinuation
            override val visitorData: String? = null
        }
    }

    override suspend fun getRadio(
        videoId: String,
        playlistId: String?,
        params: String?
    ): Result<List<InnertubeSong>> = runCatching {
        getNext( videoId, playlistId, params, null ).let( ::createInnertubeSongsFrom )
    }

    override suspend fun getCharts(): Result<InnertubeCharts> = runCatching {
        val renderer = requireNotNull(
            browse( "FEmusic_charts" )
                .contents
                ?.singleColumnBrowseResultsRenderer
                ?.tabs
                ?.firstNotNullOfOrNull( Tabs.Tab::tabRenderer )
                ?.content
                ?.sectionListRenderer
        ) { "BrowseResponse doesn't contain any chart information" }

        renderer.let( ::createInnertubeCharsFrom )
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