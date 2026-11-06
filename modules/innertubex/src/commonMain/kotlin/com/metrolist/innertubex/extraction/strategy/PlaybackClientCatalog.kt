package com.metrolist.innertubex.extraction.strategy

import com.metrolist.innertubex.models.PoTokenBinding
import com.metrolist.innertubex.models.YouTubeClient

data class PlaybackClientOption(
    val id: String,
    val displayName: String,
    val client: YouTubeClient,
    val manifest: PlaybackClientManifest,
)

object PlaybackClientCatalog {
    const val AUTOMATIC_ID = "AUTO"
    const val SABR_FIRST_ID = "SABR_FIRST"

    private const val SOURCE_PLAYBACK_MATRIX = "Playback live matrix"
    private const val SOURCE_ANDROID_BENCHMARK = "Android playback benchmark (2026-08-13)"
    private const val SOURCE_ANDROID_SABR_BENCHMARK = "Android SABR benchmark (2026-08-20)"
    private const val SOURCE_DEVICE_CLIENT_BENCHMARK = "Android device client benchmark (2026-10-14)"
    private const val SOURCE_YTDLP = "yt-dlp client inventory"
    private const val SOURCE_YOUTUBE_JS = "YouTube.js client constants"
    private const val SOURCE_HISTORICAL_CLIENTS = "YouTube-Internal-Clients survey (2022)"
    private const val SOURCE_PYTUBEFIX = "pytubefix innertube.py (b6521f09)"

    private val webProviders =
        setOf(PoTokenProviderKind.WEB_BOTGUARD, PoTokenProviderKind.WEBPAGE_ATTESTATION, PoTokenProviderKind.EXTERNAL)
    private val webSabrProviders = setOf(PoTokenProviderKind.WEBPAGE_ATTESTATION, PoTokenProviderKind.EXTERNAL)
    private val androidProviders = setOf(PoTokenProviderKind.ANDROID_DROIDGUARD, PoTokenProviderKind.EXTERNAL)
    private val iosProviders = setOf(PoTokenProviderKind.IOS_ATTESTATION, PoTokenProviderKind.EXTERNAL)

    // Current Web enforcement is GVS-only. A video-bound GVS token must not be reused by the player request.
    private val webPlayerNotRequired = PoTokenRule()
    private val webPlayerOptional =
        PoTokenRule(PoTokenRequirement.OPTIONAL, PoTokenBinding.VIDEO_ID, webProviders)
    private val webSessionPlayerRequired =
        PoTokenRule(PoTokenRequirement.REQUIRED, PoTokenBinding.VISITOR_DATA, webProviders)
    private val webGvsRequired =
        PoTokenRule(
            PoTokenRequirement.REQUIRED,
            PoTokenBinding.VIDEO_ID,
            webProviders,
            premiumMayBypass = true,
        )
    private val webGvsOptional =
        PoTokenRule(PoTokenRequirement.OPTIONAL, PoTokenBinding.VIDEO_ID, webProviders)
    private val webSabrSessionPlayerRequired =
        PoTokenRule(PoTokenRequirement.REQUIRED, PoTokenBinding.VISITOR_DATA, webSabrProviders)
    private val pageBoundGvsRequired =
        PoTokenRule(PoTokenRequirement.REQUIRED, PoTokenBinding.VIDEO_ID, webSabrProviders)
    private val webSabrGvsOptional =
        PoTokenRule(PoTokenRequirement.OPTIONAL, PoTokenBinding.VIDEO_ID, webSabrProviders)
    private val androidPlayerOptional =
        PoTokenRule(PoTokenRequirement.OPTIONAL, PoTokenBinding.VISITOR_DATA, androidProviders)
    private val androidGvsOptional =
        PoTokenRule(PoTokenRequirement.OPTIONAL, PoTokenBinding.VIDEO_ID, androidProviders)
    private val androidPlayerRequired =
        PoTokenRule(PoTokenRequirement.REQUIRED, PoTokenBinding.VISITOR_DATA, androidProviders)
    private val androidGvsRequired =
        PoTokenRule(PoTokenRequirement.REQUIRED, PoTokenBinding.VIDEO_ID, androidProviders)
    private val iosPlayerOptional =
        PoTokenRule(PoTokenRequirement.OPTIONAL, PoTokenBinding.VISITOR_DATA, iosProviders)
    private val iosGvsRequired =
        PoTokenRule(PoTokenRequirement.REQUIRED, PoTokenBinding.VIDEO_ID, iosProviders)

    private val webRequest =
        ClientRequestCapabilities(
            signatureTimestamp = true,
            signatureCipher = JavaScriptRequirement.RESPONSE_DEPENDENT,
            nTransform = JavaScriptRequirement.RESPONSE_DEPENDENT,
            webView = WebViewRequirement.OPTIONAL_TOKEN_MINTING,
            cookies = true,
        )

    private val normalWebContent =
        ClientContentCapabilities(
            explicit = CapabilitySupport.SUPPORTED,
            kids = CapabilitySupport.SUPPORTED,
            ageRestricted = CapabilitySupport.LIMITED,
            live = CapabilitySupport.SUPPORTED,
            uploads = CapabilitySupport.SUPPORTED,
        )
    private val validatedRestrictedWebContent =
        normalWebContent.copy(ageRestricted = CapabilitySupport.SUPPORTED)
    private val validatedProbeWebContent =
        ClientContentCapabilities(
            explicit = CapabilitySupport.SUPPORTED,
            kids = CapabilitySupport.SUPPORTED,
            ageRestricted = CapabilitySupport.SUPPORTED,
            uploads = CapabilitySupport.SUPPORTED,
        )

