package com.metrolist.innertubex.extraction.strategy

import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.models.YouTubeClient

class ContentAwareFallbackStrategy(
    private val healthMonitor: ClientHealthMonitor = ClientHealthMonitor.NONE,
    private val catalog: PlaybackClientCatalog = PlaybackClientCatalog,
) : ClientFallbackStrategy {
    /**
     * Resolves only clients that are usable with conservative, signed-out runtime capabilities.
     * Call [selectClients] when authentication, token providers, or platform runtimes are available.
     */
    override fun resolveClients(hints: ContentHints): List<YouTubeClient> =
        selectClients(
            ClientSelectionRequest(
                hints = hints,
                authenticated = false,
                premium = hints.premium,
                javaScriptRuntimeAvailable = false,
                webViewAvailable = false,
            ),
        ).candidates.map(SelectedClient::client)

    override fun selectClients(request: ClientSelectionRequest): ClientSelectionResult = selectClients(request, premiumHighQuality = false)

    internal fun includesVisitorBackedLegacy(excludedClients: Set<String>): Boolean =
        catalog.find("VISIONOS_0_1")?.manifest?.let {
            it.selectionMode == ClientSelectionMode.PROBE_ONLY &&
                !it.isExcluded(excludedClients, emptySet(), premiumEntitlement = false)
        } == true

    internal fun selectClients(
        request: ClientSelectionRequest,
        premiumHighQuality: Boolean,
        visitorDataAvailable: Boolean = false,
    ): ClientSelectionResult {
        val rejected = mutableListOf<RejectedClient>()
        request.hints.playbackClientOverrideId?.let(catalog::find)?.let { option ->
            val rejectionReasons = hardRejectionReasons(option.manifest, request, checkContentSupport = false)
            if (
                !option.isExcluded(
                    request.excludedClients,
                    request.availablePoTokenProviders,
                    option.manifest.hasUsablePremiumEntitlement(request),
                )
            ) {
                return ClientSelectionResult(
                    candidates =
                        listOf(
                            SelectedClient(
                                client = option.client,
                                manifest = option.manifest,
                                score = Int.MAX_VALUE,
                                reasons = listOf("manual override") + rejectionReasons.map { "ignored: $it" },
                            ),
                        ),
                )
            }
            rejected +=
                RejectedClient(
                    option.manifest,
                    rejectionReasons.ifEmpty { listOf("manual override recently failed") },
                )
        }

        val candidates = mutableListOf<SelectedClient>()
        val visitorBackedLegacy =
            catalog.find("VISIONOS_0_1")?.manifest?.takeIf {
                visitorDataAvailable && it.selectionMode == ClientSelectionMode.PROBE_ONLY
            }
        (catalog.automaticManifests + listOfNotNull(visitorBackedLegacy)).forEach { manifest ->
            if (
                manifest.isExcluded(
                    request.excludedClients,
                    request.availablePoTokenProviders,
                    manifest.hasUsablePremiumEntitlement(request),
                )
            ) {
                return@forEach
            }
            val rejectionReasons = hardRejectionReasons(manifest, request)
            if (rejectionReasons.isNotEmpty()) {
                rejected += RejectedClient(manifest, rejectionReasons)
                return@forEach
            }

            val (score, reasons) = score(manifest, request)
            candidates += SelectedClient(manifest.client, manifest, score, reasons)
        }

        val orderedCandidates =
            candidates.sortedWith(
                compareBy<SelectedClient> {
                    when (request.transportPreference) {
                        PlaybackTransportPreference.AUTO -> {
                            when {
                                it.client.useSabr -> 2
                                request.hints.isKidsContent == true && it.client in kidsClients -> 0
                                else -> 1
                            }
                        }

                        PlaybackTransportPreference.SABR -> {
                            if (it.client.useSabr) 0 else 1
                        }

                        else -> {
                            0
                        }
                    }
                }.thenBy {
                    // Only Premium gains quality from cookies; cookie-less clients start video several times faster.
                    if (request.authenticated &&
                        request.premium &&
                        (premiumHighQuality || request.hints.wantVideo) &&
                        request.hints.isKidsContent != true &&
                        it.manifest?.authentication == AuthenticationPolicy.UNSUPPORTED
                    ) {
                        1
                    } else {
                        0
                    }
                }.thenByDescending { it.score }
                    .thenBy { it.manifest?.id },
            )
        return ClientSelectionResult(
            candidates = orderedCandidates,
            rejected = rejected.distinctBy { it.manifest.id }.sortedBy { it.manifest.id },
        )
    }

    private val kidsClients = setOf(YouTubeClient.ANDROID_KIDS, YouTubeClient.WEB_KIDS)

    private fun hardRejectionReasons(
        manifest: PlaybackClientManifest,
        request: ClientSelectionRequest,
        checkContentSupport: Boolean = true,
    ): List<String> =
        buildList {
            if (manifest.lifecycle == ClientLifecycle.BROKEN) {
                add("client marked broken")
            }
            if (manifest.authentication == AuthenticationPolicy.REQUIRED && !request.authenticated) {
                add("login required")
            }
            if (request.hints.isUploaded == true && !request.authenticated) {
                add("uploads require login")
            }

            val contentSupport = manifest.content.supportFor(request.hints)
            if (checkContentSupport && contentSupport == CapabilitySupport.UNSUPPORTED) {
                add("content type unsupported")
            }
            if (checkContentSupport && manifest.id == "VISIONOS_0_1" &&
                (request.hints.wantVideo || contentSupport != CapabilitySupport.SUPPORTED)
            ) {
                add("legacy direct playback verified for normal and explicit audio only")
            }
            if (request.hints.wantVideo &&
                manifest.transports.none { it == PlaybackTransport.DIRECT || it == PlaybackTransport.SABR }
            ) {
                add("transport cannot satisfy video request")
            }

            if (!request.javaScriptRuntimeAvailable && manifest.request.signatureCipher == JavaScriptRequirement.REQUIRED) {
                add("signature transform unavailable")
            }
            if (!request.javaScriptRuntimeAvailable && manifest.request.nTransform == JavaScriptRequirement.REQUIRED) {
                add("n-transform unavailable")
            }
            if (manifest.request.webView == WebViewRequirement.FULL_PLAYER && !request.webViewAvailable) {
                add("full WebView runtime unavailable")
            }

            val missingTokenRules =
                listOf("player" to manifest.poTokens.player, "GVS" to manifest.poTokens.gvs)
                    .filter { (_, rule) ->
                        rule.requirement == PoTokenRequirement.REQUIRED &&
                            !(manifest.hasUsablePremiumEntitlement(request) && rule.premiumMayBypass) &&
                            rule.providers.none { it in request.availablePoTokenProviders }
                    }.map { it.first }
            if (missingTokenRules.isNotEmpty()) {
                add("${missingTokenRules.joinToString("+")} PO-token provider unavailable")
            }

            if (request.fastPathOnly) {
                if (manifest.request.signatureTimestamp) add("watch config excluded from fast path")
                val requiresToken =
                    listOf(manifest.poTokens.player, manifest.poTokens.gvs).any {
                        it.requirement == PoTokenRequirement.REQUIRED &&
                            !(manifest.hasUsablePremiumEntitlement(request) && it.premiumMayBypass)
                    }
                if (requiresToken) add("token generation excluded from fast path")
                if (manifest.request.signatureCipher == JavaScriptRequirement.REQUIRED) {
                    add("signature transform excluded from fast path")
                }
                if (manifest.request.nTransform == JavaScriptRequirement.REQUIRED) {
                    add("n-transform excluded from fast path")
                }
            }
        }

    private fun PlaybackClientOption.isExcluded(
        excludedClients: Set<String>,
        availablePoTokenProviders: Set<PoTokenProviderKind>,
        premiumEntitlement: Boolean,
    ): Boolean = manifest.isExcluded(excludedClients, availablePoTokenProviders, premiumEntitlement)

    private fun PlaybackClientManifest.isExcluded(
        excludedClients: Set<String>,
        availablePoTokenProviders: Set<PoTokenProviderKind>,
        premiumEntitlement: Boolean,
    ): Boolean =
        client.clientName in excludedClients ||
            id in excludedClients ||
            run {
                val noPoExcluded = catalog.profileIds(this, usedPoToken = false).any { it in excludedClients }
                val poExcluded = catalog.profileIds(this, usedPoToken = true).any { it in excludedClients }
                val tokenRules = listOf(poTokens.player, poTokens.gvs)
                val canUsePoTokens =
                    tokenRules.any { rule ->
                        rule.requirement != PoTokenRequirement.NONE &&
                            rule.providers.any { it in availablePoTokenProviders }
                    }
                val requiresPoTokens =
                    tokenRules.any {
                        it.requirement == PoTokenRequirement.REQUIRED && !(premiumEntitlement && it.premiumMayBypass)
                    }
                (noPoExcluded && poExcluded) ||
                    (!canUsePoTokens && noPoExcluded) ||
                    (requiresPoTokens && poExcluded)
            }

    private fun PlaybackClientManifest.hasUsablePremiumEntitlement(request: ClientSelectionRequest): Boolean =
        request.premium && request.authenticated && client.loginSupported

    private fun score(
        manifest: PlaybackClientManifest,
        request: ClientSelectionRequest,
    ): Pair<Int, List<String>> {
        var score = manifest.priority
        val reasons = mutableListOf("base=${manifest.priority}")

        fun adjust(
            value: Int,
            reason: String,
        ) {
            score += value
            reasons += "$reason=${if (value >= 0) "+" else ""}$value"
        }

        when (manifest.content.supportFor(request.hints)) {
            CapabilitySupport.SUPPORTED -> adjust(25, "content")
            CapabilitySupport.LIMITED -> adjust(-12, "content-limited")
            CapabilitySupport.UNKNOWN -> adjust(-4, "content-unknown")
            CapabilitySupport.UNSUPPORTED -> Unit
        }
        // Prefer sustained audio playback over Web SABR responses that stall on attestation.
        // Keep the existing video ordering: these probes only decoded audio.
        if (manifest.id == "VISIONOS_SABR" && !request.hints.wantVideo) {
            adjust(80, "sabr-audio-playback")
        }

        val restrictedContent =
            request.hints.isExplicit == true ||
                request.hints.isKidsContent == true ||
                request.hints.isAgeRestricted == true ||
                request.hints.isUploaded == true
        if (restrictedContent && manifest.authentication == AuthenticationPolicy.OPTIONAL) {
            adjust(if (request.authenticated) 18 else -18, if (request.authenticated) "authenticated" else "signed-out")
        }
        if (manifest.authentication == AuthenticationPolicy.REQUIRED) adjust(8, "auth-specialist")

        when (request.transportPreference) {
            PlaybackTransportPreference.AUTO -> {
                if (PlaybackTransport.DIRECT in manifest.transports) adjust(10, "direct-fast-path")
            }

            PlaybackTransportPreference.DIRECT -> {
                adjust(if (PlaybackTransport.DIRECT in manifest.transports) 30 else -30, "direct-preference")
            }

            PlaybackTransportPreference.SABR -> {
                adjust(if (PlaybackTransport.SABR in manifest.transports) 30 else -30, "sabr-preference")
            }

            PlaybackTransportPreference.HLS -> {
                adjust(if (PlaybackTransport.HLS in manifest.transports) 30 else -30, "hls-preference")
            }
        }

        if (request.hints.wantVideo) {
            if (manifest.client.clientName == "WEB_REMIX" || manifest.client.clientName == "WEB_CREATOR") {
                adjust(-40, "web-remix-or-creator-video-demoted")
            } else if (manifest.client.clientName.startsWith("TVHTML5") ||
                manifest.client.clientName.startsWith("VISIONOS") ||
                manifest.client.clientName.startsWith("ANDROID_VR") ||
                manifest.client.clientName.startsWith("IOS")
            ) {
                adjust(35, "video-dedicated-client-preferred")
            }
        }

        when (manifest.lifecycle) {
            ClientLifecycle.STABLE -> Unit
            ClientLifecycle.CANARY -> adjust(-15, "canary")
            ClientLifecycle.EXPERIMENTAL -> adjust(-10, "experimental")
            ClientLifecycle.UNRELEASED -> adjust(-5, "unreleased")
            ClientLifecycle.DEPRECATED -> adjust(-20, "deprecated")
            ClientLifecycle.BROKEN -> adjust(-100, "broken")
        }

        val tokenRules = listOf(manifest.poTokens.player, manifest.poTokens.gvs)
        val requiredTokenCount = tokenRules.count { it.requirement == PoTokenRequirement.REQUIRED }
        val optionalTokenCount = tokenRules.count { it.requirement == PoTokenRequirement.OPTIONAL }
        if (requiredTokenCount > 0) adjust(-12 * requiredTokenCount, "required-token")
        if (optionalTokenCount > 0) adjust(-4 * optionalTokenCount, "optional-token")

        if (manifest.request.nTransform == JavaScriptRequirement.RESPONSE_DEPENDENT) adjust(-4, "possible-n-transform")
        if (manifest.request.signatureCipher == JavaScriptRequirement.RESPONSE_DEPENDENT) adjust(-3, "possible-cipher")
        when (manifest.request.webView) {
            WebViewRequirement.NONE -> Unit
            WebViewRequirement.OPTIONAL_TOKEN_MINTING -> adjust(-2, "optional-web-runtime")
            WebViewRequirement.REQUIRED_TOKEN_MINTING -> adjust(-8, "token-web-runtime")
            WebViewRequirement.FULL_PLAYER -> adjust(-25, "full-webview")
        }

        val healthAdjustment =
            healthMonitor.scoreAdjustment(
                manifest.id,
                ClientHealthScope.from(request.hints, request.authenticated),
            )
        if (healthAdjustment != 0) adjust(healthAdjustment, "runtime-health")
        return score to reasons
    }
}
