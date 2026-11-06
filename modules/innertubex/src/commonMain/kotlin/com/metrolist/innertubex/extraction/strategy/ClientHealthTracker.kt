package com.metrolist.innertubex.extraction.strategy

import com.metrolist.innertubex.extraction.ContentHints
import kotlinx.serialization.Serializable

@Serializable
enum class ClientFailureKind {
    PLAYER_REQUEST,
    PLAYABILITY,
    TOKEN,
    MEDIA_FORBIDDEN,
    MEDIA_FAILURE,
}

@Serializable
enum class ClientHealthContent {
    NORMAL,
    EXPLICIT,
    KIDS,
    AGE_RESTRICTED,
    LIVE,
    UPLOAD,
}

@Serializable
data class ClientHealthScope(
    val content: ClientHealthContent,
    val authenticated: Boolean,
    val wantVideo: Boolean,
) {
    companion object {
        fun from(
            hints: ContentHints,
            authenticated: Boolean,
        ): ClientHealthScope =
            ClientHealthScope(
                content =
                    when {
                        hints.isUploaded == true -> ClientHealthContent.UPLOAD
                        hints.isLive == true -> ClientHealthContent.LIVE
                        hints.isAgeRestricted == true -> ClientHealthContent.AGE_RESTRICTED
                        hints.isKidsContent == true -> ClientHealthContent.KIDS
                        hints.isExplicit == true -> ClientHealthContent.EXPLICIT
                        else -> ClientHealthContent.NORMAL
                    },
                authenticated = authenticated,
                wantVideo = hints.wantVideo,
            )
    }
}

interface ClientHealthMonitor {
    fun scoreAdjustment(
        clientId: String,
        scope: ClientHealthScope? = null,
    ): Int = 0

    fun recordSuccess(
        clientId: String,
        scope: ClientHealthScope? = null,
    ) = Unit

    fun recordFailure(
        clientId: String,
        kind: ClientFailureKind,
        scope: ClientHealthScope? = null,
    ) = Unit

    companion object {
        val NONE: ClientHealthMonitor = object : ClientHealthMonitor {}
    }
}
