package app.kreate.internal.innertube.responses

import app.kreate.gateway.innertube.responses.MusicCarouselShelfRenderer
import kotlinx.serialization.Serializable


@Serializable
internal data class MusicCarouselShelfRendererImpl(
    override val header: HeaderImpl,
    override val contents: List<ContentImpl> = emptyList(),
    override val itemSize: String,
    override val numItemsPerColumn: String?
): MusicCarouselShelfRenderer {

    @Serializable
    internal data class HeaderImpl(
        override val musicCarouselShelfBasicHeaderRenderer: MusicCarouselShelfBasicHeaderRendererImpl
    ): MusicCarouselShelfRenderer.Header {

        @Serializable
        internal data class MusicCarouselShelfBasicHeaderRendererImpl(
            override val title: RunsImpl,
            override val strapline: RunsImpl?,
            override val accessibilityData: AccessibilityImpl?,
            override val headerStyle: String
        ): MusicCarouselShelfRenderer.Header.MusicCarouselShelfBasicHeaderRenderer
    }

    @Serializable
    internal data class ContentImpl(
        override val musicResponsiveListItemRenderer: MusicResponsiveListItemRendererImpl?,
        override val musicTwoRowItemRenderer: MusicTwoRowItemRendererImpl?,
        override val musicNavigationButtonRenderer: MusicNavigationButtonRendererImpl?
    ): MusicCarouselShelfRenderer.Content {

        @Serializable
        internal data class MusicNavigationButtonRendererImpl(
            override val buttonText: RunsImpl,
            override val solid: SolidImpl,
            override val clickCommand: EndpointImpl
        ) : MusicCarouselShelfRenderer.Content.MusicNavigationButtonRenderer {

            @Serializable
            internal data class SolidImpl(
                override val leftStripeColor: Long
            ) : MusicCarouselShelfRenderer.Content.MusicNavigationButtonRenderer.Solid
        }
    }
}