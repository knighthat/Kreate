package app.kreate.internal.innertube.responses

import app.kreate.gateway.innertube.responses.InnertubeResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient


@Serializable
internal data class InnertubeResponseImpl(
    override val responseContext: ContextImpl
): InnertubeResponse {

    @Serializable
    internal data class ContextImpl(
        override val visitorData: String?,
        override val serviceTrackingParams: List<ServiceTrackingImpl> = emptyList()
    ): InnertubeResponse.Context {

        @Serializable
        internal data class ServiceTrackingImpl(
            @SerialName("params")
            val paramsList: List<Param> = emptyList()
        ): InnertubeResponse.Context.ServiceTracking {

            @Transient
            override val params: Map<String, String> = paramsList.associate { it.key to it.value }

            @Serializable
            internal data class Param(val key: String, val value: String)
        }
    }
}