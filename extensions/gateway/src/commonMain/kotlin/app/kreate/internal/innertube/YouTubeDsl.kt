package app.kreate.internal.innertube

import app.kreate.gateway.innertube.responses.BrowseResponse
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