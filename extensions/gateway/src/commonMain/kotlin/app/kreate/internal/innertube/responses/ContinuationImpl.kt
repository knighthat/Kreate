package app.kreate.internal.innertube.responses

import app.kreate.gateway.innertube.responses.Continuation
import kotlinx.serialization.Serializable


@Serializable
internal data class ContinuationImpl(
    override val nextContinuationData: DataImpl?,
    override val nextRadioContinuationData: DataImpl?
): Continuation {

    @Serializable
    internal data class DataImpl(
        override val continuation: String
    ): Continuation.Data
}