    val manifests: List<PlaybackClientManifest> =
        listOf(
            manifest(
                id = "VISIONOS",
                client = YouTubeClient.VISIONOS,
                displayName = "visionOS 1.02",
                lifecycle = ClientLifecycle.UNRELEASED,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 100,
                transports = setOf(PlaybackTransport.DIRECT),
                content =
                    ClientContentCapabilities(
                        explicit = CapabilitySupport.LIMITED,
                        kids = CapabilitySupport.UNSUPPORTED,
                        ageRestricted = CapabilitySupport.UNSUPPORTED,
                        live = CapabilitySupport.UNKNOWN,
                        uploads = CapabilitySupport.UNSUPPORTED,
                    ),
                evidence = setOf(SOURCE_ANDROID_BENCHMARK, SOURCE_PLAYBACK_MATRIX, SOURCE_YTDLP),
                notes =
                    "Passed normal playback with backward and forward seeks on an authenticated device, but clean " +
                        "Android sessions can stall before receiving media. Internal/unreleased; made-for-kids and " +
                        "age-restricted media were rejected.",
            ),
            manifest(
                id = "VISIONOS_0_1",
                client = YouTubeClient.VISIONOS_0_1,
                displayName = "visionOS 0.1",
                lifecycle = ClientLifecycle.DEPRECATED,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 100,
                transports = setOf(PlaybackTransport.DIRECT),
                content = normalSongsOnlyContent().copy(explicit = CapabilitySupport.SUPPORTED),
                evidence = setOf(SOURCE_ANDROID_BENCHMARK, SOURCE_PLAYBACK_MATRIX, SOURCE_DEVICE_CLIENT_BENCHMARK),
                notes =
                    "September 2026 capture: two anonymous normal audio requests without visitorData were " +
                        "UNPLAYABLE; the same requests with watch-config visitorData returned direct audio " +
                        "(one complete file, one initial range). October 2026 device benchmark: fastest client for " +
                        "normal and non-age-gated explicit audio, signed in and out, with 90-second playback and " +
                        "bidirectional seeks; age-gated and account-restricted explicit media fail in under a second. " +
                        "Public selection stays probe-only without visitor context; internal automatic extraction " +
                        "tries visitor-backed audio only. Earlier HTTP 400 reports and other content remain unverified.",
            ),
            manifest(
                id = "ANDROID_VR_1_65_10",
                client = YouTubeClient.ANDROID_VR_1_65_10,
                displayName = "Android VR 1.65.10",
                lifecycle = ClientLifecycle.STABLE,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 40,
                transports = setOf(PlaybackTransport.DIRECT),
                content = vrContent(),
                benchmarkContent = globallyUnusableContent(),
                evidence = setOf(SOURCE_ANDROID_BENCHMARK, SOURCE_PLAYBACK_MATRIX, SOURCE_YTDLP, SOURCE_YOUTUBE_JS),
                notes =
                    "A live anonymous visitor request resolved, but every audio itag reached a CDN 403 after " +
                        "1 MiB. Kept for explicit benchmark probing only.",
            ),
            manifest(
                id = "ANDROID_VR_1_43_32",
                client = YouTubeClient.ANDROID_VR_1_43_32,
                displayName = "Android VR 1.43.32",
                lifecycle = ClientLifecycle.DEPRECATED,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 82,
                transports = setOf(PlaybackTransport.DIRECT),
                content = vrContent(),
                benchmarkContent = globallyUnusableContent(),
                evidence = setOf(SOURCE_ANDROID_BENCHMARK, SOURCE_PLAYBACK_MATRIX),
                notes =
                    "Player requests resolve, but sustained media reaches the same later-range authorization " +
                        "boundary as other Android VR profiles. Retained for benchmark comparison only.",
            ),
            manifest(
                id = "ANDROID_VR_NO_AUTH",
                client = YouTubeClient.ANDROID_VR_NO_AUTH,
                displayName = "Android VR 1.61.48 (minimal)",
                lifecycle = ClientLifecycle.DEPRECATED,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 60,
                transports = setOf(PlaybackTransport.DIRECT),
                content = restrictedFallbackVrContent(),
                benchmarkContent = globallyUnusableContent(),
                evidence = setOf(SOURCE_ANDROID_BENCHMARK, SOURCE_PLAYBACK_MATRIX),
                notes =
                    "Player requests resolve, but sustained media reaches a later-range authorization boundary. " +
                        "Retained for benchmark comparison only.",
            ),
            manifest(
                id = "ANDROID_VR_1_61_48",
                client = YouTubeClient.ANDROID_VR_1_61_48,
                displayName = "Android VR 1.61.48",
                lifecycle = ClientLifecycle.DEPRECATED,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 64,
                transports = setOf(PlaybackTransport.DIRECT),
                content = restrictedFallbackVrContent(),
                benchmarkContent = globallyUnusableContent(),
                evidence = setOf(SOURCE_ANDROID_BENCHMARK, SOURCE_PLAYBACK_MATRIX),
                notes =
                    "Player requests resolve, but sustained media reaches a later-range authorization boundary. " +
                        "Retained for benchmark comparison only.",
            ),
            /*
             * Disabled after the 2026-08-13 Android playback benchmark passed 0/3 cases.
             *
            manifest(
                id = "TVHTML5",
                client = YouTubeClient.TVHTML5,
                displayName = "TV HTML5",
                lifecycle = ClientLifecycle.STABLE,
                selectionMode = ClientSelectionMode.AUTOMATIC,
                priority = 75,
                authentication = AuthenticationPolicy.UNSUPPORTED,
                transports = setOf(PlaybackTransport.DIRECT, PlaybackTransport.HLS),
                poTokens = ClientPoTokenCapabilities(player = webPlayerNotRequired, gvs = webGvsOptional),
                request = webRequest,
                content = normalWebContent,
                evidence = setOf(SOURCE_ANDROID_BENCHMARK, SOURCE_PLAYBACK_MATRIX, SOURCE_YTDLP, SOURCE_YOUTUBE_JS),
                notes = "Player responses contained no direct or HLS audio usable by this profile.",
            ),
             */
            manifest(
                id = "TVHTML5_DOWNGRADED",
                client = YouTubeClient.TVHTML5_DOWNGRADED,
                displayName = "TV HTML5 Downgraded",
                lifecycle = ClientLifecycle.EXPERIMENTAL,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 70,
                authentication = AuthenticationPolicy.OPTIONAL,
                transports = setOf(PlaybackTransport.DIRECT, PlaybackTransport.HLS),
                request =
                    ClientRequestCapabilities(
                        signatureTimestamp = true,
                        cookies = true,
                        nTransform = JavaScriptRequirement.RESPONSE_DEPENDENT,
                    ),
                content =
                    ClientContentCapabilities(
                        explicit = CapabilitySupport.UNKNOWN,
                        kids = CapabilitySupport.SUPPORTED,
                        ageRestricted = CapabilitySupport.SUPPORTED,
                    ),
                benchmarkContent = globallyUnusableContent(),
                evidence = setOf(SOURCE_ANDROID_BENCHMARK, SOURCE_PLAYBACK_MATRIX, SOURCE_YTDLP),
                notes =
                    "Matches the current yt-dlp profile but YouTube returned UNPLAYABLE in August 2026 probes. " +
                        "Retained for explicit compatibility testing only.",
            ),
            manifest(
                id = "WEB_REMIX",
                client = YouTubeClient.WEB_REMIX,
                displayName = "Web Remix",
                lifecycle = ClientLifecycle.STABLE,
                selectionMode = ClientSelectionMode.AUTOMATIC,
                priority = 70,
                authentication = AuthenticationPolicy.OPTIONAL,
                transports = setOf(PlaybackTransport.DIRECT, PlaybackTransport.HLS),
                poTokens = ClientPoTokenCapabilities(player = webPlayerNotRequired, gvs = webGvsRequired),
                request = webRequest,
                content = validatedRestrictedWebContent,
                evidence = setOf(SOURCE_ANDROID_BENCHMARK, SOURCE_PLAYBACK_MATRIX, SOURCE_YTDLP, SOURCE_YOUTUBE_JS),
                notes = "Passed normal, made-for-kids, and age-restricted playback with backward and forward seeks.",
            ),
            manifest(
                id = "WEB_CREATOR",
                client = YouTubeClient.WEB_CREATOR,
                displayName = "Web Creator",
                lifecycle = ClientLifecycle.STABLE,
                selectionMode = ClientSelectionMode.AUTOMATIC,
                priority = 55,
                authentication = AuthenticationPolicy.REQUIRED,
                transports = setOf(PlaybackTransport.DIRECT, PlaybackTransport.HLS),
                poTokens = ClientPoTokenCapabilities(player = webPlayerNotRequired, gvs = webGvsRequired),
                request = webRequest,
                content = validatedRestrictedWebContent,
                evidence = setOf(SOURCE_ANDROID_BENCHMARK, SOURCE_PLAYBACK_MATRIX, SOURCE_YTDLP, SOURCE_YOUTUBE_JS),
                notes =
                    "Passed normal, made-for-kids, and age-restricted playback with backward and forward seeks. " +
                        "Requires a signed-in account.",
            ),
            manifest(
                id = "TVHTML5_SIMPLY",
                client = YouTubeClient.TVHTML5_SIMPLY,
                displayName = "TV HTML5 Simply",
                lifecycle = ClientLifecycle.EXPERIMENTAL,
                selectionMode = ClientSelectionMode.AUTOMATIC,
                priority = 42,
                transports = setOf(PlaybackTransport.DIRECT, PlaybackTransport.HLS),
                poTokens = ClientPoTokenCapabilities(player = webSessionPlayerRequired, gvs = webGvsRequired),
                request =
                    webRequest.copy(
                        webView = WebViewRequirement.REQUIRED_TOKEN_MINTING,
                        cookies = false,
                    ),
                content =
                    ClientContentCapabilities(
                        explicit = CapabilitySupport.UNSUPPORTED,
                        kids = CapabilitySupport.SUPPORTED,
                        ageRestricted = CapabilitySupport.UNSUPPORTED,
                        live = CapabilitySupport.UNKNOWN,
                        uploads = CapabilitySupport.UNSUPPORTED,
                    ),
                benchmarkContent =
                    ClientContentCapabilities(
                        explicit = CapabilitySupport.LIMITED,
                        kids = CapabilitySupport.SUPPORTED,
                    ),
                evidence = setOf(SOURCE_ANDROID_BENCHMARK, SOURCE_PLAYBACK_MATRIX, SOURCE_YTDLP, SOURCE_YOUTUBE_JS),
                notes =
                    "The two-token HLS path passed both normal samples, both made-for-kids " +
                        "samples, and one explicit sample with sustained playback and bidirectional seeks.",
            ),
            /*
             * Disabled after the 2026-08-13 Android playback benchmark passed 0/3 cases.
             *
            manifest(
                id = "WEB_SAFARI",
                client = YouTubeClient.WEB_SAFARI,
                displayName = "Web Safari",
                lifecycle = ClientLifecycle.EXPERIMENTAL,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 48,
                authentication = AuthenticationPolicy.OPTIONAL,
                transports = setOf(PlaybackTransport.DIRECT, PlaybackTransport.HLS),
                poTokens = ClientPoTokenCapabilities(player = webPlayerNotRequired, gvs = webGvsRequired),
                request = webRequest,
                content = globallyUnusableContent(),
                evidence = setOf(SOURCE_PLAYBACK_MATRIX, SOURCE_YTDLP),
                notes =
                    "Current player responses may contain only SABR-capable data and no direct URL or HLS stream. " +
                        "Use WEB_SAFARI_SABR for the validated transport path.",
            ),
             */
            manifest(
                id = "WEB_EMBEDDED_PLAYER",
                client = YouTubeClient.WEB_EMBEDDED_PLAYER,
                displayName = "Web Embedded",
                lifecycle = ClientLifecycle.STABLE,
                selectionMode = ClientSelectionMode.AUTOMATIC,
                priority = 65,
                authentication = AuthenticationPolicy.OPTIONAL,
                transports = setOf(PlaybackTransport.DIRECT, PlaybackTransport.HLS),
                request =
                    webRequest.copy(
                        embedded = true,
                        encryptedHostFlags = true,
                        webView = WebViewRequirement.NONE,
                    ),
                content =
                    ClientContentCapabilities(
                        normal = CapabilitySupport.SUPPORTED,
                        explicit = CapabilitySupport.UNKNOWN,
                        kids = CapabilitySupport.SUPPORTED,
                        ageRestricted = CapabilitySupport.UNSUPPORTED,
                        live = CapabilitySupport.UNSUPPORTED,
                        uploads = CapabilitySupport.UNSUPPORTED,
                    ),
                benchmarkContent =
                    ClientContentCapabilities(
                        explicit = CapabilitySupport.UNSUPPORTED,
                        kids = CapabilitySupport.SUPPORTED,
                    ),
                evidence = setOf(SOURCE_ANDROID_BENCHMARK, SOURCE_PLAYBACK_MATRIX, SOURCE_YTDLP, SOURCE_YOUTUBE_JS),
                notes =
                    "Normal and made-for-kids playback passed sustained playback and both seek directions after " +
                        "fetching per-video encryptedHostFlags; age-restricted media was rejected.",
            ),
            /*
             * Disabled after the normal case timed out and the other two cases failed to resolve.
             *
            manifest(
                id = "VISIONOS_SABR",
                client = YouTubeClient.VISIONOS_SABR,
                displayName = "visionOS 1.02 (SABR)",
                lifecycle = ClientLifecycle.UNRELEASED,
                selectionMode = ClientSelectionMode.AUTOMATIC,
                priority = 78,
                transports = setOf(PlaybackTransport.SABR),
                content = normalSongsOnlyContent(),
                evidence = setOf(SOURCE_ANDROID_BENCHMARK, SOURCE_PLAYBACK_MATRIX, SOURCE_YTDLP),
                notes = "Normal playback never became ready; the other benchmark content was rejected.",
            ),
             */
            manifest(
                id = "ANDROID_VR_SABR",
                client = YouTubeClient.ANDROID_VR_SABR,
                displayName = "Android VR 1.65.10 (SABR)",
                lifecycle = ClientLifecycle.EXPERIMENTAL,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 68,
                transports = setOf(PlaybackTransport.SABR),
                poTokens = ClientPoTokenCapabilities(player = androidPlayerOptional, gvs = androidGvsRequired),
                content = normalSongsOnlyContent(),
                benchmarkContent = globallyUnusableContent(),
                evidence = setOf(SOURCE_ANDROID_BENCHMARK, SOURCE_ANDROID_SABR_BENCHMARK, SOURCE_PLAYBACK_MATRIX, SOURCE_YTDLP),
                notes =
                    "Passed 0 of 6 current benchmark cases. Normal responses reached media but did not advance; " +
                        "explicit and made-for-kids samples were rejected or failed media without DroidGuard.",
            ),
            /*
             * Disabled after all three cases hit SABR attestation errors and fell back to another profile.
             *
            manifest(
                id = "TVHTML5_SABR",
                client = YouTubeClient.TVHTML5_SABR,
                displayName = "TV HTML5 (SABR)",
                lifecycle = ClientLifecycle.EXPERIMENTAL,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 58,
                authentication = AuthenticationPolicy.OPTIONAL,
                transports = setOf(PlaybackTransport.SABR),
                poTokens = ClientPoTokenCapabilities(player = webPlayerNotRequired, gvs = webGvsOptional),
                request = webRequest,
                content = normalWebContent,
                evidence = setOf(SOURCE_PLAYBACK_MATRIX, SOURCE_YTDLP),
                notes = "Probe-only until full playback, protection, and seek behavior are benchmarked.",
            ),
             */
            manifest(
                id = "WEB_REMIX_SABR",
                client = YouTubeClient.WEB_REMIX_SABR,
                displayName = "Web Remix (SABR)",
                lifecycle = ClientLifecycle.EXPERIMENTAL,
                selectionMode = ClientSelectionMode.AUTOMATIC,
                priority = 52,
                authentication = AuthenticationPolicy.OPTIONAL,
                transports = setOf(PlaybackTransport.SABR),
                poTokens = ClientPoTokenCapabilities(player = webPlayerNotRequired, gvs = webSabrGvsOptional),
                request = webRequest,
                content =
                    ClientContentCapabilities(
                        explicit = CapabilitySupport.UNKNOWN,
                        kids = CapabilitySupport.SUPPORTED,
                        ageRestricted = CapabilitySupport.SUPPORTED,
                        uploads = CapabilitySupport.SUPPORTED,
                    ),
                benchmarkContent =
                    ClientContentCapabilities(
                        explicit = CapabilitySupport.SUPPORTED,
                        kids = CapabilitySupport.SUPPORTED,
                    ),
                evidence = setOf(SOURCE_ANDROID_BENCHMARK, SOURCE_ANDROID_SABR_BENCHMARK, SOURCE_PLAYBACK_MATRIX),
                notes =
                    "Passed all 6 current normal, explicit, and made-for-kids cases with 90-second playback, " +
                        "bidirectional seeks, and complete telemetry without a PO token.",
            ),
            manifest(
                id = "WEB_SABR",
                client = YouTubeClient.WEB_SABR,
                displayName = "Web (SABR)",
                lifecycle = ClientLifecycle.EXPERIMENTAL,
                selectionMode = ClientSelectionMode.AUTOMATIC,
                priority = 44,
                authentication = AuthenticationPolicy.OPTIONAL,
                transports = setOf(PlaybackTransport.SABR),
                poTokens = ClientPoTokenCapabilities(player = webPlayerNotRequired, gvs = pageBoundGvsRequired),
                request = webRequest.copy(webView = WebViewRequirement.REQUIRED_TOKEN_MINTING),
                content = validatedProbeWebContent,
                evidence = setOf(SOURCE_ANDROID_BENCHMARK, SOURCE_ANDROID_SABR_BENCHMARK, SOURCE_PLAYBACK_MATRIX, SOURCE_YTDLP),
                notes =
                    "Page-bound GVS tokens passed all 6 current normal, explicit, and made-for-kids cases with " +
                        "90-second playback, bidirectional seeks, and complete telemetry.",
            ),
            manifest(
                id = "TVHTML5_SIMPLY_SABR",
                client = YouTubeClient.TVHTML5_SIMPLY_SABR,
                displayName = "TV HTML5 Simply (SABR)",
                lifecycle = ClientLifecycle.EXPERIMENTAL,
                selectionMode = ClientSelectionMode.AUTOMATIC,
                priority = 36,
                transports = setOf(PlaybackTransport.SABR),
                poTokens = ClientPoTokenCapabilities(player = webSabrSessionPlayerRequired, gvs = pageBoundGvsRequired),
                request = webRequest.copy(webView = WebViewRequirement.REQUIRED_TOKEN_MINTING, cookies = false),
                content =
                    ClientContentCapabilities(
                        explicit = CapabilitySupport.UNSUPPORTED,
                        kids = CapabilitySupport.SUPPORTED,
                        ageRestricted = CapabilitySupport.UNSUPPORTED,
                        uploads = CapabilitySupport.UNSUPPORTED,
                    ),
                benchmarkContent =
                    ClientContentCapabilities(
                        explicit = CapabilitySupport.LIMITED,
                        kids = CapabilitySupport.SUPPORTED,
                        ageRestricted = CapabilitySupport.UNSUPPORTED,
                        uploads = CapabilitySupport.UNSUPPORTED,
                    ),
                evidence = setOf(SOURCE_ANDROID_BENCHMARK, SOURCE_ANDROID_SABR_BENCHMARK, SOURCE_PLAYBACK_MATRIX),
                notes =
                    "Passed both normal and both made-for-kids samples with 90-second playback, bidirectional " +
                        "seeks, and complete telemetry. One of two explicit samples passed; explicit content " +
                        "remains excluded from automatic selection.",
            ),
            /*
             * Disabled after the 2026-08-13 Android playback benchmark passed 0/3 cases.
             *
            manifest(
                id = "ANDROID",
                client = YouTubeClient.ANDROID,
                displayName = "Android 21.26.364",
                lifecycle = ClientLifecycle.BROKEN,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 40,
                transports = setOf(PlaybackTransport.DIRECT, PlaybackTransport.HLS),
                poTokens = ClientPoTokenCapabilities(player = androidPlayerOptional, gvs = androidGvsRequired),
                content = nativeDirectUnusableContent(),
                evidence = setOf(SOURCE_ANDROID_BENCHMARK, SOURCE_PLAYBACK_MATRIX, SOURCE_YTDLP, SOURCE_YOUTUBE_JS),
                notes =
                    "Normal and made-for-kids requests return SABR-only metadata without direct URLs, while " +
                        "age-restricted media requires login. This direct profile is not currently usable.",
            ),
             *
             * Disabled after SABR attestation errors prevented both resolvable cases from staying on this profile.
             *
            manifest(
                id = "ANDROID_SABR",
                client = YouTubeClient.ANDROID_SABR,
                displayName = "Android 21.26.364 (SABR)",
                lifecycle = ClientLifecycle.CANARY,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 30,
                transports = setOf(PlaybackTransport.SABR),
                poTokens = ClientPoTokenCapabilities(player = androidPlayerOptional, gvs = androidGvsRequired),
                content = ClientContentCapabilities(explicit = CapabilitySupport.UNKNOWN, kids = CapabilitySupport.UNKNOWN),
                evidence = setOf(SOURCE_ANDROID_BENCHMARK, SOURCE_PLAYBACK_MATRIX, SOURCE_YTDLP),
                notes = "Not automatic until a DroidGuard-compatible provider is available.",
            ),
             */
            manifest(
                id = "MWEB",
                client = YouTubeClient.MWEB,
                displayName = "Mobile Web",
                lifecycle = ClientLifecycle.EXPERIMENTAL,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 38,
                authentication = AuthenticationPolicy.OPTIONAL,
                transports = setOf(PlaybackTransport.DIRECT, PlaybackTransport.HLS),
                poTokens = ClientPoTokenCapabilities(player = webPlayerNotRequired, gvs = pageBoundGvsRequired),
                request = webRequest,
                content = validatedProbeWebContent,
                evidence = setOf(SOURCE_ANDROID_BENCHMARK, SOURCE_PLAYBACK_MATRIX, SOURCE_YTDLP, SOURCE_YOUTUBE_JS),
                notes =
                    "Page-bound GVS tokens passed normal, made-for-kids, and age-restricted playback. " +
                        "Fresh tokenized URLs can return several transient HTTP 403 responses before becoming usable.",
            ),
            manifest(
                id = "MWEB_SABR",
                client = YouTubeClient.MWEB_SABR,
                displayName = "Mobile Web (SABR)",
                lifecycle = ClientLifecycle.EXPERIMENTAL,
                selectionMode = ClientSelectionMode.AUTOMATIC,
                priority = 32,
                authentication = AuthenticationPolicy.OPTIONAL,
                transports = setOf(PlaybackTransport.SABR),
                poTokens = ClientPoTokenCapabilities(player = webPlayerNotRequired, gvs = pageBoundGvsRequired),
                request = webRequest.copy(webView = WebViewRequirement.REQUIRED_TOKEN_MINTING),
                content = validatedProbeWebContent,
                benchmarkContent = validatedProbeWebContent.copy(explicit = CapabilitySupport.LIMITED),
                evidence = setOf(SOURCE_ANDROID_BENCHMARK, SOURCE_ANDROID_SABR_BENCHMARK, SOURCE_PLAYBACK_MATRIX, SOURCE_YTDLP),
                notes =
                    "Passed 5 of 6 current cases with 90-second playback and bidirectional seeks. One explicit " +
                        "sample had a media failure; runtime health fallback remains enabled.",
            ),
            manifest(
                id = "WEB_SAFARI_SABR",
                client = YouTubeClient.WEB_SAFARI_SABR,
                displayName = "Web Safari (SABR)",
                lifecycle = ClientLifecycle.EXPERIMENTAL,
                selectionMode = ClientSelectionMode.AUTOMATIC,
                priority = 28,
                authentication = AuthenticationPolicy.OPTIONAL,
                transports = setOf(PlaybackTransport.SABR),
                poTokens = ClientPoTokenCapabilities(player = webPlayerNotRequired, gvs = pageBoundGvsRequired),
                request = webRequest.copy(webView = WebViewRequirement.REQUIRED_TOKEN_MINTING),
                content = validatedProbeWebContent,
                evidence = setOf(SOURCE_ANDROID_BENCHMARK, SOURCE_ANDROID_SABR_BENCHMARK, SOURCE_PLAYBACK_MATRIX, SOURCE_YTDLP),
                notes =
                    "Page-bound GVS tokens passed all 6 current normal, explicit, and made-for-kids cases with " +
                        "90-second playback, bidirectional seeks, and complete telemetry.",
            ),
            /*
             * Disabled after the 2026-08-13 Android playback benchmark passed 0/3 cases.
             *
            manifest(
                id = "IOS",
                client = YouTubeClient.IOS,
                displayName = "iOS 21.26.4",
                lifecycle = ClientLifecycle.BROKEN,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 35,
                transports = setOf(PlaybackTransport.DIRECT, PlaybackTransport.HLS),
                poTokens = ClientPoTokenCapabilities(player = iosPlayerOptional, gvs = iosGvsRequired),
                content = nativeDirectUnusableContent(),
                evidence = setOf(SOURCE_PLAYBACK_MATRIX, SOURCE_YTDLP, SOURCE_YOUTUBE_JS),
                notes =
                    "Normal and made-for-kids requests return SABR-only metadata without direct URLs. " +
                        "Age-restricted media requires login; no iOS attestation provider is available.",
            ),
             *
             * Disabled after both resolvable cases raised reload-player errors and switched to another profile.
             *
            manifest(
                id = "IOS_SABR",
                client = YouTubeClient.IOS_SABR,
                displayName = "iOS 21.26.4 (SABR)",
                lifecycle = ClientLifecycle.CANARY,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 25,
                transports = setOf(PlaybackTransport.SABR),
                poTokens = ClientPoTokenCapabilities(player = iosPlayerOptional, gvs = iosGvsRequired),
                content = ClientContentCapabilities(),
                evidence = setOf(SOURCE_YTDLP),
            ),
             *
             * Disabled after the 2026-08-13 Android playback benchmark passed 0/3 cases.
             *
            manifest(
                id = "IPADOS",
                client = YouTubeClient.IPADOS,
                displayName = "iPadOS 17.7.10",
                lifecycle = ClientLifecycle.BROKEN,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 0,
                transports = setOf(PlaybackTransport.DIRECT, PlaybackTransport.HLS),
                poTokens = ClientPoTokenCapabilities(player = iosPlayerOptional, gvs = iosGvsRequired),
                content = nativeDirectUnusableContent(),
                evidence = setOf(SOURCE_PLAYBACK_MATRIX),
                notes =
                    "Current normal and made-for-kids responses are SABR-only without direct URLs; age-restricted " +
                        "media requires login. Historical direct probes failed sequential GVS ranges around 1 MiB.",
            ),
             *
             * Disabled after YouTube returned an unsupported-application error for all three cases.
             *
            manifest(
                id = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                client = YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
                displayName = "TV HTML5 Simply Embedded",
                lifecycle = ClientLifecycle.BROKEN,
                selectionMode = ClientSelectionMode.MANUAL_ONLY,
                priority = 15,
                transports = setOf(PlaybackTransport.DIRECT),
                poTokens = ClientPoTokenCapabilities(player = webSessionPlayerRequired, gvs = webGvsRequired),
                request =
                    webRequest.copy(
                        webView = WebViewRequirement.REQUIRED_TOKEN_MINTING,
                        cookies = false,
                        embedded = true,
                    ),
                content = globallyUnusableContent(),
                evidence = setOf(SOURCE_PLAYBACK_MATRIX, SOURCE_YOUTUBE_JS),
                notes = "YouTube reports this application and device as no longer supported.",
            ),
             */
            manifest(
                id = "WEB_KIDS",
                client = YouTubeClient.WEB_KIDS,
                displayName = "Web Kids",
                lifecycle = ClientLifecycle.EXPERIMENTAL,
                selectionMode = ClientSelectionMode.AUTOMATIC,
                priority = 55,
                transports = setOf(PlaybackTransport.DIRECT),
                request = webRequest.copy(cookies = false),
                content =
                    ClientContentCapabilities(
                        normal = CapabilitySupport.UNSUPPORTED,
                        explicit = CapabilitySupport.UNSUPPORTED,
                        kids = CapabilitySupport.SUPPORTED,
                        ageRestricted = CapabilitySupport.UNSUPPORTED,
                        live = CapabilitySupport.UNSUPPORTED,
                        uploads = CapabilitySupport.UNSUPPORTED,
                    ),
                evidence = setOf(SOURCE_ANDROID_BENCHMARK, SOURCE_YOUTUBE_JS),
                notes =
                    "Made-for-kids playback passed 70 seconds plus backward and forward seeks; normal and " +
                        "age-restricted playback are unavailable.",
            ),
            manifest(
                id = "TVHTML5",
                client = YouTubeClient.TVHTML5,
                displayName = "TV HTML5",
                lifecycle = ClientLifecycle.BROKEN,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 0,
                authentication = AuthenticationPolicy.OPTIONAL,
                transports = setOf(PlaybackTransport.DIRECT, PlaybackTransport.HLS),
                poTokens = ClientPoTokenCapabilities(player = webPlayerNotRequired, gvs = webGvsOptional),
                request = webRequest,
                content = normalWebContent,
                evidence = setOf(SOURCE_ANDROID_BENCHMARK, SOURCE_YTDLP),
                notes = "Previous probes returned no direct or HLS audio; retained for benchmark regression checks.",
            ),
            manifest(
                id = "WEB_SAFARI",
                client = YouTubeClient.WEB_SAFARI,
                displayName = "Web Safari",
                lifecycle = ClientLifecycle.BROKEN,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 0,
                authentication = AuthenticationPolicy.OPTIONAL,
                transports = setOf(PlaybackTransport.DIRECT, PlaybackTransport.HLS),
                poTokens = ClientPoTokenCapabilities(player = webPlayerNotRequired, gvs = webGvsRequired),
                request = webRequest,
                content = globallyUnusableContent(),
                evidence = setOf(SOURCE_ANDROID_BENCHMARK, SOURCE_YTDLP),
                notes = "Previous responses exposed SABR-only media; retained to detect direct-stream regressions.",
            ),
            manifest(
                id = "VISIONOS_SABR",
                client = YouTubeClient.VISIONOS_SABR,
                displayName = "visionOS 1.02 (SABR)",
                lifecycle = ClientLifecycle.UNRELEASED,
                selectionMode = ClientSelectionMode.AUTOMATIC,
                priority = 0,
                transports = setOf(PlaybackTransport.SABR),
                content = normalSongsOnlyContent().copy(explicit = CapabilitySupport.SUPPORTED),
                evidence =
                    setOf(
                        SOURCE_ANDROID_BENCHMARK,
                        SOURCE_ANDROID_SABR_BENCHMARK,
                        SOURCE_PLAYBACK_MATRIX,
                        SOURCE_YTDLP,
                        SOURCE_DEVICE_CLIENT_BENCHMARK,
                    ),
                notes =
                    "Passed both normal samples and one non-age-gated explicit audio sample with 90-second " +
                        "playback and bidirectional seeks. October 2026 device benchmark: fastest SABR client for " +
                        "audio and for 1080p normal and 2160p explicit video, signed in and out. Kids, age-gated, " +
                        "and account-restricted media were rejected quickly.",
            ),
            manifest(
                id = "TVHTML5_SABR",
                client = YouTubeClient.TVHTML5_SABR,
                displayName = "TV HTML5 (SABR)",
                lifecycle = ClientLifecycle.BROKEN,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 0,
                authentication = AuthenticationPolicy.OPTIONAL,
                transports = setOf(PlaybackTransport.SABR),
                poTokens = ClientPoTokenCapabilities(player = webPlayerNotRequired, gvs = webGvsOptional),
                request = webRequest,
                content = normalWebContent,
                evidence = setOf(SOURCE_ANDROID_BENCHMARK, SOURCE_ANDROID_SABR_BENCHMARK, SOURCE_YTDLP),
                notes = "Passed 0 of 6 current cases; every player request was rejected. Retained for regression checks.",
            ),
            manifest(
                id = "ANDROID",
                client = YouTubeClient.ANDROID,
                displayName = "Android 21.26.364",
                lifecycle = ClientLifecycle.BROKEN,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 0,
                transports = setOf(PlaybackTransport.DIRECT, PlaybackTransport.HLS),
                poTokens = ClientPoTokenCapabilities(player = androidPlayerOptional, gvs = androidGvsRequired),
                content = nativeDirectUnusableContent(),
                evidence = setOf(SOURCE_ANDROID_BENCHMARK, SOURCE_YTDLP, SOURCE_YOUTUBE_JS),
                notes = "Previous responses exposed SABR-only media and require unavailable DroidGuard attestation.",
            ),
            manifest(
                id = "ANDROID_SABR",
                client = YouTubeClient.ANDROID_SABR,
                displayName = "Android 21.26.364 (SABR)",
                lifecycle = ClientLifecycle.BROKEN,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 0,
                transports = setOf(PlaybackTransport.SABR),
                poTokens = ClientPoTokenCapabilities(player = androidPlayerOptional, gvs = androidGvsRequired),
                content = ClientContentCapabilities(explicit = CapabilitySupport.UNKNOWN, kids = CapabilitySupport.UNKNOWN),
                benchmarkContent = globallyUnusableContent().copy(normal = CapabilitySupport.LIMITED),
                evidence = setOf(SOURCE_ANDROID_BENCHMARK, SOURCE_ANDROID_SABR_BENCHMARK, SOURCE_YTDLP),
                notes =
                    "Passed 1 of 2 normal cases but failed the other 5 current cases. No DroidGuard-compatible " +
                        "token provider is available, so this remains probe-only.",
            ),
            manifest(
                id = "IOS",
                client = YouTubeClient.IOS,
                displayName = "iOS 21.26.4",
                lifecycle = ClientLifecycle.BROKEN,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 0,
                transports = setOf(PlaybackTransport.DIRECT, PlaybackTransport.HLS),
                poTokens = ClientPoTokenCapabilities(player = iosPlayerOptional, gvs = iosGvsRequired),
                content = nativeDirectUnusableContent(),
                evidence = setOf(SOURCE_YTDLP, SOURCE_YOUTUBE_JS),
                notes = "Previous responses exposed SABR-only media and require unavailable iOS attestation.",
            ),
            manifest(
                id = "IOS_SABR",
                client = YouTubeClient.IOS_SABR,
                displayName = "iOS 21.26.4 (SABR)",
                lifecycle = ClientLifecycle.BROKEN,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 0,
                transports = setOf(PlaybackTransport.SABR),
                poTokens = ClientPoTokenCapabilities(player = iosPlayerOptional, gvs = iosGvsRequired),
                content = ClientContentCapabilities(),
                evidence = setOf(SOURCE_ANDROID_SABR_BENCHMARK, SOURCE_YTDLP),
                notes = "Passed 0 of 6 current cases. No iOS-compatible token provider is available.",
            ),
            manifest(
                id = "IPADOS",
                client = YouTubeClient.IPADOS,
                displayName = "iPadOS 17.7.10",
                lifecycle = ClientLifecycle.BROKEN,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 0,
                transports = setOf(PlaybackTransport.DIRECT, PlaybackTransport.HLS),
                poTokens = ClientPoTokenCapabilities(player = iosPlayerOptional, gvs = iosGvsRequired),
                content = nativeDirectUnusableContent(),
                evidence = setOf(SOURCE_PLAYBACK_MATRIX),
                notes = "Previous responses exposed SABR-only media and require unavailable iOS attestation.",
            ),
            manifest(
                id = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                client = YouTubeClient.TVHTML5_SIMPLY_EMBEDDED_PLAYER,
                displayName = "TV HTML5 Simply Embedded",
                lifecycle = ClientLifecycle.BROKEN,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 0,
                transports = setOf(PlaybackTransport.DIRECT),
                poTokens = ClientPoTokenCapabilities(player = webSessionPlayerRequired, gvs = webGvsRequired),
                request = webRequest.copy(webView = WebViewRequirement.REQUIRED_TOKEN_MINTING, cookies = false, embedded = true),
                content = globallyUnusableContent(),
                evidence = setOf(SOURCE_YOUTUBE_JS),
                notes = "YouTube previously reported this application and device as unsupported.",
            ),
            manifest(
                id = "ANDROID_MUSIC",
                client = YouTubeClient.ANDROID_MUSIC,
                displayName = "Android Music 5.34.51",
                lifecycle = ClientLifecycle.BROKEN,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 0,
                transports = setOf(PlaybackTransport.DIRECT),
                poTokens = ClientPoTokenCapabilities(player = androidPlayerRequired, gvs = androidGvsRequired),
                content = globallyUnusableContent(),
                evidence = setOf(SOURCE_YOUTUBE_JS),
                notes = "Previous player requests returned HTTP 400 FAILED_PRECONDITION.",
            ),
            manifest(
                id = "ANDROID_CREATOR",
                client = YouTubeClient.ANDROID_CREATOR,
                displayName = "Android Studio 25.03.101",
                lifecycle = ClientLifecycle.BROKEN,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 0,
                authentication = AuthenticationPolicy.REQUIRED,
                transports = setOf(PlaybackTransport.DIRECT),
                content = globallyUnusableContent(),
                evidence = setOf(SOURCE_YOUTUBE_JS),
                notes = "Previous player requests returned HTTP 400 INVALID_ARGUMENT.",
            ),
            /*
             * Disabled after every player request returned HTTP 400 FAILED_PRECONDITION.
             *
            manifest(
                id = "ANDROID_MUSIC",
                client = YouTubeClient.ANDROID_MUSIC,
                displayName = "Android Music 5.34.51",
                lifecycle = ClientLifecycle.BROKEN,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 5,
                transports = setOf(PlaybackTransport.DIRECT),
                poTokens = ClientPoTokenCapabilities(player = androidPlayerRequired, gvs = androidGvsRequired),
                content = globallyUnusableContent(),
                evidence = setOf(SOURCE_PLAYBACK_MATRIX, SOURCE_YOUTUBE_JS),
                notes = "Current player requests return HTTP 400 FAILED_PRECONDITION for every benchmark probe.",
            ),
             *
             * Disabled after every player request returned HTTP 400 INVALID_ARGUMENT.
             *
            manifest(
                id = "ANDROID_CREATOR",
                client = YouTubeClient.ANDROID_CREATOR,
                displayName = "Android Studio 25.03.101",
                lifecycle = ClientLifecycle.BROKEN,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 0,
                authentication = AuthenticationPolicy.REQUIRED,
                transports = setOf(PlaybackTransport.DIRECT),
                content = globallyUnusableContent(),
                evidence = setOf(SOURCE_PLAYBACK_MATRIX, SOURCE_YOUTUBE_JS),
                notes = "Both 25.03.101 and the YouTube.js 22.43.101 identity return HTTP 400.",
            ),
             */
            manifest(
                id = "IOS_MUSIC",
                client = YouTubeClient.IOS_MUSIC,
                displayName = "iOS Music probe",
                lifecycle = ClientLifecycle.EXPERIMENTAL,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 0,
                transports = setOf(PlaybackTransport.DIRECT),
                content = unverifiedProbeContent(),
                evidence = setOf(SOURCE_PYTUBEFIX, SOURCE_HISTORICAL_CLIENTS),
                notes = "Historical iOS Music identity and app fields; playback and transport support are unverified.",
            ),
            manifest(
                id = "ANDROID_KIDS",
                client = YouTubeClient.ANDROID_KIDS,
                displayName = "Android Kids",
                lifecycle = ClientLifecycle.EXPERIMENTAL,
                selectionMode = ClientSelectionMode.AUTOMATIC,
                priority = 60,
                transports = setOf(PlaybackTransport.DIRECT),
                content =
                    ClientContentCapabilities(
                        normal = CapabilitySupport.UNSUPPORTED,
                        explicit = CapabilitySupport.UNSUPPORTED,
                        kids = CapabilitySupport.SUPPORTED,
                        ageRestricted = CapabilitySupport.UNSUPPORTED,
                        live = CapabilitySupport.UNSUPPORTED,
                        uploads = CapabilitySupport.UNSUPPORTED,
                    ),
                evidence = setOf(SOURCE_PYTUBEFIX, SOURCE_HISTORICAL_CLIENTS, SOURCE_DEVICE_CLIENT_BENCHMARK),
                notes =
                    "Fastest made-for-kids client in the October 2026 device benchmark, signed in and out, with " +
                        "90-second playback and bidirectional seeks and no token or cipher work; normal and explicit " +
                        "media are rejected.",
            ),
            manifest(
                id = "ANDROID_PRODUCER",
                client = YouTubeClient.ANDROID_PRODUCER,
                displayName = "Android Producer probe",
                lifecycle = ClientLifecycle.EXPERIMENTAL,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 0,
                transports = setOf(PlaybackTransport.DIRECT),
                content = unverifiedProbeContent(),
                evidence = setOf(SOURCE_PYTUBEFIX),
                notes = "Android Producer identity and app fields from the pinned source; playback is unverified.",
            ),
            manifest(
                id = "MEDIA_CONNECT_FRONTEND",
                client = YouTubeClient.MEDIA_CONNECT_FRONTEND,
                displayName = "Media Connect Frontend probe",
                lifecycle = ClientLifecycle.EXPERIMENTAL,
                selectionMode = ClientSelectionMode.PROBE_ONLY,
                priority = 0,
                transports = setOf(PlaybackTransport.DIRECT),
                content = unverifiedProbeContent(),
                evidence = setOf(SOURCE_PYTUBEFIX, SOURCE_HISTORICAL_CLIENTS),
                notes = "Historical Media Connect identity and app fields; playback and transport support are unverified.",
            ),
            manifest(
                id = "WEB",
                client = YouTubeClient.WEB,
                displayName = "Web API",
                lifecycle = ClientLifecycle.STABLE,
                selectionMode = ClientSelectionMode.API_ONLY,
                priority = 0,
                authentication = AuthenticationPolicy.OPTIONAL,
                transports = emptySet(),
                poTokens = ClientPoTokenCapabilities(player = webPlayerNotRequired, gvs = webGvsRequired),
                request = webRequest,
                content = normalWebContent,
                evidence = setOf(SOURCE_YTDLP, SOURCE_YOUTUBE_JS),
                notes = "Used for metadata/transcript calls. Playback uses the explicit WEB_SABR profile.",
            ),
        ).also(::validateInventory)

