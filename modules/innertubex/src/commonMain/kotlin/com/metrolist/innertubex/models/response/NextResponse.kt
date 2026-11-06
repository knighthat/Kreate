package com.metrolist.innertubex.models.response

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class NextResponse(
    val contents: Contents? = null,
    val continuationContents: ContinuationContents? = null,
) {
    @Serializable
    data class Contents(
        val singleColumnMusicWatchNextResultsRenderer: JsonObject? = null,
        val twoColumnWatchNextResults: JsonObject? = null,
    )

    @Serializable
    data class ContinuationContents(
        val playlistPanelContinuation: JsonObject? = null,
    )
}
