package app.kreate.internal.innertube.responses

import app.kreate.gateway.innertube.responses.Accessibility
import kotlinx.serialization.Serializable


@Serializable
internal data class AccessibilityImpl(
    override val accessibilityData: DataImpl
): Accessibility {

    @Serializable
    internal data class DataImpl(
        override val label: String
    ): Accessibility.Data
}