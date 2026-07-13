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
    override val subheaders: List<SubheaderImpl> = emptyList(),
    override val continuations: List<ContinuationImpl> = emptyList()
): MusicShelfRenderer {

    @Serializable
    internal data class ContentImpl(
        override val musicResponsiveListItemRenderer: MusicResponsiveListItemRendererImpl?
    ): MusicShelfRenderer.Content

    @Serializable
    internal data class SubheaderImpl(
        override val musicSideAlignedItemRenderer: RendererImpl
    ) : MusicShelfRenderer.Subheader {

        @Serializable
        internal data class RendererImpl(
            override val startItems: List<ItemImpl>
        ) : MusicShelfRenderer.Subheader.Renderer {

            @Serializable
            internal data class ItemImpl(
                override val musicSortFilterButtonRenderer: MusicSortFilterButtonRendererImpl
            ) : MusicShelfRenderer.Subheader.Renderer.Item {

                @Serializable
                internal data class MusicSortFilterButtonRendererImpl(
                    override val title: RunsImpl,
                    override val menu: MenuImpl,
                    override val accessibility: AccessibilityImpl
                ) : MusicShelfRenderer.Subheader.Renderer.Item.MusicSortFilterButtonRenderer {

                    @Serializable
                    internal data class MenuImpl(
                        override val musicMultiSelectMenuRenderer: MusicMultiSelectMenuRendererImpl
                    ) : MusicShelfRenderer.Subheader.Renderer.Item.MusicSortFilterButtonRenderer.Menu
                }
            }
        }
    }
}