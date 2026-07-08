package app.kreate.internal.innertube.responses

import app.kreate.gateway.innertube.responses.MusicMultiSelectMenuRenderer
import kotlinx.serialization.Serializable


@Serializable
internal data class MusicMultiSelectMenuRendererImpl(
    override val title: TitleImpl
) : MusicMultiSelectMenuRenderer {

    @Serializable
    internal data class TitleImpl(
        override val musicMenuTitleRenderer: RendererImpl
    ) : MusicMultiSelectMenuRenderer.Title {

        @Serializable
        internal data class RendererImpl(
            override val primaryText: RunsImpl
        ) : MusicMultiSelectMenuRenderer.Title.Renderer
    }
}