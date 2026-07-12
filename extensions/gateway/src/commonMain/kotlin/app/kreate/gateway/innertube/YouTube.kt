package app.kreate.gateway.innertube

import app.kreate.gateway.innertube.models.InnertubeSearch
import app.kreate.gateway.innertube.models.InnertubeSearchSuggestion


interface YouTube {

    val account: Account

    fun isLoggedIn(): Boolean

    suspend fun getSearchSuggestions( query: String ): Result<InnertubeSearchSuggestion>

    suspend fun getSearchResults(
        query: String?,
        continuation: String?,
        @SearchFilter params: String? = null
    ): Result<InnertubeSearch>
}