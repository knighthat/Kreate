package com.metrolist.innertubex.models.body

import com.metrolist.innertubex.models.Context
import kotlinx.serialization.Serializable

@Serializable
internal data class SubscribeBody(
    val context: Context,
    val channelIds: List<String>,
    val params: String? = null,
)