    val options: List<PlaybackClientOption> =
        manifests
            .filter { it.selectionMode !in setOf(ClientSelectionMode.DISABLED, ClientSelectionMode.API_ONLY) }
            .map { PlaybackClientOption(it.id, it.displayName, it.client, it) }

    val benchmarkOptions: List<PlaybackClientOption> =
        manifests
            .filter { it.selectionMode != ClientSelectionMode.API_ONLY }
            .map { PlaybackClientOption(it.id, it.displayName, it.client, it) }

    val devtoolsOverrideOptions: List<PlaybackClientOption> =
        options.filter { it.manifest.selectionMode != ClientSelectionMode.PROBE_ONLY }

    val automaticManifests: List<PlaybackClientManifest> = manifests.filter(PlaybackClientManifest::isAutomatic)

    fun find(id: String): PlaybackClientOption? = options.firstOrNull { it.id == id }

    fun findBenchmark(id: String): PlaybackClientOption? = benchmarkOptions.firstOrNull { it.id == id }

    fun findDevtoolsOverride(id: String): PlaybackClientOption? = devtoolsOverrideOptions.firstOrNull { it.id == id }

    fun isDevtoolsOverrideAllowed(id: String): Boolean = findDevtoolsOverride(id) != null

    fun isDevtoolsSelectionAllowed(id: String): Boolean = id == AUTOMATIC_ID || id == SABR_FIRST_ID || isDevtoolsOverrideAllowed(id)

