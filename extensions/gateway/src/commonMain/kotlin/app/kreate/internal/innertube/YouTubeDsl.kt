package app.kreate.internal.innertube

import app.kreate.gateway.innertube.responses.BrowseResponse
import app.kreate.gateway.innertube.responses.NextResponse
import app.kreate.gateway.innertube.responses.SearchResponse
import app.kreate.gateway.innertube.responses.SearchSuggestionsResponse
import kotlinx.serialization.json.JsonObject


internal expect suspend fun browse(
    browseId: String,
    params: String? = null,
    continuation: String? = null,
    setLogin: Boolean = false
): BrowseResponse

internal expect suspend fun accountMenu(): JsonObject

internal expect suspend fun searchSuggestions( query: String ): SearchSuggestionsResponse

internal expect suspend fun searchResults( query: String?, params: String?, continuation: String? ): SearchResponse

internal expect suspend fun getNext(
    videoId: String?,
    playlistId: String?,
    params: String?,
    continuation: String?
): NextResponse

internal expect suspend fun getYouTubeNext( videoId: String?, params: String? ): NextResponse