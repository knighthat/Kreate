package app.kreate.internal.innertube

import app.kreate.gateway.innertube.responses.BrowseResponse
import app.kreate.gateway.innertube.responses.SearchResponse
import app.kreate.gateway.innertube.responses.SearchSuggestionsResponse
import kotlinx.serialization.json.JsonObject


internal actual suspend fun browse(
    browseId: String,
    params: String?,
    continuation: String?,
    setLogin: Boolean
): BrowseResponse = TODO("Not yet implemented")

internal actual suspend fun accountMenu(): JsonObject = TODO("Not yet implemented")

actual suspend fun searchSuggestions(query: String): SearchSuggestionsResponse =
    TODO("Not yet implemented")

actual suspend fun searchResults( query: String?, params: String?, continuation: String? ): SearchResponse =
    TODO("Not yet implemented")