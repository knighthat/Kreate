package app.kreate.internal.player

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import java.io.IOException


@UnstableApi
internal class ErrorHandlingPolicy : DefaultLoadErrorHandlingPolicy(){

    // TODO: Inspect error to determine eligibility
    override fun isEligibleForFallback( exception: IOException): Boolean = true
}