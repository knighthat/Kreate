package com.metrolist.innertubex.models.response

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class GetQueueResponse(
    val queueDatas: List<QueueData>? = null,
) {
    @Serializable
    data class QueueData(
        val content: JsonObject? = null,
    )
}
