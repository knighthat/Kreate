package app.kreate.gateway.innertube

import app.kreate.gateway.innertube.models.InnertubeAlbum
import app.kreate.gateway.innertube.models.InnertubeArtist
import app.kreate.gateway.innertube.models.InnertubeCharts
import app.kreate.gateway.innertube.models.InnertubePlaylist
import app.kreate.gateway.innertube.models.InnertubeSearch
import app.kreate.gateway.innertube.models.InnertubeSearchSuggestion
import app.kreate.gateway.innertube.models.InnertubeSong
import app.kreate.gateway.innertube.models.InnertubeSongDetails


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
        playlistId: String?,
        params: String?
    ): Result<List<InnertubeSong>>

    suspend fun getCharts(): Result<InnertubeCharts>
}