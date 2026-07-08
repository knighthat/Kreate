package app.kreate.internal.innertube.responses

import app.kreate.gateway.innertube.responses.PlaylistPanelRenderer
import kotlinx.serialization.Serializable


@Serializable
internal data class PlaylistPanelRendererImpl(
    override val contents: List<ContentImpl> = emptyList(),
    override val playlistId: String?,
    override val isInfinite: Boolean,
    override val continuations: List<ContinuationImpl> = emptyList(),
    override val numItemsToShow: Short?
): PlaylistPanelRenderer {

    @Serializable
    internal data class ContentImpl(
        override val playlistPanelVideoRenderer: VideoRendererImpl?
    ): PlaylistPanelRenderer.Content {

        @Serializable
        internal data class VideoRendererImpl(
            override val title: RunsImpl,
            override val longBylineText: RunsImpl,
            override val thumbnail: ThumbnailsImpl,
            override val lengthText: RunsImpl,
            override val selected: Boolean,
            override val navigationEndpoint: EndpointImpl,
            override val videoId: String,
            override val shortBylineText: RunsImpl,
            override val badges: List<BadgeImpl> = emptyList(),
            override val playlistSetVideoId: String?,
            override val canReorder: Boolean,
            override val queueNavigationEndpoint: EndpointImpl?
        ): PlaylistPanelRenderer.Content.VideoRenderer
    }
}