    fun findManifest(id: String): PlaybackClientManifest? = manifests.firstOrNull { it.id == id }

    fun findManifest(client: YouTubeClient): PlaybackClientManifest? = manifests.firstOrNull { it.client == client }

    fun manifestIdFromProfileId(profileId: String?): String? =
        profileId
            ?.substringBefore("__")
            ?.takeIf { candidate -> manifests.any { it.id == candidate } }

    fun profileIds(
        manifest: PlaybackClientManifest,
        usedPoToken: Boolean,
    ): Set<String> =
        setOf(
            "${manifest.id}__${if (usedPoToken) "po" else "nopo"}",
            manifest.client.legacyProfileId(usedPoToken),
        )

    private fun YouTubeClient.legacyProfileId(usedPoToken: Boolean): String {
        val base =
            buildString {
                append(clientName)
                append('_')
                append(friendlyName ?: clientVersion)
                if (isEmbedded) append("_embedded")
            }
        return "${base.replace(Regex("[^A-Za-z0-9_.-]"), "_")}_${if (usedPoToken) "po" else "nopo"}"
    }

    private fun manifest(
        id: String,
        client: YouTubeClient,
        displayName: String,
        lifecycle: ClientLifecycle,
        selectionMode: ClientSelectionMode,
        authentication: AuthenticationPolicy = AuthenticationPolicy.UNSUPPORTED,
        transports: Set<PlaybackTransport>,
        poTokens: ClientPoTokenCapabilities = ClientPoTokenCapabilities(),
        request: ClientRequestCapabilities =
            ClientRequestCapabilities(
                signatureTimestamp = client.useSignatureTimestamp,
                embedded = client.isEmbedded,
            ),
        content: ClientContentCapabilities,
        benchmarkContent: ClientContentCapabilities? = null,
        priority: Int,
        evidence: Set<String>,
        notes: String? = null,
    ) = PlaybackClientManifest(
        id = id,
        displayName = displayName,
        client = client,
        lifecycle = lifecycle,
        selectionMode = selectionMode,
        authentication = authentication,
        transports = transports,
        poTokens = poTokens,
        request = request,
        content = content,
        benchmarkContent = benchmarkContent ?: if (lifecycle == ClientLifecycle.BROKEN) globallyUnusableContent() else content,
        priority = priority,
        evidence = evidence,
        notes = notes,
    )

