package app.kreate.internal.innertube.responses

import app.kreate.gateway.innertube.responses.Badge
import kotlinx.serialization.Serializable


@Serializable
internal data class BadgeImpl(
    override val musicInlineBadgeRenderer: MusicInlineBadgeImpl?,
    override val metadataBadgeRenderer: MetadataBadgeImpl?
): Badge {

    @Serializable
    internal data class MusicInlineBadgeImpl(
        override val accessibilityData: AccessibilityImpl?,
        override val style: String?,
        override val tooltip: String?,
        override val icon: IconImpl?
    ): Badge.MusicInlineBadge

    @Serializable
    internal data class MetadataBadgeImpl(
        override val accessibilityData: AccessibilityImpl.DataImpl?,
        override val style: String?,
        override val tooltip: String?,
        override val icon: IconImpl?
    ): Badge.MetadataBadge
}
