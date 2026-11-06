package com.metrolist.innertubex.models.response

import kotlinx.serialization.Serializable

@Serializable
internal data class ImageUploadResponse(
    val encryptedBlobId: String,
)
