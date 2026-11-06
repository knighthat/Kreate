package com.metrolist.innertubex.models

import kotlinx.serialization.Serializable

@Serializable
data class AccountInfo(
    val name: String,
    val email: String?,
    val channelHandle: String?,
    val thumbnailUrl: String?,
)
