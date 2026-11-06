package com.metrolist.innertubex.models.body

import com.metrolist.innertubex.models.Context
import kotlinx.serialization.Serializable

@Serializable
internal data class SearchBody(
    val context: Context,
    val query: String? = null,
    val params: String? = null,
)
