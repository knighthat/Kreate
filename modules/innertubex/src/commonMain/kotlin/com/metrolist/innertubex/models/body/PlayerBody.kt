package com.metrolist.innertubex.models.body

import com.metrolist.innertubex.models.Context
import kotlinx.serialization.Serializable

@Serializable
internal data class PlayerBody(
    val context: Context,
    val videoId: String,
    val playlistId: String? = null,
    val playbackContext: PlaybackContext? = null,
    val thirdParty: ThirdParty? = null,
    val serviceIntegrityDimensions: ServiceIntegrityDimensions? = null,
    val contentCheckOk: Boolean,
    val racyCheckOk: Boolean,
    val videoCheckOk: Boolean? = null,
) {
    @Serializable
    data class PlaybackContext(
        val contentPlaybackContext: ContentPlaybackContext,
    ) {
        @Serializable
        data class ContentPlaybackContext(
            val html5Preference: String? = null,
            val signatureTimestamp: Int? = null,
            val encryptedHostFlags: String? = null,
        )
    }

    @Serializable
    data class ServiceIntegrityDimensions(
        val poToken: String,
    )

    @Serializable
    data class ThirdParty(
        val embedUrl: String,
    )
}
