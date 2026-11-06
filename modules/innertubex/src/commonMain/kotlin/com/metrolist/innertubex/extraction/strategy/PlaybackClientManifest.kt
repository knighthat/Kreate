package com.metrolist.innertubex.extraction.strategy

import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.models.PoTokenBinding
import com.metrolist.innertubex.models.YouTubeClient

enum class ClientLifecycle {
    STABLE,
    CANARY,
    EXPERIMENTAL,
    UNRELEASED,
    DEPRECATED,
    BROKEN,
}

enum class ClientSelectionMode {
    AUTOMATIC,
    MANUAL_ONLY,
    PROBE_ONLY,
    API_ONLY,
    DISABLED,
}

enum class AuthenticationPolicy {
    UNSUPPORTED,
    OPTIONAL,
    REQUIRED,
}

enum class PlaybackTransport {
    DIRECT,
    SABR,
    HLS,
    DASH,
}

enum class CapabilitySupport {
    SUPPORTED,
    LIMITED,
    UNKNOWN,
    UNSUPPORTED,
}

enum class JavaScriptRequirement {
    NEVER,
    RESPONSE_DEPENDENT,
    REQUIRED,
}

enum class WebViewRequirement {
    NONE,
    OPTIONAL_TOKEN_MINTING,
    REQUIRED_TOKEN_MINTING,
    FULL_PLAYER,
}

enum class PoTokenRequirement {
    NONE,
    OPTIONAL,
    REQUIRED,
}

enum class PoTokenProviderKind {
    WEB_BOTGUARD,
    WEBPAGE_ATTESTATION,
    ANDROID_DROIDGUARD,
    IOS_ATTESTATION,
    EXTERNAL,
}

data class PoTokenRule(
    val requirement: PoTokenRequirement = PoTokenRequirement.NONE,
    val binding: PoTokenBinding? = null,
    val providers: Set<PoTokenProviderKind> = emptySet(),
    val premiumMayBypass: Boolean = false,
) {
    init {
        require(requirement == PoTokenRequirement.NONE || binding != null) {
            "A PO token rule must declare its content binding"
        }
        require(requirement == PoTokenRequirement.NONE || providers.isNotEmpty()) {
            "A PO token rule must declare at least one compatible provider"
        }
    }
}

data class ClientPoTokenCapabilities(
    val player: PoTokenRule = PoTokenRule(),
    val gvs: PoTokenRule = PoTokenRule(),
    val subtitles: PoTokenRule = PoTokenRule(),
)

data class ClientContentCapabilities(
    val normal: CapabilitySupport = CapabilitySupport.SUPPORTED,
    val explicit: CapabilitySupport = CapabilitySupport.UNKNOWN,
    val kids: CapabilitySupport = CapabilitySupport.UNKNOWN,
    val ageRestricted: CapabilitySupport = CapabilitySupport.UNKNOWN,
    val live: CapabilitySupport = CapabilitySupport.UNKNOWN,
    val uploads: CapabilitySupport = CapabilitySupport.UNKNOWN,
) {
    fun supportFor(hints: ContentHints): CapabilitySupport =
        when {
            hints.isUploaded == true -> uploads
            hints.isLive == true -> live
            hints.isAgeRestricted == true -> ageRestricted
            hints.isKidsContent == true -> kids
            hints.isExplicit == true -> explicit
            else -> normal
        }
}

data class ClientRequestCapabilities(
    val signatureTimestamp: Boolean = false,
    val signatureCipher: JavaScriptRequirement = JavaScriptRequirement.NEVER,
    val nTransform: JavaScriptRequirement = JavaScriptRequirement.NEVER,
    val webView: WebViewRequirement = WebViewRequirement.NONE,
    val cookies: Boolean = false,
    val embedded: Boolean = false,
    val encryptedHostFlags: Boolean = false,
)

data class PlaybackClientManifest(
    val id: String,
    val displayName: String,
    val client: YouTubeClient,
    val lifecycle: ClientLifecycle,
    val selectionMode: ClientSelectionMode,
    val authentication: AuthenticationPolicy,
    val transports: Set<PlaybackTransport>,
    val poTokens: ClientPoTokenCapabilities = ClientPoTokenCapabilities(),
    val request: ClientRequestCapabilities = ClientRequestCapabilities(),
    val content: ClientContentCapabilities = ClientContentCapabilities(),
    val benchmarkContent: ClientContentCapabilities = content,
    val priority: Int,
    val evidence: Set<String> = emptySet(),
    val notes: String? = null,
) {
    init {
        require(id.matches(Regex("[A-Z0-9_]+"))) { "Client manifest ID must be stable and uppercase: $id" }
        require(transports.isNotEmpty() || selectionMode == ClientSelectionMode.API_ONLY) {
            "Playback client $id must declare at least one transport"
        }
        require((PlaybackTransport.SABR in transports) == client.useSabr) {
            "Client $id disagrees with its SABR request profile"
        }
        require((authentication != AuthenticationPolicy.UNSUPPORTED) == client.loginSupported) {
            "Client $id disagrees with its login request profile"
        }
        require((authentication == AuthenticationPolicy.REQUIRED) == client.loginRequired) {
            "Client $id disagrees with its required-login request profile"
        }
        require(request.signatureTimestamp == client.useSignatureTimestamp) {
            "Client $id disagrees with its signature timestamp request profile"
        }
        require(request.embedded == client.isEmbedded) {
            "Client $id disagrees with its embedded request profile"
        }
        require(!request.encryptedHostFlags || request.embedded) {
            "Client $id requests encrypted host flags without an embedded player context"
        }
    }

    val isAutomatic: Boolean
        get() = selectionMode == ClientSelectionMode.AUTOMATIC

    val isPlaybackClient: Boolean
        get() = selectionMode != ClientSelectionMode.API_ONLY

    fun supportsProvider(kinds: Set<PoTokenProviderKind>): Boolean {
        val requiredRules = listOf(poTokens.player, poTokens.gvs).filter { it.requirement == PoTokenRequirement.REQUIRED }
        return requiredRules.all { rule -> rule.providers.any { it in kinds } }
    }
}
