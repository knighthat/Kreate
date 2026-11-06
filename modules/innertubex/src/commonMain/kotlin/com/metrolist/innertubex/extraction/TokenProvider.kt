package com.metrolist.innertubex.extraction

import com.metrolist.innertubex.extraction.strategy.PoTokenProviderKind
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

internal val TV_BEARER_MINIMUM_LIFETIME = 8.seconds

public data class TokenProviderCapabilities(
    val providers: Set<PoTokenProviderKind> = emptySet(),
    val usesWebView: Boolean = false,
)

/** Implementations handle sensitive cookies and attestation tokens; never log their values. */
public interface TokenProvider {
    public val capabilities: TokenProviderCapabilities
        get() = TokenProviderCapabilities()

    /** Cookie and returned token values are sensitive and must not be logged. */
    public suspend fun getPoToken(
        videoId: String,
        visitorData: String,
        cookie: String? = null,
    ): PoTokenResult?

    public suspend fun prewarm(cookie: String? = null) {}

    public suspend fun invalidateAttestation() {}

    public suspend fun close() {}
}

internal object UnavailableTokenProvider : TokenProvider {
    override suspend fun getPoToken(
        videoId: String,
        visitorData: String,
        cookie: String?,
    ): PoTokenResult? = null
}

/**
 * A host-acquired, short-lived bearer credential scoped to one existing TV player profile.
 * The host owns consent, secure storage, refresh, and revocation.
 */
public class TvBearerCredential(
    value: String,
    public val profileId: String,
    public val expiresAt: Instant,
    public val sessionGeneration: Long,
    public val visitorData: String? = null,
) {
    private val bearerValue = value

    init {
        require(value.length in 1..MAX_BEARER_LENGTH && value.all { it.code in 0x21..0x7e }) {
            "TV bearer credential must be a bounded header-safe value"
        }
        require(profileId in APPROVED_PROFILE_IDS) { "Invalid TV bearer profile ID" }
        require(sessionGeneration >= 0L) { "TV bearer session generation must be non-negative" }
        require(
            visitorData?.let { it.length <= MAX_VISITOR_DATA_LENGTH && it.all { character -> character.code in 0x21..0x7e } } != false,
        ) {
            "TV bearer visitor data is not header-safe"
        }
    }

    override fun toString(): String =
        "TvBearerCredential(" +
            "value=present, " +
            "profileId=$profileId, " +
            "expiresAt=$expiresAt, " +
            "sessionGeneration=$sessionGeneration, " +
            "visitorData=${visitorData.presence()})"

    internal fun bearerValue(): String = bearerValue

    internal fun isUsableFor(
        generation: Long,
        minimumLifetime: Duration = Duration.ZERO,
        now: Instant = Clock.System.now(),
    ): Boolean = sessionGeneration == generation && expiresAt > now + minimumLifetime

    private fun String?.presence(): String = if (isNullOrBlank()) "missing" else "present"

    private companion object {
        private const val MAX_BEARER_LENGTH = 4096
        private const val MAX_VISITOR_DATA_LENGTH = 4096
        private val APPROVED_PROFILE_IDS = setOf("TVHTML5", "TVHTML5_DOWNGRADED")
    }
}

/** Called for each explicit TV discovery request; implementations must not return stale account credentials. */
public interface TvBearerCredentialProvider {
    public suspend fun getCredential(
        videoId: String,
        sessionGeneration: Long,
    ): TvBearerCredential?

    /** Return false when host-owned logout, revocation, or account replacement invalidated [credential]. */
    public suspend fun isCredentialCurrent(credential: TvBearerCredential): Boolean
}

public data class PoTokenResult(
    val playerRequestToken: String,
    val streamingDataToken: String,
    val visitorData: String,
) {
    override fun toString(): String =
        "PoTokenResult(" +
            "playerRequestToken=${playerRequestToken.presence()}, " +
            "streamingDataToken=${streamingDataToken.presence()}, " +
            "visitorData=${visitorData.presence()})"

    private fun String.presence(): String = if (isBlank()) "missing" else "present"
}
