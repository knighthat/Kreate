package app.kreate.internal.innertube.responses

import app.kreate.gateway.innertube.responses.Tabs
import kotlinx.serialization.Serializable


@Serializable
internal data class TabsImpl(
    override val tabs: List<TabImpl> = emptyList()
): Tabs {

    @Serializable
    internal data class TabImpl(
        override val tabRenderer: RendererImpl
    ): Tabs.Tab {

        @Serializable
        internal data class RendererImpl(
            override val endpoint: EndpointImpl?,
            override val title: String?,
            override val selected: Boolean?,
            override val content: ContentImpl?,
            override val tabIdentifier: String?
        ): Tabs.Tab.Renderer {

            @Serializable
            internal data class ContentImpl(
                override val sectionListRenderer: SectionListRendererImpl?,
                override val musicQueueRenderer: MusicQueueRendererImpl?
            ): Tabs.Tab.Renderer.Content
        }
    }
}