package com.metrolist.innertubex.models.response

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

@Serializable
data class BrowseResponse(
    val contents: Contents? = null,
    val continuationContents: ContinuationContents? = null,
    val header: Header? = null,
    val microformat: JsonObject? = null,
    val onResponseReceivedActions: JsonArray? = null,
    val onResponseReceivedCommands: JsonArray? = null,
    val onResponseReceivedEndpoints: JsonArray? = null,
    val background: JsonObject? = null,
) {
    @Serializable
    data class Contents(
        val singleColumnBrowseResultsRenderer: JsonObject? = null,
        val sectionListRenderer: JsonObject? = null,
        val twoColumnBrowseResultsRenderer: JsonObject? = null,
    )

    @Serializable
    data class ContinuationContents(
        val sectionListContinuation: JsonObject? = null,
        val musicPlaylistShelfContinuation: JsonObject? = null,
        val gridContinuation: JsonObject? = null,
        val musicShelfContinuation: JsonObject? = null,
    )

    @Serializable
    data class Header(
        val musicImmersiveHeaderRenderer: JsonObject? = null,
        val musicDetailHeaderRenderer: JsonObject? = null,
        val musicEditablePlaylistDetailHeaderRenderer: JsonObject? = null,
        val musicResponsiveHeaderRenderer: JsonObject? = null,
        val musicVisualHeaderRenderer: JsonObject? = null,
        val musicHeaderRenderer: JsonObject? = null,
    )
}
