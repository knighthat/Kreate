package app.kreate.gateway.innertube

import app.kreate.gateway.innertube.models.InnertubeAlbum
import app.kreate.gateway.innertube.models.InnertubeArtist
import app.kreate.gateway.innertube.models.InnertubeCharts
import app.kreate.gateway.innertube.models.InnertubeExplore
import app.kreate.gateway.innertube.models.InnertubePlaylist
import app.kreate.gateway.innertube.models.InnertubeSearch
import app.kreate.gateway.innertube.models.InnertubeSearchSuggestion
import app.kreate.gateway.innertube.models.InnertubeSong
import app.kreate.gateway.innertube.models.InnertubeSongDetails
import app.kreate.gateway.innertube.models.MultiContent
import app.kreate.gateway.innertube.models.Section


interface YouTube {

    val account: Account

    fun isLoggedIn(): Boolean

    suspend fun getSearchSuggestions( query: String ): Result<InnertubeSearchSuggestion>

    suspend fun getSearchResults(
        query: String?,
        continuation: String?,
        @SearchFilter params: String? = null
    ): Result<InnertubeSearch>

    suspend fun getSongBasicInfo( songId: String ): Result<InnertubeSong>

    suspend fun getSongDetails( songId: String ): Result<InnertubeSongDetails>

    suspend fun getAlbum( albumId: String, params: String?, useLogin: Boolean = false ): Result<InnertubeAlbum>

    suspend fun getArtist( artistId: String, params: String?, useLogin: Boolean = false ): Result<InnertubeArtist>

    suspend fun getPlaylist(
        playlistId: String?,
        continuation: String?,
        params: String? = null,
        useLogin: Boolean = false
    ): Result<InnertubePlaylist>

    suspend fun getRadio(
        videoId: String,
        playlistId: String? = "RDAMVM$videoId",
        params: String? = null
    ): Result<List<InnertubeSong>>

    suspend fun getCharts(): Result<InnertubeCharts>

    suspend fun getRelated( videoId: String ): Result<MultiContent>

    suspend fun explore(): Result<InnertubeExplore>

    suspend fun getSeeMorePage( browseId: String, params: String? = null ): Result<List<Section>>

    suspend fun reverseAlbumIdFrom( playlistId: String ): Result<String>

    suspend fun getLyrics( videoId: String ): Result<String>
}