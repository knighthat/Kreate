package app.kreate.internal.innertube.responses

import app.kreate.gateway.innertube.responses.MusicShelfRenderer
import kotlinx.serialization.Serializable


@Serializable
internal data class MusicShelfRendererImpl(
    override val title: RunsImpl?,
    override val contents: List<ContentImpl> = emptyList(),
    override val bottomText: RunsImpl?,
    override val bottomEndpoint: EndpointImpl?,
    override val contentsMultiSelectable: Boolean?,
    override val continuations: List<ContinuationImpl> = emptyList()
): MusicShelfRenderer {

    @Serializable
    internal data class ContentImpl(
        override val musicResponsiveListItemRenderer: MusicResponsiveListItemRendererImpl?
    ): MusicShelfRenderer.Content
}