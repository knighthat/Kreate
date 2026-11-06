package com.metrolist.innertubex.models.body

import com.metrolist.innertubex.models.Context
import kotlinx.serialization.Serializable

@Serializable
internal data class CreatePlaylistBody(
    val context: Context,
    val title: String,
    val privacyStatus: String,
    val videoIds: List<String>? = null,
)
