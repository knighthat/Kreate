package com.metrolist.innertubex.models.body

import com.metrolist.innertubex.models.Context
import kotlinx.serialization.Serializable

@Serializable
internal data class PlaylistDeleteBody(
    val context: Context,
    val playlistId: String,
)
