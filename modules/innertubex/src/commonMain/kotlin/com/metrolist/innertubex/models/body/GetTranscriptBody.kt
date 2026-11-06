package com.metrolist.innertubex.models.body

import com.metrolist.innertubex.models.Context
import kotlinx.serialization.Serializable

@Serializable
internal data class GetTranscriptBody(
    val context: Context,
    val params: String,
)
