package com.metrolist.innertubex.models

import kotlinx.serialization.Serializable

@Serializable
enum class PoTokenBinding {
    VIDEO_ID,
    VISITOR_DATA,
}

@Serializable
data class YouTubeClient(
    val clientName: String,
    val clientVersion: String,
    val clientId: String,
    val userAgent: String,
    val osName: String? = null,
    val osVersion: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
    val androidSdkVersion: String? = null,
    val platform: String? = null,
    val buildId: String? = null,
    val cronetVersion: String? = null,
    val packageName: String? = null,
    val friendlyName: String? = null,
    val loginSupported: Boolean = false,
    val loginRequired: Boolean = false,
    val useSignatureTimestamp: Boolean = false,
    val isEmbedded: Boolean = false,
    val useWebPoTokens: Boolean = false,
    val requirePoToken: Boolean = false,
    val poTokenBinding: PoTokenBinding = PoTokenBinding.VIDEO_ID,
    val includeUserAgentInContext: Boolean = false,
    val useSabr: Boolean = false,
    val useMusicPlayerEndpoint: Boolean = false,
    val skipPlayerResponseValidation: Boolean = false,
) {
    fun toContext(
        locale: YouTubeLocale,
        visitorData: String?,
        dataSyncId: String?,
    ) = Context(
        thirdParty = if (isEmbedded) Context.ThirdParty(embedUrl = "https://www.reddit.com/") else null,
        client =
            Context.Client(
                clientName = clientName,
                clientVersion = clientVersion,
                userAgent = if (includeUserAgentInContext) userAgent else null,
                osName = osName,
                osVersion = osVersion,
                deviceMake = deviceMake,
                deviceModel = deviceModel,
                androidSdkVersion = androidSdkVersion,
                platform = platform,
                gl = locale.gl,
                hl = locale.hl,
                visitorData = visitorData,
            ),
        user =
            Context.User(
                onBehalfOfUser = if (loginSupported) dataSyncId else null,
            ),
    )

    companion object {
        const val USER_AGENT_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

        const val ORIGIN_YOUTUBE_MUSIC = "https://music.youtube.com"
        const val REFERER_YOUTUBE_MUSIC = "$ORIGIN_YOUTUBE_MUSIC/"

        /*
         * In general:
         * - Authenticated clients are slower than signed-out clients
         * - Signed-out clients can't play explicit tracks most of the time
         * - Authenticated clients need to be tokenized in order to play songs for kids (unauthed clients will fail)
         */

        // -------------------------------------------------------------------------
        // Authenticated playback (loginSupported = true)
        // -------------------------------------------------------------------------

        /**
         * Primary YouTube Music client. Also used for browse, search, and account APIs.
         * Works for normal, explicit, and kids content when signed in.
         */
        val WEB_REMIX =
            YouTubeClient(
                clientName = "WEB_REMIX",
                clientVersion = "1.20260707.12.00",
                clientId = "67", // lol
                userAgent = USER_AGENT_WEB,
                loginSupported = true,
                useSignatureTimestamp = true,
                useWebPoTokens = true,
            )

        /**
         * Login-required creator client. Works for normal, explicit, and kids content.
         */
        val WEB_CREATOR =
            YouTubeClient(
                clientName = "WEB_CREATOR",
                clientVersion = "1.20260708.06.00",
                clientId = "62",
                userAgent = USER_AGENT_WEB,
                loginSupported = true,
                loginRequired = true,
                useSignatureTimestamp = true,
                useWebPoTokens = true,
            )

        val TVHTML5 =
            YouTubeClient(
                clientName = "TVHTML5",
                clientVersion = "7.20260707.07.00",
                clientId = "7",
                userAgent =
                    "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold " +
                        "(unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)",
                loginSupported = true,
                useSignatureTimestamp = true,
                useWebPoTokens = true,
                includeUserAgentInContext = true,
            )

        // -------------------------------------------------------------------------
        // Unauthenticated playback (loginSupported = false)
        // -------------------------------------------------------------------------

        /**
         * Internal YT client for an unreleased YT client. May stop working at any time.
         * Retained for explicit probing because it can return direct audio URLs quickly.
         * Clean Android sessions may stall, and current explicit and kids probes are rejected.
         */
        val VISIONOS =
            YouTubeClient(
                clientName = "VISIONOS",
                clientVersion = "1.02",
                clientId = "101",
                userAgent =
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 " +
                        "(KHTML, like Gecko) Version/26.0 Safari/605.1.15",
                osName = "visionOS",
                osVersion = "26.5.23O471",
                deviceMake = "Apple",
                deviceModel = "RealityDevice17,1",
                friendlyName = "visionOS",
                loginSupported = false,
                useSignatureTimestamp = false,
                useMusicPlayerEndpoint = true,
            )

        val VISIONOS_0_1 =
            YouTubeClient(
                clientName = "VISIONOS",
                clientVersion = "0.1",
                clientId = "101",
                userAgent =
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6) AppleWebKit/605.1.15 " +
                        "(KHTML, like Gecko) Version/17.5 Safari/605.1.15",
                osName = "VISION_OS",
                osVersion = "1.3",
                deviceMake = "Apple",
                deviceModel = "RealityDevice14,1",
                platform = "MOBILE",
                friendlyName = "visionOS 0.1",
                loginSupported = false,
                useSignatureTimestamp = false,
                useMusicPlayerEndpoint = true,
                skipPlayerResponseValidation = true,
            )

        /**
         * Mirrors yt-dlp android_vr profile. Versions above 1.65 may return SABR-only streams.
         * Keep older Android VR profiles around because they have different format/stability behavior.
         * Normal content only; explicit and kids fail.
         */
        val ANDROID_VR_1_65_10 =
            YouTubeClient(
                clientName = "ANDROID_VR",
                clientVersion = "1.65.10",
                clientId = "28",
                userAgent =
                    "com.google.android.apps.youtube.vr.oculus/1.65.10 " +
                        "(Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                osName = "Android",
                osVersion = "12L",
                deviceMake = "Oculus",
                deviceModel = "Quest 3",
                androidSdkVersion = "32",
                friendlyName = "Android VR 1.65",
                loginSupported = false,
                useSignatureTimestamp = false,
                includeUserAgentInContext = true,
                useMusicPlayerEndpoint = true,
            )

        val ANDROID_VR_NO_AUTH =
            YouTubeClient(
                clientName = "ANDROID_VR",
                clientVersion = "1.61.48",
                clientId = "28",
                userAgent =
                    "com.google.android.apps.youtube.vr.oculus/1.61.48 " +
                        "(Linux; U; Android 12; en_US; Oculus Quest 3; " +
                        "Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)",
                loginSupported = false,
                useSignatureTimestamp = false,
                includeUserAgentInContext = true,
                useMusicPlayerEndpoint = true,
            )

        /**
         * Video not playable: Kids / Paid / Movie / Private / Age-restricted.
         * This client can only be used when logged out.
         */
        val ANDROID_VR_1_61_48 =
            YouTubeClient(
                clientName = "ANDROID_VR",
                clientVersion = "1.61.48",
                clientId = "28",
                userAgent =
                    "com.google.android.apps.youtube.vr.oculus/1.61.48 " +
                        "(Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; " +
                        "Cronet/132.0.6808.3)",
                osName = "Android",
                osVersion = "12",
                deviceMake = "Oculus",
                deviceModel = "Quest 3",
                androidSdkVersion = "32",
                buildId = "SQ3A.220605.009.A1",
                cronetVersion = "132.0.6808.3",
                packageName = "com.google.android.apps.youtube.vr.oculus",
                friendlyName = "Android VR 1.61",
                loginSupported = false,
                useSignatureTimestamp = false,
                includeUserAgentInContext = true,
                useMusicPlayerEndpoint = true,
            )

        /**
         * Uses non-adaptive bitrate, which fixes audio stuttering with YT Music.
         * Does not use AV1.
         * Keep as a fallback candidate even though yt-dlp currently defaults to 1.65.10.
         */
        val ANDROID_VR_1_43_32 =
            YouTubeClient(
                clientName = "ANDROID_VR",
                clientVersion = "1.43.32",
                clientId = "28",
                userAgent =
                    "com.google.android.apps.youtube.vr.oculus/1.43.32 " +
                        "(Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; " +
                        "Cronet/107.0.5284.2)",
                osName = "Android",
                osVersion = "12",
                deviceMake = "Oculus",
                deviceModel = "Quest 3",
                androidSdkVersion = "32",
                buildId = "SQ3A.220605.009.A1",
                cronetVersion = "107.0.5284.2",
                packageName = "com.google.android.apps.youtube.vr.oculus",
                friendlyName = "Android VR 1.43",
                loginSupported = false,
                useSignatureTimestamp = false,
                includeUserAgentInContext = true,
                useMusicPlayerEndpoint = true,
            )

        /**
         * Web-cipher TV client. The player token is visitor-bound and the media token is video-bound.
         */
        val TVHTML5_SIMPLY =
            YouTubeClient(
                clientName = "TVHTML5_SIMPLY",
                clientVersion = "1.0",
                clientId = "75",
                userAgent = TVHTML5.userAgent,
                useSignatureTimestamp = true,
                useWebPoTokens = true,
                requirePoToken = true,
                poTokenBinding = PoTokenBinding.VISITOR_DATA,
                useMusicPlayerEndpoint = true,
            )

        val TVHTML5_SIMPLY_EMBEDDED_PLAYER =
            YouTubeClient(
                clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
                clientVersion = "2.0",
                clientId = "85",
                userAgent = TVHTML5.userAgent,
                useSignatureTimestamp = true,
                isEmbedded = true,
                useWebPoTokens = true,
                requirePoToken = true,
                poTokenBinding = PoTokenBinding.VISITOR_DATA,
                friendlyName = "TVHTML5 Simply Embedded",
            )

        // -------------------------------------------------------------------------
        // Other (non-playback / API-only)
        // -------------------------------------------------------------------------

        /**
         * API-only client for non-playback InnerTube calls. Do not use for stream extraction.
         * Player requests return UNPLAYABLE for normal and kids content.
         */
        val WEB =
            YouTubeClient(
                clientName = "WEB",
                clientVersion = "2.20260708.00.00",
                clientId = "1",
                userAgent = USER_AGENT_WEB,
                loginSupported = true,
                useSignatureTimestamp = true,
                useWebPoTokens = true,
            )

        /** Safari-flavoured WEB profile retained for direct/HLS probes; SABR has a dedicated profile. */
        val WEB_SAFARI =
            WEB.copy(
                userAgent =
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                        "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.5 Safari/605.1.15,gzip(gfe)",
                friendlyName = "Web Safari",
            )

        /** Mobile web profile with ultralow formats. */
        val MWEB =
            YouTubeClient(
                clientName = "MWEB",
                clientVersion = "2.20260708.05.00",
                clientId = "2",
                userAgent =
                    "Mozilla/5.0 (iPad; CPU OS 16_7_10 like Mac OS X) AppleWebKit/605.1.15 " +
                        "(KHTML, like Gecko) Version/16.6 Mobile/15E148 Safari/604.1,gzip(gfe)",
                friendlyName = "Mobile Web",
                loginSupported = true,
                useSignatureTimestamp = true,
                useWebPoTokens = true,
                includeUserAgentInContext = true,
            )

        /** Third-party iframe profile. Only embeddable content is expected to work. */
        val WEB_EMBEDDED_PLAYER =
            YouTubeClient(
                clientName = "WEB_EMBEDDED_PLAYER",
                clientVersion = "2.20260708.00.00",
                clientId = "56",
                userAgent = USER_AGENT_WEB,
                friendlyName = "Web Embedded",
                loginSupported = true,
                useSignatureTimestamp = true,
                isEmbedded = true,
            )

        /** Current native iOS profile. It needs an iOS PO-token provider when enforcement applies. */
        val IOS =
            YouTubeClient(
                clientName = "IOS",
                clientVersion = "21.26.4",
                clientId = "5",
                userAgent = "com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
                osName = "iPhone",
                osVersion = "18.3.2.22D82",
                deviceMake = "Apple",
                deviceModel = "iPhone16,2",
                friendlyName = "iOS",
                includeUserAgentInContext = true,
            )

        /** iPad device variant retained for range-failure regression probes. */
        val IPADOS =
            IOS.copy(
                userAgent =
                    "com.google.ios.youtube/21.26.4 " +
                        "(iPad7,6; U; CPU iPadOS 17_7_10 like Mac OS X; en-US)",
                osName = "iPadOS",
                osVersion = "17.7.10.21H450",
                deviceModel = "iPad7,6",
                friendlyName = "iPadOS",
            )

        /** YouTube Kids web identity. Kept in inventory pending full playback validation. */
        val WEB_KIDS =
            YouTubeClient(
                clientName = "WEB_KIDS",
                clientVersion = "2.20260205.00.00",
                clientId = "76",
                userAgent = USER_AGENT_WEB,
                friendlyName = "Web Kids",
                useSignatureTimestamp = true,
            )

        /** Native YouTube Music identity. Current playback generally needs platform attestation. */
        val ANDROID_MUSIC =
            YouTubeClient(
                clientName = "ANDROID_MUSIC",
                clientVersion = "5.34.51",
                clientId = "21",
                userAgent = "com.google.android.apps.youtube.music/5.34.51 (Linux; U; Android 11) gzip",
                osName = "Android",
                osVersion = "11",
                androidSdkVersion = "30",
                friendlyName = "Android Music",
                includeUserAgentInContext = true,
            )

        /** Native Studio identity retained as an experimental inventory/probe profile. */
        val ANDROID_CREATOR =
            YouTubeClient(
                clientName = "ANDROID_CREATOR",
                clientVersion = "25.03.101",
                clientId = "14",
                userAgent =
                    "com.google.android.apps.youtube.creator/25.03.101 (Linux; U; Android 15; en_US; " +
                        "Pixel 9 Pro Fold; Build/AP3A.241005.015.A2; Cronet/132.0.6779.0)",
                osName = "Android",
                osVersion = "15",
                deviceMake = "Google",
                deviceModel = "Pixel 9 Pro Fold",
                androidSdkVersion = "35",
                buildId = "AP3A.241005.015.A2",
                cronetVersion = "132.0.6779.0",
                packageName = "com.google.android.apps.youtube.creator",
                friendlyName = "Android Studio",
                loginSupported = true,
                loginRequired = true,
                useSignatureTimestamp = true,
                includeUserAgentInContext = true,
            )

        /** Current native Android identity. Platform attestation is described by the playback manifest. */
        val ANDROID =
            YouTubeClient(
                clientName = "ANDROID",
                clientVersion = "21.26.364",
                clientId = "3",
                userAgent = "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip",
                osName = "Android",
                osVersion = "11",
                androidSdkVersion = "30",
                friendlyName = "Android",
                loginSupported = false,
                useSignatureTimestamp = false,
                includeUserAgentInContext = true,
            )

        val ANDROID_SABR =
            ANDROID.copy(
                friendlyName = "Android SABR",
                useSabr = true,
            )

        val WEB_REMIX_SABR =
            WEB_REMIX.copy(
                friendlyName = "Web Remix SABR",
                requirePoToken = true,
                poTokenBinding = PoTokenBinding.VISITOR_DATA,
                useSabr = true,
            )

        val ANDROID_VR_SABR =
            ANDROID_VR_1_65_10.copy(
                friendlyName = "Android VR SABR",
                useSabr = true,
            )

        val VISIONOS_SABR =
            VISIONOS.copy(
                friendlyName = "visionOS SABR",
                useSabr = true,
            )

        val TVHTML5_SIMPLY_SABR =
            TVHTML5_SIMPLY.copy(
                friendlyName = "TV HTML5 Simply SABR",
                useSabr = true,
            )

        val WEB_SABR =
            WEB.copy(
                friendlyName = "Web SABR",
                poTokenBinding = PoTokenBinding.VIDEO_ID,
                useSabr = true,
            )

        val WEB_SAFARI_SABR =
            WEB_SAFARI.copy(
                friendlyName = "Web Safari SABR",
                poTokenBinding = PoTokenBinding.VIDEO_ID,
                useSabr = true,
            )

        val MWEB_SABR =
            MWEB.copy(
                friendlyName = "Mobile Web SABR",
                poTokenBinding = PoTokenBinding.VIDEO_ID,
                useSabr = true,
            )

        val TVHTML5_SABR =
            TVHTML5.copy(
                friendlyName = "TV HTML5 SABR",
                useSabr = true,
            )

        val IOS_SABR =
            IOS.copy(
                friendlyName = "iOS SABR",
                useSabr = true,
            )

        /** Cookie-oriented fallback used by current yt-dlp defaults. */
        val TVHTML5_DOWNGRADED =
            YouTubeClient(
                clientName = "TVHTML5",
                clientVersion = "5.20260707",
                clientId = "7",
                userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version",
                friendlyName = "TV HTML5 Downgraded",
                loginSupported = true,
                useSignatureTimestamp = true,
                includeUserAgentInContext = true,
            )

        /** Historical probe identity: iOS Music client ID 26, current source evidence only. */
        val IOS_MUSIC =
            YouTubeClient(
                clientName = "IOS_MUSIC",
                clientVersion = "7.27.0",
                clientId = "26",
                userAgent = "com.google.ios.youtubemusic/7.27.0 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)",
                osName = "iPhone",
                osVersion = "18.1.0.22B83",
                deviceMake = "Apple",
                deviceModel = "iPhone16,2",
                platform = "MOBILE",
                friendlyName = "iOS Music probe",
            )

        /** Historical probe identity: Android Kids client ID 18, current source evidence only. */
        val ANDROID_KIDS =
            YouTubeClient(
                clientName = "ANDROID_KIDS",
                clientVersion = "7.36.1",
                clientId = "18",
                userAgent = "com.google.android.apps.youtube.kids/7.36.1 (Linux; U; Android 11) gzip",
                osName = "Android",
                osVersion = "11",
                androidSdkVersion = "30",
                friendlyName = "Android Kids probe",
            )

        /** Historical probe identity: Android Producer client ID 91, current source evidence only. */
        val ANDROID_PRODUCER =
            YouTubeClient(
                clientName = "ANDROID_PRODUCER",
                clientVersion = "0.111.1",
                clientId = "91",
                userAgent = "com.google.android.apps.youtube.producer/0.111.1 (Linux; U; Android 11) gzip",
                osName = "Android",
                osVersion = "11",
                androidSdkVersion = "30",
                friendlyName = "Android Producer probe",
            )

        /** Historical probe identity: Media Connect Frontend client ID 95, current source evidence only. */
        val MEDIA_CONNECT_FRONTEND =
            YouTubeClient(
                clientName = "MEDIA_CONNECT_FRONTEND",
                clientVersion = "0.1",
                clientId = "95",
                userAgent = "Mozilla/5.0",
                friendlyName = "Media Connect probe",
            )
    }
}
