package app.kreate.internal.innertube.responses

import app.kreate.gateway.innertube.responses.MusicQueueRenderer
import kotlinx.serialization.Serializable


@Serializable
internal data class MusicQueueRendererImpl(
    override val content: ContentImpl?
): MusicQueueRenderer {

    @Serializable
    internal data class ContentImpl(
        override val playlistPanelRenderer: PlaylistPanelRendererImpl
    ) : MusicQueueRenderer.Content
}
