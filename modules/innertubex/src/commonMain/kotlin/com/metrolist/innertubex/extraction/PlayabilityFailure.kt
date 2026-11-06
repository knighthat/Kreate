package com.metrolist.innertubex.extraction

import com.metrolist.innertubex.extraction.strategy.ClientFailureKind

internal data class PlayabilityFailure(
    val status: String?,
    val reason: String?,
    val category: ClientFailureKind = classifyPlayabilityFailure(status, reason),
)

internal data class PlayerResponseBatch(
    val playableResponses: List<ClientResult>,
    val failures: List<PlayabilityFailure>,
    val requestFailures: List<Throwable>,
    val attempts: List<StreamAttemptDiagnostic>,
)

internal fun classifyPlayabilityFailures(failures: List<PlayabilityFailure>): StreamResolveException.Reason? {
    if (failures.any(PlayabilityFailure::isAgeRestricted)) {
        return StreamResolveException.Reason.AGE_RESTRICTED
    }
    if (failures.any(PlayabilityFailure::isUnavailable)) {
        return StreamResolveException.Reason.UNAVAILABLE
    }
    return null
}

private fun PlayabilityFailure.isAgeRestricted(): Boolean {
    val normalizedStatus = status.orEmpty().uppercase()
    val normalizedReason = reason.orEmpty().lowercase()
    return normalizedStatus in setOf("AGE_CHECK_REQUIRED", "AGE_VERIFICATION_REQUIRED", "CONTENT_CHECK_REQUIRED") ||
        listOf("confirm your age", "age-restricted", "age restricted", "age verification")
            .any(normalizedReason::contains)
}

private fun classifyPlayabilityFailure(
    status: String?,
    reason: String?,
): ClientFailureKind {
    val normalizedStatus = status.orEmpty().uppercase()
    return when {
        normalizedStatus in
            setOf(
                "AGE_CHECK_REQUIRED",
                "AGE_VERIFICATION_REQUIRED",
                "CONTENT_CHECK_REQUIRED",
            )
        -> ClientFailureKind.PLAYABILITY

        normalizedStatus in setOf("UNPLAYABLE", "VIDEO_UNAVAILABLE", "LIVE_STREAM_OFFLINE") -> ClientFailureKind.PLAYABILITY

        normalizedStatus == "LOGIN_REQUIRED" -> ClientFailureKind.TOKEN

        else -> ClientFailureKind.PLAYABILITY
    }
}

private fun PlayabilityFailure.isUnavailable(): Boolean {
    val normalizedStatus = status.orEmpty().uppercase()
    val normalizedReason = reason.orEmpty().lowercase()
    return normalizedStatus in setOf("UNPLAYABLE", "VIDEO_UNAVAILABLE", "LIVE_STREAM_OFFLINE") ||
        listOf("video unavailable", "not available", "private video", "has been removed")
            .any(normalizedReason::contains)
}