    private fun restrictedFallbackVrContent() =
        ClientContentCapabilities(
            explicit = CapabilitySupport.UNKNOWN,
            kids = CapabilitySupport.UNSUPPORTED,
            ageRestricted = CapabilitySupport.UNKNOWN,
            live = CapabilitySupport.LIMITED,
            uploads = CapabilitySupport.UNKNOWN,
        )

    private fun vrContent() =
        ClientContentCapabilities(
            explicit = CapabilitySupport.UNSUPPORTED,
            kids = CapabilitySupport.UNSUPPORTED,
            ageRestricted = CapabilitySupport.UNSUPPORTED,
            live = CapabilitySupport.LIMITED,
            uploads = CapabilitySupport.UNSUPPORTED,
        )

    private fun normalSongsOnlyContent() =
        ClientContentCapabilities(
            explicit = CapabilitySupport.UNSUPPORTED,
            kids = CapabilitySupport.UNSUPPORTED,
            ageRestricted = CapabilitySupport.UNSUPPORTED,
            live = CapabilitySupport.UNSUPPORTED,
            uploads = CapabilitySupport.UNSUPPORTED,
        )

    private fun nativeDirectUnusableContent() =
        ClientContentCapabilities(
            normal = CapabilitySupport.UNSUPPORTED,
            kids = CapabilitySupport.UNSUPPORTED,
            ageRestricted = CapabilitySupport.UNSUPPORTED,
        )

    private fun unverifiedProbeContent() =
        ClientContentCapabilities(
            normal = CapabilitySupport.UNKNOWN,
            explicit = CapabilitySupport.UNKNOWN,
            kids = CapabilitySupport.UNKNOWN,
            ageRestricted = CapabilitySupport.UNKNOWN,
            live = CapabilitySupport.UNKNOWN,
            uploads = CapabilitySupport.UNKNOWN,
        )

    private fun globallyUnusableContent() =
        ClientContentCapabilities(
            normal = CapabilitySupport.UNSUPPORTED,
            explicit = CapabilitySupport.UNSUPPORTED,
            kids = CapabilitySupport.UNSUPPORTED,
            ageRestricted = CapabilitySupport.UNSUPPORTED,
            live = CapabilitySupport.UNSUPPORTED,
            uploads = CapabilitySupport.UNSUPPORTED,
        )

    private fun validateInventory(manifests: List<PlaybackClientManifest>) {
        require(manifests.map { it.id }.distinct().size == manifests.size) { "Playback client manifest IDs must be unique" }
        require(manifests.map { it.client }.distinct().size == manifests.size) {
            "Every manifest must have a distinguishable YouTubeClient request profile"
        }
    }
}
