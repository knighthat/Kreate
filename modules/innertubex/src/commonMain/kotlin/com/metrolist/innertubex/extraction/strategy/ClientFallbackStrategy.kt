package com.metrolist.innertubex.extraction.strategy

import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.models.YouTubeClient

enum class PlaybackTransportPreference {
    AUTO,
    DIRECT,
    SABR,
    HLS,
}

data class ClientSelectionRequest(
    val hints: ContentHints,
    val authenticated: Boolean,
    val premium: Boolean = false,
    val availablePoTokenProviders: Set<PoTokenProviderKind> = emptySet(),
    val javaScriptRuntimeAvailable: Boolean = true,
    val webViewAvailable: Boolean = false,
    val fastPathOnly: Boolean = false,
    val transportPreference: PlaybackTransportPreference = PlaybackTransportPreference.AUTO,
    val excludedClients: Set<String> = emptySet(),
)

data class SelectedClient(
    val client: YouTubeClient,
    val manifest: PlaybackClientManifest? = null,
    val score: Int = 0,
    val reasons: List<String> = emptyList(),
)

data class RejectedClient(
    val manifest: PlaybackClientManifest,
    val reasons: List<String>,
)

data class ClientSelectionResult(
    val candidates: List<SelectedClient>,
    val rejected: List<RejectedClient> = emptyList(),
)

interface ClientFallbackStrategy {
    fun resolveClients(hints: ContentHints): List<YouTubeClient>

    fun selectClients(request: ClientSelectionRequest): ClientSelectionResult =
        ClientSelectionResult(resolveClients(request.hints).map(::SelectedClient))
}
