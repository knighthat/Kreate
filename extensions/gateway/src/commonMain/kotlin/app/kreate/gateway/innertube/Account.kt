package app.kreate.gateway.innertube

import app.kreate.gateway.innertube.models.AccountInfo
import app.kreate.gateway.innertube.models.InnertubeAlbum
import app.kreate.gateway.innertube.models.InnertubeArtist
import app.kreate.gateway.innertube.models.InnertubeHomePage
import app.kreate.gateway.innertube.models.InnertubePlaylist


interface Account {

    suspend fun getLikedAlbums( params: String? = null ): Result<List<InnertubeAlbum>>

    suspend fun getLikedArtists( params: String? = null ): Result<List<InnertubeArtist>>

    suspend fun getLikedPlaylists( params: String? = null ): Result<List<InnertubePlaylist>>

    suspend fun getAccountDetails(): Result<AccountInfo>

    suspend fun getHomePage(): Result<InnertubeHomePage>
}