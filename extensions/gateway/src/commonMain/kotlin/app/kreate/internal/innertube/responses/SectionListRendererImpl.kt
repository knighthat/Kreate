package app.kreate.internal.innertube.responses

import app.kreate.gateway.innertube.responses.SectionListRenderer
import kotlinx.serialization.Serializable


@Serializable
internal data class SectionListRendererImpl(
    override val contents: List<ContentImpl> = emptyList(),
    override val continuations: List<ContinuationImpl> = emptyList()
): SectionListRenderer {

    @Serializable
    internal data class ContentImpl(
        override val musicDescriptionShelfRenderer: MusicDescriptionShelfRendererImpl?,
        override val musicTastebuilderShelfRenderer: MusicTastebuilderShelfRendererImpl?,
        override val musicResponsiveHeaderRenderer: MusicResponsiveHeaderRendererImpl?,
        override val musicEditablePlaylistDetailHeaderRenderer: MusicEditablePlaylistDetailHeaderRendererImpl?,
        override val musicShelfRenderer: MusicShelfRendererImpl?,
        override val musicCarouselShelfRenderer: MusicCarouselShelfRendererImpl?,
        override val gridRenderer: GridRendererImpl?,
        override val musicPlaylistShelfRenderer: MusicPlaylistShelfRendererImpl?,
        override val musicCardShelfRenderer: MusicCardShelfRendererImpl?
    ): SectionListRenderer.Content {

        @Serializable
        internal data class MusicDescriptionShelfRendererImpl(
            override val header: RunsImpl?,
            override val subheader: RunsImpl?,
            override val description: RunsImpl,
            override val shelfStyle: String?,
            override val maxCollapsedLines: Int?,
            override val maxExpandedLines: Int?,
            override val footer: RunsImpl?
        ): SectionListRenderer.Content.MusicDescriptionShelfRenderer

        @Serializable
        internal data class MusicTastebuilderShelfRendererImpl(
            override val thumbnail: ThumbnailImpl,
            override val primaryText: RunsImpl,
            override val secondaryText: RunsImpl,
            override val isVisible: Boolean?
        ): SectionListRenderer.Content.MusicTastebuilderShelfRenderer {

            @Serializable
            internal data class ThumbnailImpl(
                override val musicTastebuilderShelfThumbnailRenderer: ThumbnailImpl.RendererImpl
            ): SectionListRenderer.Content.MusicTastebuilderShelfRenderer.Thumbnail
        }

        @Serializable
        internal data class MusicResponsiveHeaderRendererImpl(
            override val thumbnail: ThumbnailImpl,
            override val title: RunsImpl,
            override val subtitle: RunsImpl,
            override val description: DescriptionImpl?,
            override val straplineTextOne: RunsImpl?,
            override val straplineThumbnail: ThumbnailImpl?,
            override val subtitleBadge: List<BadgeImpl> = emptyList(),
            override val secondSubtitle: RunsImpl?
        ): SectionListRenderer.Content.MusicResponsiveHeaderRenderer {

            @Serializable
            internal data class DescriptionImpl(
                override val musicDescriptionShelfRenderer: MusicDescriptionShelfRendererImpl
            ): SectionListRenderer.Content.MusicResponsiveHeaderRenderer.Description
        }

        @Serializable
        internal data class MusicEditablePlaylistDetailHeaderRendererImpl(
            override val header: HeaderImpl
        ): SectionListRenderer.Content.MusicEditablePlaylistDetailHeaderRenderer {

            @Serializable
            internal data class HeaderImpl(
                override val musicResponsiveHeaderRenderer: MusicResponsiveHeaderRendererImpl
            ): SectionListRenderer.Content.MusicEditablePlaylistDetailHeaderRenderer.Header
        }

        @Serializable
        internal data class GridRendererImpl(
            override val items: List<ItemImpl> = emptyList()
        ): SectionListRenderer.Content.GridRenderer {

            @Serializable
            internal data class ItemImpl(
                override val musicTwoRowItemRenderer: MusicTwoRowItemRendererImpl
            ): SectionListRenderer.Content.GridRenderer.Item
        }
    }
}