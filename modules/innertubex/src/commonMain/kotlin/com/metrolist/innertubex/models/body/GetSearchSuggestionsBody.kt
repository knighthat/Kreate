package com.metrolist.innertubex.models.body

import com.metrolist.innertubex.models.Context
import kotlinx.serialization.Serializable

@Serializable
internal data class GetSearchSuggestionsBody(
    val context: Context,
    val input: String,
)
