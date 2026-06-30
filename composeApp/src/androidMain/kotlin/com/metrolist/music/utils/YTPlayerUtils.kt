@file:OptIn(UnstableApi::class)

/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.utils

import android.net.ConnectivityManager
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import co.touchlab.kermit.Logger
import com.metrolist.innertube.NewPipeExtractor
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeClient
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.metrolist.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.metrolist.innertube.models.YouTubeClient.Companion.IOS
import com.metrolist.innertube.models.YouTubeClient.Companion.IPADOS
import com.metrolist.innertube.models.YouTubeClient.Companion.MOBILE
import com.metrolist.innertube.models.YouTubeClient.Companion.TVHTML5
import com.metrolist.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.metrolist.innertube.models.YouTubeClient.Companion.VISIONOS
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.metrolist.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.metrolist.innertube.models.response.PlayerResponse
import com.metrolist.music.utils.YTPlayerUtils.MAIN_CLIENT
import com.metrolist.music.utils.YTPlayerUtils.STREAM_FALLBACK_CLIENTS
import com.metrolist.music.utils.YTPlayerUtils.validateStatus
import com.metrolist.music.utils.cipher.CipherDeobfuscator
import com.metrolist.music.utils.potoken.PoTokenGenerator
import com.metrolist.music.utils.potoken.PoTokenResult
import it.fast4x.rimusic.enums.AudioQualityFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object YTPlayerUtils : KoinComponent {
    private const val logTag = "YTPlayerUtils"
    private const val TAG = "YTPlayerUtils"

    private val httpClient by inject<OkHttpClient>()
    private val logger = Logger.withTag(logTag)

    private val poTokenGenerator = PoTokenGenerator()

    // Track videoIds whose WEB_REMIX stream URL 403'd on the ExoPlayer GET, so the next resolution
    // falls through to the fallback clients instead of skipping HEAD validation and looping.
    private val webRemixFailedIds = java.util.Collections.newSetFromMap(
        java.util.concurrent.ConcurrentHashMap<String, Boolean>(),
    )

    fun markWebRemixFailed(videoId: String) {
        webRemixFailedIds.add(videoId)
    }

    /**
     * Cleared when the cipher recovers (player config refreshed after a stream rejection): the
     * prior WEB_REMIX failures were caused by the stale cipher, so let resolution try WEB_REMIX
     * again instead of staying pinned to a lower fallback client for the rest of the process.
     */
    fun clearWebRemixFailures() {
        webRemixFailedIds.clear()
    }

    // Fire-and-forget scope for the cipher config self-heal triggered when a cipher client fails
    // stream validation during resolution. Only WEB_REMIX skips HEAD validation (so its bad URL
    // 403s on ExoPlayer and hits MusicService's handler); WEB_CREATOR / TVHTML5 / WEB are validated
    // here and never reach ExoPlayer, so without this trigger a WEB_REMIX-disabled user would never
    // self-heal a stale/wrong cipher config. Kept off the resolution coroutine so the (network)
    // refresh never blocks falling through to the next client.
    private val cipherRefreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val MAIN_CLIENT: YouTubeClient = WEB_REMIX

    // VISIONOS first (its CDN URL has no spc throttle gate, so it streams whole songs with no
    // poToken/cipher — the most reliable fallback), then WEB_CREATOR, TVHTML5, the ANDROID_VR
    // variants, then TVHTML5_SIMPLY_EMBEDDED_PLAYER (login-free, bypasses age-restriction for
    // logged-out users), then the spc-gated IOS/IPADOS as last-ditch attempts.
    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        VISIONOS,
        WEB_CREATOR,
        TVHTML5,
        ANDROID_VR_1_43_32,
        ANDROID_VR_1_61_48,
        TVHTML5_SIMPLY_EMBEDDED_PLAYER,
        IOS,
        IPADOS,
        ANDROID_CREATOR,
        ANDROID_VR_NO_AUTH,
        MOBILE,
        WEB,
    )

    /** Client names disabled by the user in Settings → Stream sources. Updated reactively by MusicService. */
    @Volatile
    var disabledStreamClients: Set<String> = emptySet()

    // A stable video id used only to warm the local BotGuard token generator; the token is
    // discarded. PoToken generation is a local WebView computation (no YouTube /player call), so
    // this triggers no network request to YouTube for the video itself.
    private const val POTOKEN_WARMUP_VIDEO_ID = "jNQXAC9IVRw"

    /**
     * Best-effort warm-up of the PoToken/BotGuard generator (BotGuard cold-start is ~2–5s) so the
     * first real playback skips it. Requires a session (visitorData); the caller should gate this on
     * visitorData being ready. The cipher WebView warm-up is separate (CipherDeobfuscator.prewarm)
     * since it needs no session. Failure is swallowed; playback falls back to lazy init unchanged.
     */
    suspend fun prewarmPoToken() {
        val sessionId = YouTube.visitorData ?: return
        if (!MAIN_CLIENT.useWebPoTokens) return
        runCatching {
            withContext(Dispatchers.IO) {
                poTokenGenerator.getWebClientPoToken(POTOKEN_WARMUP_VIDEO_ID, sessionId)
            }
        }.onFailure { logger.w("PoToken prewarm skipped: ${it.message}", it) }
    }

    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
        val streamClient: String = "unknown",
    )
    /**
     * Custom player response intended to use for playback.
     * Metadata like audioConfig and videoDetails are from [MAIN_CLIENT].
     * Format & stream can be from [MAIN_CLIENT] or [STREAM_FALLBACK_CLIENTS].
     */
    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQualityFormat,
        connectivityManager: ConnectivityManager,
    ): Result<PlaybackData> = runCatching {
        logger.d("=== PLAYER RESPONSE FOR PLAYBACK ===")
        logger.d("videoId: $videoId")
        logger.d("playlistId: $playlistId")
        logger.d("audioQuality: $audioQuality")

        // Check if this is an uploaded/privately owned track
        val isUploadedTrack = playlistId == "MLPT" || playlistId?.contains("MLPT") == true
        logger.d("Content type detection (preliminary):")
        logger.d("  isUploadedTrack (from playlistId): $isUploadedTrack")

        val isLoggedIn = YouTube.cookie != null
        logger.d("Authentication status: ${if (isLoggedIn) "LOGGED_IN" else "ANONYMOUS"}")

        // Get signature timestamp (same as before for normal content)
        val signatureTimestamp = getSignatureTimestampOrNull(videoId)
        logger.d("Signature timestamp: ${signatureTimestamp.timestamp}")

        // Generate PoToken
        var poToken: PoTokenResult? = null
        val sessionId = YouTube.visitorData
        if (MAIN_CLIENT.useWebPoTokens && sessionId != null) {
            logger.d("Generating PoToken for WEB_REMIX with sessionId")
            try {
                poToken = poTokenGenerator.getWebClientPoToken(videoId, sessionId)
                if (poToken != null) {
                    logger.d("PoToken generated successfully")
                }
            } catch (e: Exception) {
                logger.e("PoToken generation failed: ${e.message}", e)
            }
        }

        // Try WEB_REMIX with signature timestamp and poToken (same as before)
        logger.d("Attempting to get player response using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        var mainPlayerResponse = YouTube.player(videoId, playlistId, MAIN_CLIENT, signatureTimestamp.timestamp, poToken?.playerRequestPoToken).getOrThrow()

        // Debug uploaded track response
        if (isUploadedTrack || playlistId?.contains("MLPT") == true) {
            println("[PLAYBACK_DEBUG] Main player response status: ${mainPlayerResponse.playabilityStatus.status}")
            println("[PLAYBACK_DEBUG] Playability reason: ${mainPlayerResponse.playabilityStatus.reason}")
            println("[PLAYBACK_DEBUG] Video details: title=${mainPlayerResponse.videoDetails?.title}, videoId=${mainPlayerResponse.videoDetails?.videoId}")
            println("[PLAYBACK_DEBUG] Streaming data null? ${mainPlayerResponse.streamingData == null}")
            println("[PLAYBACK_DEBUG] Adaptive formats count: ${mainPlayerResponse.streamingData?.adaptiveFormats?.size ?: 0}")
        }

        var usedAgeRestrictedClient: YouTubeClient? = null
        val wasOriginallyAgeRestricted: Boolean

        // Check if WEB_REMIX response indicates age-restricted
        val mainStatus = mainPlayerResponse.playabilityStatus.status
        val isAgeRestrictedFromResponse = mainStatus in listOf("AGE_CHECK_REQUIRED", "AGE_VERIFICATION_REQUIRED", "LOGIN_REQUIRED", "CONTENT_CHECK_REQUIRED")
        wasOriginallyAgeRestricted = isAgeRestrictedFromResponse

        if (isAgeRestrictedFromResponse && isLoggedIn) {
            // Age-restricted: use WEB_CREATOR directly (no NewPipe needed from here)
            logger.d("Age-restricted detected, using WEB_CREATOR")
            logger.i("Age-restricted: using WEB_CREATOR for videoId=$videoId")
            val creatorResponse = YouTube.player(videoId, playlistId, WEB_CREATOR, null, null).getOrNull()
            if (creatorResponse?.playabilityStatus?.status == "OK") {
                logger.d("WEB_CREATOR works for age-restricted content")
                mainPlayerResponse = creatorResponse
                usedAgeRestrictedClient = WEB_CREATOR
            }
        }

        // If we still don't have a valid response, throw

        var audioConfig = mainPlayerResponse.playerConfig?.audioConfig
        val videoDetails = mainPlayerResponse.videoDetails
        val playbackTracking = mainPlayerResponse.playbackTracking
        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamExpiresInSeconds: Int? = null
        var streamPlayerResponse: PlayerResponse? = null
        val retryMainPlayerResponse: PlayerResponse? = if (usedAgeRestrictedClient != null) mainPlayerResponse else null

        // Check current status
        val currentStatus = mainPlayerResponse.playabilityStatus.status
        val isAgeRestricted = currentStatus in listOf("AGE_CHECK_REQUIRED", "AGE_VERIFICATION_REQUIRED", "LOGIN_REQUIRED", "CONTENT_CHECK_REQUIRED")

        if (isAgeRestricted) {
            logger.d("Content is still age-restricted (status: $currentStatus), will try fallback clients")
            logger
                .i("Age-restricted content detected: videoId=$videoId, status=$currentStatus")
        }

        // For age-restricted: skip main client, start with fallbacks
        // For normal content: standard order
        val startIndex = when {
            isAgeRestricted -> 0
            else -> -1
        }

        var bestFallbackFormat: PlayerResponse.StreamingData.Format? = null
        var bestFallbackUrl: String? = null
        var bestFallbackExpiry: Int? = null
        var bestFallbackResponse: PlayerResponse? = null
        var bestFallbackClient: String? = null
        var successClient: String? = null

        val hasHighQuality = mainPlayerResponse.streamingData?.adaptiveFormats?.any { it.audioQuality == "AUDIO_QUALITY_HIGH" } == true

        for (clientIndex in (startIndex until STREAM_FALLBACK_CLIENTS.size)) {
            // reset for each client
            format = null
            streamUrl = null
            streamExpiresInSeconds = null

            // decide which client to use for streams and load its player response
            val client: YouTubeClient
            if (clientIndex == -1) {
                // try with streams from main client first (use retry response if available)
                client = MAIN_CLIENT
                if (client.clientName in disabledStreamClients) {
                    logger.d("Skipping MAIN_CLIENT ${client.clientName} — disabled in stream sources")
                    continue
                }
                streamPlayerResponse = retryMainPlayerResponse ?: mainPlayerResponse
                logger.d("Trying stream from MAIN_CLIENT: ${client.clientName}")
            } else {
                // after main client use fallback clients
                client = STREAM_FALLBACK_CLIENTS[clientIndex]
                logger.d("Trying fallback client ${clientIndex + 1}/${STREAM_FALLBACK_CLIENTS.size}: ${client.clientName}")

                if (client.clientName in disabledStreamClients) {
                    logger.d("Skipping client ${client.clientName} — disabled in stream sources")
                    continue
                }

                if (client.loginRequired && !isLoggedIn && YouTube.cookie == null) {
                    // skip client if it requires login but user is not logged in
                    logger.d("Skipping client ${client.clientName} - requires login but user is not logged in")
                    continue
                }

                logger.d("Fetching player response for fallback client: ${client.clientName}")
                // Only pass poToken for clients that support it
                val clientPoToken = if (client.useWebPoTokens) poToken?.playerRequestPoToken else null
                // Skip signature timestamp for age-restricted (faster), use it for normal content
                val clientSigTimestamp = if (wasOriginallyAgeRestricted) null else signatureTimestamp.timestamp
                streamPlayerResponse =
                    YouTube.player(videoId, playlistId, client, clientSigTimestamp, clientPoToken).getOrNull()
            }

            // process current client response
            if (streamPlayerResponse?.playabilityStatus?.status == "OK") {
                logger.d("Player response status OK for client: ${if (clientIndex == -1) MAIN_CLIENT.clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")

                // Skip NewPipe for age-restricted content (NewPipe doesn't use our auth)
                val responseToUse = if (wasOriginallyAgeRestricted) {
                    logger.d("Skipping NewPipe for age-restricted content")
                    streamPlayerResponse
                } else {
                    // Try to get streams using newPipePlayer method
                    val newPipeResponse = YouTube.newPipePlayer(videoId, streamPlayerResponse)
                    newPipeResponse ?: streamPlayerResponse
                }

                if (audioConfig == null) {
                    audioConfig = responseToUse.playerConfig?.audioConfig

                    if (audioConfig != null) {
                        logger.d("AudioConfig obtained from response of client: ${if (clientIndex == -1) MAIN_CLIENT.clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")
                    } else {
                        logger.d("No audioConfig found in responseToUse.")
                    }
                }

                format =
                    findFormat(
                        responseToUse,
                        audioQuality,
                        connectivityManager,
                    )

                if (format == null) {
                    logger.d("No suitable format found for client: ${if (clientIndex == -1) MAIN_CLIENT.clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")
                    continue
                }

                logger.d("Format found: ${format.mimeType}, bitrate: ${format.bitrate}")

                streamUrl = findUrlOrNull(format, videoId, responseToUse, skipNewPipe = wasOriginallyAgeRestricted)
                if (streamUrl == null) {
                    logger.d("Stream URL not found for format")
                    continue
                }

                // Apply n-transform for throttle parameter handling
                val currentClient = if (clientIndex == -1) {
                    usedAgeRestrictedClient ?: MAIN_CLIENT
                } else {
                    STREAM_FALLBACK_CLIENTS[clientIndex]
                }

                val musicVideoType = streamPlayerResponse.videoDetails?.musicVideoType

                logger.d("=== N-TRANSFORM DECISION ===")
                logger.d("Content type analysis:")
                logger.d("  musicVideoType: $musicVideoType")
                logger.d("  isUploadedTrack (from playlistId): $isUploadedTrack")
                logger.d("  wasOriginallyAgeRestricted: $wasOriginallyAgeRestricted")
                logger.d("Client analysis:")
                logger.d("  currentClient: ${currentClient.clientName}")
                logger.d("  useWebPoTokens: ${currentClient.useWebPoTokens}")

                // Apply n-transform and PoToken for web clients (WEB, WEB_REMIX, WEB_CREATOR, TVHTML5)
                val needsNTransform = currentClient.useWebPoTokens ||
                    currentClient.clientName in listOf("WEB", "WEB_REMIX", "WEB_CREATOR", "TVHTML5")

                logger.d("N-transform decision:")
                logger.d("  needsNTransform: $needsNTransform")
                logger.d("  Reason: useWebPoTokens=${currentClient.useWebPoTokens}, " +
                    "clientInList=${currentClient.clientName in listOf("WEB", "WEB_REMIX", "WEB_CREATOR", "TVHTML5")}")

                if (needsNTransform) {
                    try {
                        logger.d("Applying n-transform to stream URL...")
                        logger.d("  Original URL length: ${streamUrl.length}")
                        logger.d("  Original URL preview: ${streamUrl.take(100)}...")

                        val originalUrl = streamUrl
                        // Use CipherDeobfuscator for n-transform (fixed implementation)
                        streamUrl = CipherDeobfuscator.transformNParamInUrl(streamUrl)

                        logger.d("  Transformed URL length: ${streamUrl.length}")
                        logger.d("  URL changed: ${originalUrl != streamUrl}")

                        // Append pot= parameter with streaming data poToken
                        val needsPoToken = currentClient.useWebPoTokens && poToken?.streamingDataPoToken != null
                        logger.d("PoToken decision:")
                        logger.d("  needsPoToken: $needsPoToken")
                        logger.d("  hasStreamingDataPoToken: ${poToken?.streamingDataPoToken != null}")

                        if (needsPoToken) {
                            logger.d("Appending pot= parameter to stream URL")
                            val separator = if ("?" in streamUrl) "&" else "?"
                            streamUrl = "${streamUrl}${separator}pot=${Uri.encode(poToken.streamingDataPoToken)}"
                            logger.d("  Final URL length (with pot): ${streamUrl.length}")
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e // request superseded/cancelled — abort cleanly, don't validate an un-transformed URL
                    } catch (e: Exception) {
                        logger.e("N-transform or pot append failed: ${e.message}", e)
                        logger.e("Stack trace: ${e.stackTraceToString().take(500)}")
                        // Continue with original URL
                    }
                } else {
                    logger.d("Skipping n-transform (not required for this client/content)")
                }

                streamExpiresInSeconds = streamPlayerResponse.streamingData?.expiresInSeconds
                if (streamExpiresInSeconds == null) {
                    logger.d("Stream expiration time not found")
                    continue
                }

                logger.d("Stream expires in: $streamExpiresInSeconds seconds")

                fun scoreFallbackQuality(quality: String?): Int = when (quality) {
                    "AUDIO_QUALITY_HIGH" -> 3
                    "AUDIO_QUALITY_MEDIUM" -> 2
                    "AUDIO_QUALITY_LOW" -> 1
                    else -> 0
                }

                fun scoreFallbackCodec(mimeType: String): Int = when {
                    mimeType.contains("opus", ignoreCase = true) -> 2
                    mimeType.contains("mp4a", ignoreCase = true) -> 1
                    else -> 0
                }

                if (audioQuality == AudioQualityFormat.High && format.audioQuality != "AUDIO_QUALITY_HIGH" && hasHighQuality) {
                    val isBetter = bestFallbackFormat == null ||
                        compareValuesBy(
                            format, bestFallbackFormat,
                            { scoreFallbackQuality(it.audioQuality) },
                            { it.audioChannels ?: 2 },
                            { scoreFallbackCodec(it.mimeType) },
                            { it.bitrate }
                        ) > 0
                    if (isBetter) {
                        logger.d("Saving fallback format: ${format.mimeType}, bitrate: ${format.bitrate}")
                        bestFallbackFormat = format
                        bestFallbackUrl = streamUrl
                        bestFallbackExpiry = streamExpiresInSeconds
                        bestFallbackResponse = streamPlayerResponse
                        bestFallbackClient = currentClient.clientName
                    }
                    continue
                }

                if (clientIndex == STREAM_FALLBACK_CLIENTS.size - 1) {
                    /** skip [validateStatus] for last client */
                    logger.d("Using last fallback client without validation: ${STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")
                    logger
                        .i("Playback: client=${currentClient.clientName}, videoId=$videoId")
                    successClient = currentClient.clientName
                    break
                }

                // WEB_REMIX authenticated CDN URLs can 403 on HEAD yet serve fine on the byte-range
                // GET that ExoPlayer makes. Skip HEAD validation for the main client and let ExoPlayer
                // try directly, UNLESS this videoId already 403'd on GET (markWebRemixFailed) — then
                // fall through to the fallback clients. Saves a validateStatus round-trip per resolve.
                if (clientIndex == -1 && currentClient.clientName == "WEB_REMIX" &&
                    !webRemixFailedIds.contains(videoId)
                ) {
                    logger.d("WEB_REMIX — skipping HEAD validation, letting ExoPlayer try directly")
                    logger.i("Playback: client=${currentClient.clientName}, videoId=$videoId")
                    successClient = currentClient.clientName
                    break
                }

                if (validateStatus(streamUrl)) {
                    // working stream found
                    logger.d("Stream validated successfully with client: ${currentClient.clientName}")
                    // Log for release builds
                    logger.i("Playback: client=${currentClient.clientName}, videoId=$videoId")
                    break
                } else {
                    logger.d("Stream validation failed for client: ${currentClient.clientName}")
                }
            } else {
                logger.d("Player response status not OK: ${streamPlayerResponse?.playabilityStatus?.status}, reason: ${streamPlayerResponse?.playabilityStatus?.reason}")
            }
        }

        if (audioQuality == AudioQualityFormat.High && format?.audioQuality != "AUDIO_QUALITY_HIGH" && bestFallbackFormat != null) {
            logger.d("Using best fallback format: ${bestFallbackFormat.mimeType}, bitrate: ${bestFallbackFormat.bitrate}")
            format = bestFallbackFormat
            streamUrl = bestFallbackUrl
            streamExpiresInSeconds = bestFallbackExpiry
            streamPlayerResponse = bestFallbackResponse
            successClient = bestFallbackClient
        }

        if (streamPlayerResponse == null) {
            logger.e("Bad stream player response - all clients failed")
            if (isUploadedTrack) {
                println("[PLAYBACK_DEBUG] FAILURE: All clients failed for uploaded track videoId=$videoId")
            }
            throw Exception("Bad stream player response")
        }

        if (streamPlayerResponse.playabilityStatus.status != "OK") {
            val errorReason = streamPlayerResponse.playabilityStatus.reason
            // YouTube often surfaces generic reasons (e.g. "error 2000") for restricted or
            // unavailable streams; Metrolist cannot recover those without official playback.
            logger.e("Playability status not OK: $errorReason")
            if (isUploadedTrack) {
                println("[PLAYBACK_DEBUG] FAILURE: Playability not OK for uploaded track - status=${streamPlayerResponse.playabilityStatus.status}, reason=$errorReason")
            }
            throw PlaybackException(
                errorReason,
                null,
                PlaybackException.ERROR_CODE_REMOTE_ERROR
            )
        }

        if (streamExpiresInSeconds == null) {
            logger.e("Missing stream expire time")
            throw Exception("Missing stream expire time")
        }

        if (format == null) {
            logger.e("Could not find format")
            throw Exception("Could not find format")
        }

        if (streamUrl == null) {
            logger.e("Could not find stream url")
            throw Exception("Could not find stream url")
        }

        logger.d("Successfully obtained playback data with format: ${format.mimeType}, bitrate: ${format.bitrate}")
        if (isUploadedTrack) {
            println("[PLAYBACK_DEBUG] SUCCESS: Got playback data for uploaded track - format=${format.mimeType}, streamUrl=${streamUrl.take(100)}...")
        }
        PlaybackData(
            audioConfig,
            videoDetails,
            playbackTracking,
            format,
            streamUrl,
            streamExpiresInSeconds,
            streamClient = successClient ?: "unknown",
        )
    }.onFailure { e ->
        println("[PLAYBACK_DEBUG] EXCEPTION during playback for videoId=$videoId: ${e::class.simpleName}: ${e.message}")
        e.printStackTrace()
    }
    /**
     * Player response intended for metadata / playback-tracking retrieval.
     * Stream URLs of this response might not work so don't use them.
     */
    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
    ): Result<PlayerResponse> {
        logger.d("Fetching metadata player response for videoId: $videoId using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        val signatureTimestamp = getSignatureTimestampOrNull(videoId)
        val sessionId = YouTube.visitorData
        var poToken: PoTokenResult? = null
        if (MAIN_CLIENT.useWebPoTokens && sessionId != null) {
            try {
                poToken = poTokenGenerator.getWebClientPoToken(videoId, sessionId)
            } catch (_: Exception) { }
        }
        return YouTube.player(videoId, playlistId, WEB_REMIX, signatureTimestamp.timestamp, poToken?.playerRequestPoToken)
            .onSuccess { logger.d("Successfully fetched metadata player response") }
            .onFailure { logger.e("Failed to fetch metadata player response", it) }
    }

    private fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQualityFormat,
        connectivityManager: ConnectivityManager,
    ): PlayerResponse.StreamingData.Format? {
        logger.d("Finding format with audioQuality: $audioQuality, network metered: ${connectivityManager.isActiveNetworkMetered}")

        val adaptiveFormats = playerResponse.streamingData?.adaptiveFormats ?: return null

        val audioCapableFormats = adaptiveFormats.filter { it.isAudio }
        if (audioCapableFormats.isEmpty()) return null

        val maxBitrate = audioCapableFormats.maxOfOrNull { it.bitrate } ?: return null

        fun scoreCodec(mimeType: String): Int = when {
            mimeType.contains("opus", ignoreCase = true) -> 2
            mimeType.contains("mp4a", ignoreCase = true) -> 1
            else -> 0
        }

        val format = when (audioQuality) {
            AudioQualityFormat.High -> {
                audioCapableFormats.maxWithOrNull(
                    compareBy<PlayerResponse.StreamingData.Format> { format ->
                        when (format.audioQuality) {
                            "AUDIO_QUALITY_HIGH" -> 3
                            "AUDIO_QUALITY_MEDIUM" -> 2
                            "AUDIO_QUALITY_LOW" -> 1
                            else -> 0
                        }
                    }.thenBy { it.audioChannels ?: 2 }
                        .thenBy { scoreCodec(it.mimeType) }
                        .thenBy { it.bitrate }
                )
            }

            AudioQualityFormat.Low -> {
                val cappedFormats = audioCapableFormats.filter { it.bitrate <= 128000 }
                val lowFormat = cappedFormats
                    .filter { it.isOriginal }
                    .maxByOrNull { it.bitrate }
                    ?: cappedFormats.maxByOrNull { it.bitrate }
                    ?: audioCapableFormats
                        .filter { it.isOriginal }
                        .minByOrNull { kotlin.math.abs(it.bitrate.toDouble() - 128000.0) }
                    ?: audioCapableFormats.maxByOrNull { it.bitrate }

                if (lowFormat != null) {
                    logger.d("Selected LOW format: itag=${lowFormat.itag}, bitrate: ${lowFormat.bitrate}")
                }

                lowFormat
            }

            AudioQualityFormat.Auto -> {
                val targetBitrate = if (connectivityManager.isActiveNetworkMetered) 128000.0 else maxBitrate.toDouble()
                val cappedFormats = audioCapableFormats.filter { it.bitrate <= targetBitrate }
                val autoFormat = cappedFormats
                    .filter { it.isOriginal }
                    .maxByOrNull { it.bitrate }
                    ?: cappedFormats.maxByOrNull { it.bitrate }
                    ?: audioCapableFormats
                        .filter { it.isOriginal }
                        .minByOrNull { kotlin.math.abs(it.bitrate - targetBitrate) }
                    ?: audioCapableFormats.maxByOrNull { it.bitrate }

                if (autoFormat != null) {
                    logger.d("Selected AUTO format: itag=${autoFormat.itag}, bitrate: ${autoFormat.bitrate}")
                }

                autoFormat
            }
        }

        if (format != null) {
            logger.d("Selected format: itag=${format.itag}, mimeType=${format.mimeType}, bitrate=${format.bitrate}, audioQuality label: ${format.audioQuality}")
        } else {
            logger.d("No suitable audio format found")
        }

        return format
    }
    /**
     * Checks if the stream url returns a successful status.
     * If this returns true the url is likely to work.
     * If this returns false the url might cause an error during playback.
     */
    private fun validateStatus(url: String): Boolean {
        logger.d("Validating stream URL status")
        try {
            val requestBuilder = okhttp3.Request.Builder()
                .head()
                .url(url)

            // Add authentication cookie for privately owned tracks
            YouTube.cookie?.let { cookie ->
                requestBuilder.addHeader("Cookie", cookie)
                println("[PLAYBACK_DEBUG] Added cookie to validation request")
            }

            val response = httpClient.newCall(requestBuilder.build()).execute()
            val isSuccessful = response.isSuccessful
            logger.d("Stream URL validation result: ${if (isSuccessful) "Success" else "Failed"} (${response.code})")
            return isSuccessful
        } catch (e: Exception) {
            logger.e("Stream URL validation failed with exception", e)
        }
        return false
    }
    data class SignatureTimestampResult(
        val timestamp: Int?,
        val isAgeRestricted: Boolean
    )

    private suspend fun getSignatureTimestampOrNull(videoId: String): SignatureTimestampResult {
        logger.d("Getting signature timestamp for videoId: $videoId")

        // Prefer the STS of the player the cipher actually deciphers with. The STS decides which
        // player generation YouTube mints the signatureCipher for; during A/B rollouts NewPipe's
        // independently fetched player can be a DIFFERENT generation, and a sig minted for one
        // player but deciphered by another 403s on the CDN. NewPipe is kept for age-restriction
        // detection and as the STS source only when the cipher player fetch fails.
        val cipherSts = try {
            CipherDeobfuscator.signatureTimestamp()
                ?.also { logger.d("Signature timestamp from cipher player: $it") }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // cooperative cancellation: don't swallow, let the playback coroutine unwind
        } catch (e: Exception) {
            logger.e("Cipher player STS fetch failed", e)
            null
        }

        val result = NewPipeExtractor.getSignatureTimestamp(videoId)
        return result.fold(
            onSuccess = { timestamp ->
                val chosen = cipherSts ?: timestamp
                logger.d("Signature timestamp resolved: cipher=$cipherSts newpipe=$timestamp -> using $chosen")
                SignatureTimestampResult(chosen, isAgeRestricted = false)
            },
            onFailure = { error ->
                val isAgeRestricted = error.message?.contains("age-restricted", ignoreCase = true) == true ||
                    error.cause?.message?.contains("age-restricted", ignoreCase = true) == true
                when {
                    isAgeRestricted -> {
                        logger.d("Age-restricted content detected from NewPipe")
                        logger.i("Age-restricted detected early via NewPipe: videoId=$videoId")
                    }
                    cipherSts != null -> {
                        // Non-fatal: the cipher player's STS already covers us, so NewPipe is just
                        // a fallback here — don't report its failure as an exception (avoids noise).
                        logger.w("NewPipe STS unavailable, using cipher player STS: ${error.message}")
                    }
                    else -> {
                        logger.e("Failed to get signature timestamp via NewPipe", error)
                    }
                }
                // The cipher player's STS is exactly the one the cipher will decipher with.
                logger.d("Signature timestamp resolved: cipher=$cipherSts (NewPipe failed)")
                SignatureTimestampResult(cipherSts, isAgeRestricted)
            }
        )
    }

    private suspend fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        playerResponse: PlayerResponse,
        skipNewPipe: Boolean = false
    ): String? {
        logger.d("Finding stream URL for format: ${format.mimeType}, videoId: $videoId, skipNewPipe: $skipNewPipe")

        // First check if format already has a URL
        if (!format.url.isNullOrEmpty()) {
            logger.d("Using URL from format directly")
            return format.url
        }

        // Try custom cipher deobfuscation for signatureCipher formats
        val signatureCipher = format.signatureCipher ?: format.cipher
        if (!signatureCipher.isNullOrEmpty()) {
            logger.d("Format has signatureCipher, using custom deobfuscation")
            val customDeobfuscatedUrl = CipherDeobfuscator.deobfuscateStreamUrl(signatureCipher, videoId)
            if (customDeobfuscatedUrl != null) {
                logger.d("Stream URL obtained via custom cipher deobfuscation")
                return customDeobfuscatedUrl
            }
            logger.d("Custom cipher deobfuscation failed")
        }

        // Always try NewPipe signature deobfuscation - it doesn't need auth,
        // it just applies the cipher algorithm from player.js.
        // This is critical for privately-owned tracks where skipNewPipe is true.
        val deobfuscatedUrl = NewPipeExtractor.getStreamUrl(format, videoId)
        if (deobfuscatedUrl != null) {
            logger.d("Stream URL obtained via NewPipe deobfuscation")
            return deobfuscatedUrl
        }

        // Skip StreamInfo fallback for age-restricted or private content
        // (StreamInfo fetch may fail without auth for these)
        if (skipNewPipe) {
            logger.d("Skipping StreamInfo fallback for age-restricted/private content")
            return null
        }

        // Fallback: try to get URL from StreamInfo
        logger.d("Trying StreamInfo fallback for URL")
        val streamUrls = YouTube.getNewPipeStreamUrls(videoId)
        if (streamUrls.isNotEmpty()) {
            val streamUrl = streamUrls.find { it.first == format.itag }?.second
            if (streamUrl != null) {
                logger.d("Stream URL obtained from StreamInfo")
                return streamUrl
            }

            // If exact itag not found, try to find any audio stream
            val audioStream = streamUrls.find { urlPair ->
                playerResponse.streamingData?.adaptiveFormats?.any {
                    it.itag == urlPair.first && it.isAudio
                } == true
            }?.second

            if (audioStream != null) {
                logger.d("Audio stream URL obtained from StreamInfo (different itag)")
                return audioStream
            }
        }

        logger.e("Failed to get stream URL")
        return null
    }

    fun forceRefreshForVideo(videoId: String) {
        logger.d("Force refreshing for videoId: $videoId")
    }
}
