package com.metrolist.innertubex.models.response

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SearchResponse(
    val contents: Contents? = null,
    val continuationContents: ContinuationContents? = null,
) {
    @Serializable
    data class Contents(
        val tabbedSearchResultsRenderer: JsonObject? = null,
        val sectionListRenderer: JsonObject? = null,
    )

    @Serializable
    data class ContinuationContents(
        val musicShelfContinuation: JsonObject? = null,
    )
}
