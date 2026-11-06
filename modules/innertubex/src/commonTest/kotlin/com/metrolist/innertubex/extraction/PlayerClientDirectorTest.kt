package com.metrolist.innertubex.extraction

import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.extraction.strategy.AuthenticationPolicy
import com.metrolist.innertubex.extraction.strategy.ClientFallbackStrategy
import com.metrolist.innertubex.extraction.strategy.ClientSelectionRequest
import com.metrolist.innertubex.extraction.strategy.ClientSelectionResult
import com.metrolist.innertubex.extraction.strategy.PlaybackClientCatalog
import com.metrolist.innertubex.extraction.strategy.PlaybackTransportPreference
import com.metrolist.innertubex.extraction.strategy.PoTokenProviderKind
import com.metrolist.innertubex.extraction.strategy.SelectedClient
import com.metrolist.innertubex.models.YouTubeClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class PlayerClientDirectorTest {
    @Test
    fun embeddedConfigVisitorOverridesSessionVisitor() =
        runBlocking {
            val sentVisitors = mutableListOf<String?>()
            val client =
                HttpClient(
                    MockEngine { request ->
                        sentVisitors += request.headers["X-Goog-Visitor-Id"]
                        respond(PLAYER_RESPONSE, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                ) {
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                }
            val innerTube = InnerTube(client, retryDelay = {}).also { it.visitorData = "session-visitor" }
            val embedded = fixed(checkNotNull(PlaybackClientCatalog.findManifest("WEB_EMBEDDED_PLAYER")))

            PlayerClientDirector(innerTube, embedded, NoTokenProvider)
                .fetchPlayerResponses("video", PlayerConfig("player.js", 1, "embed-visitor", null, "flags"), ContentHints())
            PlayerClientDirector(innerTube, embedded, NoTokenProvider)
                .fetchPlayerResponses("video", PlayerConfig("player.js", 1, "page-visitor", null), ContentHints())

            assertEquals(listOf<String?>("embed-visitor", "session-visitor"), sentVisitors)
            client.close()
        }

    @Test
    fun videoRequestsPreferSabrTransport() =
        runBlocking {
            val client = client { PLAYER_RESPONSE }
            val manifest = checkNotNull(PlaybackClientCatalog.findManifest("VISIONOS"))
            val preferences = mutableListOf<PlaybackTransportPreference>()
            val director =
                PlayerClientDirector(
                    InnerTube(client, retryDelay = {}).also { it.visitorData = "visitor" },
                    object : ClientFallbackStrategy {
                        override fun resolveClients(hints: ContentHints) = listOf(manifest.client)

                        override fun selectClients(request: ClientSelectionRequest): ClientSelectionResult {
                            preferences += request.transportPreference
                            return ClientSelectionResult(listOf(SelectedClient(manifest.client, manifest)))
                        }
                    },
                    NoTokenProvider,
                )

            listOf(ContentHints(wantVideo = true), ContentHints()).forEach {
                director.fetchPlayerResponses("video", PlayerConfig("player.js", null, null, null), it)
            }

            assertEquals(listOf(PlaybackTransportPreference.SABR, PlaybackTransportPreference.AUTO), preferences)
            client.close()
        }

    @Test
    fun authenticatedPremiumManualOverrideSkipsTokenMinting() =
        runBlocking {
            val client = client { PLAYER_RESPONSE }
            val innerTube =
                InnerTube(client, retryDelay = {}).also {
                    it.cookie = "SAPISID=synthetic-session"
                    it.visitorData = "synthetic-visitor"
                }
            val manifest = checkNotNull(PlaybackClientCatalog.findManifest("WEB_REMIX"))
            var selectionRequest: ClientSelectionRequest? = null
            var tokenRequests = 0
            val tokenProvider =
                object : TokenProvider {
                    override val capabilities = TokenProviderCapabilities(setOf(PoTokenProviderKind.WEB_BOTGUARD))

                    override suspend fun getPoToken(
                        videoId: String,
                        visitorData: String,
                        cookie: String?,
                    ): PoTokenResult? {
                        tokenRequests++
                        return null
                    }
                }
            val director =
                PlayerClientDirector(
                    innerTube,
                    object : ClientFallbackStrategy {
                        override fun resolveClients(hints: ContentHints) = listOf(manifest.client)

                        override fun selectClients(request: ClientSelectionRequest): ClientSelectionResult {
                            selectionRequest = request
                            return ClientSelectionResult(listOf(SelectedClient(manifest.client, manifest)))
                        }
                    },
                    tokenProvider,
                )

            val result =
                director.fetchPlayerResponses(
                    "video",
                    PlayerConfig("player.js", null, null, null),
                    ContentHints(playbackClientOverrideId = "WEB_REMIX").withPremium(),
                )

            assertTrue(selectionRequest?.premium == true)
            assertEquals(0, tokenRequests)
            assertEquals("WEB_REMIX", result.playableResponses.single().clientName)
            client.close()
        }

    @Test
    fun signedOutPremiumHintStillRequiresPoToken() =
        runBlocking {
            val client = client { PLAYER_RESPONSE }
            val manifest = checkNotNull(PlaybackClientCatalog.findManifest("WEB_REMIX"))
            val director = PlayerClientDirector(InnerTube(client, retryDelay = {}), fixed(manifest), NoTokenProvider)

            val result =
                director.fetchPlayerResponses(
                    "video",
                    PlayerConfig("player.js", null, null, null),
                    ContentHints(playbackClientOverrideId = "WEB_REMIX").withPremium(),
                )

            assertTrue(result.playableResponses.isEmpty())
            client.close()
        }

    @Test
    fun premiumHintDoesNotBypassTokensForAnonymousOnlyClient() =
        runBlocking {
            val client = client { PLAYER_RESPONSE }
            val innerTube = InnerTube(client, retryDelay = {}).also { it.cookie = "SAPISID=synthetic-session" }
            val anonymousClient = YouTubeClient.VISIONOS_0_1
            val sourceManifest = checkNotNull(PlaybackClientCatalog.findManifest("WEB_REMIX"))
            val manifest =
                sourceManifest.copy(
                    id = "SYNTHETIC_ANONYMOUS",
                    client = anonymousClient,
                    authentication = AuthenticationPolicy.UNSUPPORTED,
                    request = sourceManifest.request.copy(signatureTimestamp = anonymousClient.useSignatureTimestamp, cookies = false),
                )
            val director = PlayerClientDirector(innerTube, fixed(manifest), NoTokenProvider)

            val result =
                director.fetchPlayerResponses(
                    "video",
                    PlayerConfig("player.js", null, null, null),
                    ContentHints().withPremium(),
                )

            assertTrue(result.playableResponses.isEmpty())
            client.close()
        }

    @Test
    fun requiredPoTokenIsBoundToTheCorrectVisitorAndBinding() =
        runBlocking {
            val client = client { TOKEN_PLAYER_RESPONSE }
            val innerTube = InnerTube(client, retryDelay = {}).also { it.visitorData = "visitor" }
            var receivedVisitor = ""
            val provider =
                object : TokenProvider {
                    override val capabilities =
                        TokenProviderCapabilities(
                            setOf(PoTokenProviderKind.WEB_BOTGUARD, PoTokenProviderKind.WEBPAGE_ATTESTATION),
                            usesWebView = true,
                        )

                    override suspend fun getPoToken(
                        videoId: String,
                        visitorData: String,
                        cookie: String?,
                    ): PoTokenResult {
                        receivedVisitor = visitorData
                        return PoTokenResult("player-token", "stream-token", visitorData)
                    }
                }
            val manifest = checkNotNull(PlaybackClientCatalog.findManifest("WEB_SABR"))
            val director = PlayerClientDirector(innerTube, fixed(manifest), provider)
            val result =
                director.fetchPlayerResponses(
                    "video",
                    PlayerConfig("https://www.youtube.com/s/player/x/base.js", null, null, null),
                    ContentHints(playbackClientOverrideId = "WEB_SABR"),
                )

            assertEquals("visitor", receivedVisitor)
            assertEquals("stream-token", result.playableResponses.single().streamingDataPoToken)
            client.close()
        }

    @Test
    fun tokenVisitorChangeCancelsBeforeSendingOldCredentials() =
        runBlocking {
            var requests = 0
            val client =
                HttpClient(
                    MockEngine {
                        requests++
                        respond(PLAYER_RESPONSE, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                )
            val innerTube = InnerTube(client, retryDelay = {}).also { it.visitorData = "old-visitor" }
            val manifest = checkNotNull(PlaybackClientCatalog.findManifest("WEB_REMIX"))
            val provider =
                object : TokenProvider {
                    override val capabilities = TokenProviderCapabilities(setOf(PoTokenProviderKind.WEB_BOTGUARD), usesWebView = true)

                    override suspend fun getPoToken(
                        videoId: String,
                        visitorData: String,
                        cookie: String?,
                    ): PoTokenResult {
                        innerTube.visitorData = "new-visitor"
                        return PoTokenResult("player", "stream", visitorData)
                    }
                }
            val director = PlayerClientDirector(innerTube, fixed(manifest), provider)
            assertFailsWith<CancellationException> {
                director.fetchPlayerResponses(
                    "video",
                    PlayerConfig("player.js", null, null, null),
                    ContentHints(playbackClientOverrideId = "WEB_REMIX"),
                )
            }
            assertEquals(0, requests)
            client.close()
        }

    @Test
    fun visitorFetchedForOneClientDoesNotAbortTheRestOfTheBatch() =
        runBlocking {
            val client =
                HttpClient(
                    MockEngine { request ->
                        val body =
                            when {
                                request.url.encodedPath == "/sw.js_data" -> {
                                    ")]}'\n[[\"x\",0,[[[0,1,2,3,4,5,6,7,8,9,10,11,12,\"fresh-visitor\"]]]]]"
                                }

                                // A playable Web SABR response still needs a GVS token, which triggers the visitor fetch.
                                request.headers["X-YouTube-Client-Name"] == YouTubeClient.WEB_SABR.clientId -> {
                                    TOKEN_PLAYER_RESPONSE
                                }

                                else -> {
                                    PLAYER_RESPONSE
                                }
                            }
                        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                ) {
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                }
            val innerTube = InnerTube(client, retryDelay = {})
            val provider =
                object : TokenProvider {
                    override val capabilities =
                        TokenProviderCapabilities(
                            setOf(PoTokenProviderKind.WEB_BOTGUARD, PoTokenProviderKind.WEBPAGE_ATTESTATION),
                            usesWebView = true,
                        )

                    override suspend fun getPoToken(
                        videoId: String,
                        visitorData: String,
                        cookie: String?,
                    ): PoTokenResult? = null
                }
            val manifests = listOf("WEB_SABR", "VISIONOS").map { checkNotNull(PlaybackClientCatalog.findManifest(it)) }
            val director =
                PlayerClientDirector(
                    innerTube,
                    object : ClientFallbackStrategy {
                        override fun resolveClients(hints: ContentHints) = manifests.map { it.client }

                        override fun selectClients(request: ClientSelectionRequest) =
                            ClientSelectionResult(manifests.map { SelectedClient(it.client, it) })
                    },
                    provider,
                )

            val result = director.fetchPlayerResponses("video", PlayerConfig("player.js", null, null, null), ContentHints())

            assertEquals("fresh-visitor", innerTube.visitorData)
            assertEquals(YouTubeClient.VISIONOS.clientName, result.playableResponses.single().clientName)
            client.close()
        }

    @Test
    fun unavailablePoTokenIsRequestedOnlyOncePerBatch() =
        runBlocking {
            val client = client { PLAYER_RESPONSE }
            val innerTube =
                InnerTube(client, retryDelay = {}).also {
                    it.visitorData = "visitor"
                    it.cookie = "SAPISID=test"
                }
            var tokenRequests = 0
            val provider =
                object : TokenProvider {
                    override val capabilities =
                        TokenProviderCapabilities(setOf(PoTokenProviderKind.WEB_BOTGUARD), usesWebView = true)

                    override suspend fun getPoToken(
                        videoId: String,
                        visitorData: String,
                        cookie: String?,
                    ): PoTokenResult? {
                        tokenRequests++
                        return null
                    }
                }
            val manifests =
                listOf("WEB_REMIX", "WEB_CREATOR").map { id ->
                    checkNotNull(PlaybackClientCatalog.findManifest(id))
                }
            val director =
                PlayerClientDirector(
                    innerTube,
                    object : ClientFallbackStrategy {
                        override fun resolveClients(hints: ContentHints) = manifests.map { it.client }

                        override fun selectClients(request: ClientSelectionRequest) =
                            ClientSelectionResult(manifests.map { SelectedClient(it.client, it) })
                    },
                    provider,
                )

            director.fetchPlayerResponses("video", PlayerConfig("player.js", null, null, null), ContentHints())

            assertEquals(1, tokenRequests)
            client.close()
        }

    @Test
    fun stalledClientTimesOutAndFallsThrough() =
        runBlocking {
            var requests = 0
            val client =
                HttpClient(
                    MockEngine { request ->
                        requests++
                        if (request.headers["X-YouTube-Client-Name"] == YouTubeClient.VISIONOS.clientId) delay(100)
                        respond(PLAYER_RESPONSE, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                ) {
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                }
            val director =
                PlayerClientDirector(
                    InnerTube(client, retryDelay = {}),
                    object : ClientFallbackStrategy {
                        override fun resolveClients(hints: ContentHints) = listOf(YouTubeClient.VISIONOS, YouTubeClient.ANDROID_VR_1_65_10)
                    },
                    NoTokenProvider,
                    playerRequestTimeoutMs = 25,
                )
            val batch = director.fetchPlayerResponses("video", PlayerConfig("player.js", null, null, null), ContentHints())
            assertEquals(2, requests)
            assertEquals(YouTubeClient.ANDROID_VR_1_65_10.clientName, batch.playableResponses.single().clientName)
            client.close()
        }

    @Test
    fun playerResponseBodyFormatCountIsBounded() =
        runBlocking {
            val formats =
                (1..2050).joinToString(",") {
                    "{\"itag\":$it,\"url\":\"https://r.googlevideo.com/videoplayback\",\"mimeType\":\"audio/mp4\"}"
                }
            val client = client { "{\"playabilityStatus\":{\"status\":\"OK\"},\"streamingData\":{\"adaptiveFormats\":[$formats]}}" }
            val director =
                PlayerClientDirector(
                    InnerTube(client, retryDelay = {}),
                    object : ClientFallbackStrategy {
                        override fun resolveClients(hints: ContentHints) = listOf(YouTubeClient.VISIONOS)
                    },
                    NoTokenProvider,
                )
            assertTrue(
                director
                    .fetchPlayerResponses(
                        "video",
                        PlayerConfig("player.js", null, null, null),
                        ContentHints(),
                    ).playableResponses
                    .isEmpty(),
            )
            client.close()
        }

    @Test
    fun wrongVideoResponseIsRejectedAtThePlayerBoundary() =
        runBlocking {
            val wrongVideoResponse =
                """
                {"playabilityStatus":{"status":"OK"},"videoDetails":{"videoId":"other-video"},"streamingData":{"adaptiveFormats":[{"itag":251,"url":"https://r.googlevideo.com/videoplayback","mimeType":"audio/webm","bitrate":128000}]}}
                """.trimIndent()
            val client = client { wrongVideoResponse }
            val director =
                PlayerClientDirector(
                    InnerTube(client, retryDelay = {
                    }),
                    fixed(checkNotNull(PlaybackClientCatalog.findManifest("VISIONOS_0_1"))),
                    NoTokenProvider,
                )

            val batch = director.fetchPlayerResponses("requested-video", PlayerConfig("player.js", null, null, null), ContentHints())

            assertTrue(batch.playableResponses.isEmpty())
            assertEquals("no_playable_response", batch.attempts.single().outcome)
            client.close()
        }

    @Test
    fun missingVideoIdentityRemainsCompatible() =
        runBlocking {
            val response =
                """
                {"playabilityStatus":{"status":"OK"},"streamingData":{"adaptiveFormats":[{"itag":251,"url":"https://r.googlevideo.com/videoplayback","mimeType":"audio/webm","bitrate":128000}]}}
                """.trimIndent()
            val client = client { response }
            val director =
                PlayerClientDirector(
                    InnerTube(client, retryDelay = {
                    }),
                    fixed(checkNotNull(PlaybackClientCatalog.findManifest("VISIONOS_0_1"))),
                    NoTokenProvider,
                )

            val batch = director.fetchPlayerResponses("requested-video", PlayerConfig("player.js", null, null, null), ContentHints())

            assertEquals(1, batch.playableResponses.size)
            client.close()
        }

    @Test
    fun wrongVideoResponseIsRejectedForTokenizedRequests() =
        runBlocking {
            val wrongVideoResponse =
                """
                {"playabilityStatus":{"status":"OK"},"videoDetails":{"videoId":"other-video"},"streamingData":{"serverAbrStreamingUrl":"https://r.googlevideo.com/videoplayback","adaptiveFormats":[{"itag":140,"mimeType":"audio/mp4","bitrate":128000}]},"playerConfig":{"mediaCommonConfig":{"mediaUstreamerRequestConfig":{"videoPlaybackUstreamerConfig":"AQID"}}}}
                """.trimIndent()
            val client = client { wrongVideoResponse }
            val innerTube = InnerTube(client, retryDelay = {}).also { it.visitorData = "synthetic-visitor" }
            val manifest = checkNotNull(PlaybackClientCatalog.findManifest("WEB_SABR"))
            val tokenProvider =
                object : TokenProvider {
                    override val capabilities = TokenProviderCapabilities(setOf(PoTokenProviderKind.WEB_BOTGUARD))

                    override suspend fun getPoToken(
                        videoId: String,
                        visitorData: String,
                        cookie: String?,
                    ) = PoTokenResult("synthetic-player-token", "synthetic-stream-token", visitorData)
                }
            val director = PlayerClientDirector(innerTube, fixed(manifest), tokenProvider)

            val batch =
                director.fetchPlayerResponses(
                    "requested-video",
                    PlayerConfig("https://www.youtube.com/s/player/x/base.js", null, null, null),
                    ContentHints(playbackClientOverrideId = "WEB_SABR"),
                )

            assertTrue(batch.playableResponses.isEmpty())
            client.close()
        }

    @Test
    fun playerDiagnosticsClassifyObservedTransportFields() =
        runBlocking {
            val observedResponse =
                """
                {"playabilityStatus":{"status":"UNPLAYABLE"},"videoDetails":{"videoId":"video"},"streamingData":{"formats":[{"itag":22,"mimeType":"video/mp4","width":1280,"height":720}],"adaptiveFormats":[{"itag":140,"mimeType":"audio/mp4","bitrate":128000}],"hlsManifestUrl":"https://video.google.com/live.m3u8","dashManifestUrl":"https://video.google.com/video.mpd","serverAbrStreamingUrl":"https://r.googlevideo.com/videoplayback"},"playerConfig":{"mediaCommonConfig":{"mediaUstreamerRequestConfig":{"videoPlaybackUstreamerConfig":"AQID"}}}}
                """.trimIndent()
            val client = client { observedResponse }
            val transportLogs = mutableListOf<Map<String, String>>()
            val logger =
                InnerTubeLogger { event ->
                    if (event.message == "player response decoded") transportLogs += event.details
                }
            val manifest = checkNotNull(PlaybackClientCatalog.findManifest("IOS_MUSIC"))
            val director = PlayerClientDirector(InnerTube(client, retryDelay = {}), fixed(manifest), NoTokenProvider, logger = logger)

            val batch =
                director.fetchPlayerResponses(
                    "video",
                    PlayerConfig("player.js", null, null, null),
                    ContentHints(playbackClientOverrideId = "IOS_MUSIC"),
                )

            assertTrue(batch.playableResponses.isEmpty())
            val details = transportLogs.single()
            assertEquals("1", details["adaptiveFormatCount"])
            assertEquals("0", details["adaptiveDirectCount"])
            assertEquals("0", details["adaptiveCipherCount"])
            assertEquals("1", details["progressiveFormatCount"])
            assertEquals("0", details["progressiveDirectCount"])
            assertEquals("0", details["progressiveCipherCount"])
            assertEquals("true", details["hlsPresent"])
            assertEquals("true", details["dashPresent"])
            assertEquals("true", details["sabrPresent"])
            assertEquals("true", details["sabrConfigPresent"])
            assertEquals(
                "response_shape:adaptive_direct=absent,adaptive_cipher=absent,progressive_direct=absent,progressive_cipher=absent,hls=present,dash=present,sabr=present",
                batch.attempts.single().outcome,
            )
            client.close()
        }

    @Test
    fun bearerDrmMarkerRejectsCandidateBeforeFormatSelection() =
        runBlocking {
            val drmResponses =
                listOf(
                    PLAYER_RESPONSE.replace(
                        "\"adaptiveFormats\":[{\"itag\":251",
                        "\"adaptiveFormats\":[{\"drmFamilies\":[\"WIDEVINE\"],\"itag\":251",
                    ),
                    PLAYER_RESPONSE.replace(
                        "\"streamingData\":{\"adaptiveFormats\"",
                        "\"streamingData\":{\"licenseInfos\":[{\"type\":\"WIDEVINE\"}],\"adaptiveFormats\"",
                    ),
                )
            for (drmResponse in drmResponses) {
                val client = client { drmResponse }
                val innerTube = InnerTube(client, retryDelay = {})
                val manifest = checkNotNull(PlaybackClientCatalog.findManifest("TVHTML5"))
                val credential =
                    TvBearerCredential(
                        "synthetic-bearer",
                        manifest.id,
                        Clock.System.now().plus(1.hours),
                        innerTube.sessionSnapshot().generation,
                    )
                val director = PlayerClientDirector(innerTube, fixed(manifest), NoTokenProvider)

                val batch =
                    director.fetchPlayerResponses(
                        "video",
                        PlayerConfig("player.js", null, null, null),
                        ContentHints(),
                        tvBearerCredential = credential,
                    )

                assertTrue(batch.playableResponses.isEmpty())
                client.close()
            }
        }

    @Test
    fun bearerEmptyDrmMarkersRemainClearCandidates() =
        runBlocking {
            val clearResponses =
                listOf(
                    PLAYER_RESPONSE.replace(
                        "\"streamingData\":{\"adaptiveFormats\"",
                        "\"streamingData\":{\"licenseInfos\":null,\"drmFamilies\":[],\"adaptiveFormats\"",
                    ),
                    PLAYER_RESPONSE.replace(
                        "\"itag\":251",
                        "\"drmFamilies\":null,\"drmTrackType\":\"\",\"itag\":251",
                    ),
                )
            for (clearResponse in clearResponses) {
                val client = client { clearResponse }
                val innerTube = InnerTube(client, retryDelay = {})
                val manifest = checkNotNull(PlaybackClientCatalog.findManifest("TVHTML5"))
                val credential =
                    TvBearerCredential(
                        "synthetic-bearer",
                        manifest.id,
                        Clock.System.now().plus(1.hours),
                        innerTube.sessionSnapshot().generation,
                    )
                val director = PlayerClientDirector(innerTube, fixed(manifest), NoTokenProvider)

                val batch =
                    director.fetchPlayerResponses(
                        "video",
                        PlayerConfig("player.js", null, null, null),
                        ContentHints(),
                        tvBearerCredential = credential,
                    )

                assertEquals(1, batch.playableResponses.size)
                client.close()
            }
        }

    @Test
    fun expiredTvBearerCredentialIsRejectedBeforeRequest() =
        runBlocking {
            var requests = 0
            val client =
                HttpClient(
                    MockEngine {
                        requests++
                        respond(PLAYER_RESPONSE, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                )
            val innerTube = InnerTube(client, retryDelay = {})
            val manifest = checkNotNull(PlaybackClientCatalog.findManifest("TVHTML5"))
            val credential = TvBearerCredential("synthetic-bearer", manifest.id, Clock.System.now(), innerTube.sessionSnapshot().generation)
            val director = PlayerClientDirector(innerTube, fixed(manifest), NoTokenProvider)

            val batch =
                director.fetchPlayerResponses(
                    "video",
                    PlayerConfig("player.js", null, null, null),
                    ContentHints(),
                    tvBearerCredential = credential,
                )

            assertTrue(batch.playableResponses.isEmpty())
            assertEquals(0, requests)
            client.close()
        }

    @Test
    fun playerResponseBodyByteLimitRejectsOversizedPayload() =
        runBlocking {
            val client = client { "x".repeat(4 * 1024 * 1024 + 1) }
            val director =
                PlayerClientDirector(
                    InnerTube(client, retryDelay = {}),
                    object : ClientFallbackStrategy {
                        override fun resolveClients(hints: ContentHints) = listOf(YouTubeClient.VISIONOS)
                    },
                    NoTokenProvider,
                )

            val batch = director.fetchPlayerResponses("video", PlayerConfig("player.js", null, null, null), ContentHints())

            assertTrue(batch.playableResponses.isEmpty())
            assertTrue(batch.requestFailures.isNotEmpty())
            client.close()
        }

    @Test
    fun hlsResponseIsReturnedForLiveContent() =
        runBlocking {
            val client =
                client {
                    "{\"playabilityStatus\":{\"status\":\"OK\"},\"streamingData\":{\"hlsManifestUrl\":\"https://video.google.com/live.m3u8\"}}"
                }
            val director =
                PlayerClientDirector(
                    InnerTube(client, retryDelay = {}),
                    object : ClientFallbackStrategy {
                        override fun resolveClients(hints: ContentHints) = listOf(YouTubeClient.VISIONOS)
                    },
                    NoTokenProvider,
                )

            val batch = director.fetchPlayerResponses("video", PlayerConfig("player.js", null, null, null), ContentHints(isLive = true))

            assertEquals(1, batch.playableResponses.size)
            assertEquals(
                "https://video.google.com/live.m3u8",
                batch.playableResponses
                    .single()
                    .response.streamingData
                    ?.hlsManifestUrl,
            )
            client.close()
        }

    private fun fixed(manifest: com.metrolist.innertubex.extraction.strategy.PlaybackClientManifest) =
        object : ClientFallbackStrategy {
            override fun resolveClients(hints: ContentHints) = listOf(manifest.client)

            override fun selectClients(request: ClientSelectionRequest) =
                ClientSelectionResult(listOf(SelectedClient(manifest.client, manifest)))
        }

    private fun client(body: () -> String) =
        HttpClient(
            MockEngine {
                respond(body(), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        ) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

    private object NoTokenProvider : TokenProvider {
        override suspend fun getPoToken(
            videoId: String,
            visitorData: String,
            cookie: String?,
        ) = null
    }

    private companion object {
        val PLAYER_RESPONSE =
            """
            {"playabilityStatus":{"status":"OK"},"streamingData":{"adaptiveFormats":[{"itag":251,"url":"https://r.googlevideo.com/videoplayback","mimeType":"audio/webm","bitrate":128000}]}}
            """.trimIndent()
        val TOKEN_PLAYER_RESPONSE =
            """
            {"playabilityStatus":{"status":"OK"},"streamingData":{"serverAbrStreamingUrl":"https://r.googlevideo.com/videoplayback","adaptiveFormats":[{"itag":140,"mimeType":"audio/mp4","bitrate":128000}]},"playerConfig":{"mediaCommonConfig":{"mediaUstreamerRequestConfig":{"videoPlaybackUstreamerConfig":"AQID"}}}}
            """.trimIndent()
    }
}
