package app.kreate.internal.innertube.responses

import app.kreate.gateway.innertube.responses.MusicCardShelfRenderer
import kotlinx.serialization.Serializable


@Serializable
internal data class MusicCardShelfRendererImpl(
    override val thumbnail: ThumbnailImpl,
    override val title: RunsImpl,
    override val subtitle: RunsImpl,
    override val contents: List<ContentImpl> = emptyList()
): MusicCardShelfRenderer {

    @Serializable
    internal data class ContentImpl(
        override val musicResponsiveListItemRenderer: MusicResponsiveListItemRendererImpl?
    ): MusicCardShelfRenderer.Content
}