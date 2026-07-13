package app.kreate.internal.innertube.responses

import app.kreate.gateway.innertube.responses.BrowseResponse
import kotlinx.serialization.Serializable


@Serializable
internal data class BrowseResponseImpl(
    override val contents: ContentsImpl?,
    override val header: HeaderImpl?,
    override val maxAgeStoreSeconds: Int?,
    override val microformat: MicroformatImpl?,
    override val background: ThumbnailImpl?,
    override val onResponseReceivedActions: List<ResponseReceivedActionImpl> = emptyList(),
    override val responseContext: InnertubeResponseImpl.ContextImpl,
    override val continuationContents: ContinuationContentsImpl?
): BrowseResponse {

    @Serializable
    internal data class ContentsImpl(
        override val singleColumnBrowseResultsRenderer: TabsImpl?,
        override val twoColumnBrowseResultsRenderer: TwoColumnBrowseResultsRendererImpl?,
        override val sectionListRenderer: SectionListRendererImpl?
    ): BrowseResponse.Contents {

        @Serializable
        internal data class TwoColumnBrowseResultsRendererImpl(
            override val tabs: List<TabsImpl.TabImpl> = emptyList(),
            override val secondaryContents: SecondaryContentsImpl?
        ): BrowseResponse.Contents.TwoColumnBrowseResultsRenderer {

            @Serializable
            internal data class SecondaryContentsImpl(
                override val sectionListRenderer: SectionListRendererImpl
            ): BrowseResponse.Contents.TwoColumnBrowseResultsRenderer.SecondaryContents
        }
    }

    @Serializable
    internal data class HeaderImpl(
        override val musicImmersiveHeaderRenderer: MusicImmersiveHeaderRendererImpl?,
        override val musicHeaderRenderer: MusicHeaderRendererImpl?,
        override val musicVisualHeaderRenderer: MusicVisualHeaderRendererImpl?
    ): BrowseResponse.Header {

        @Serializable
        internal data class MusicImmersiveHeaderRendererImpl(
            override val title: RunsImpl,
            override val description: RunsImpl?,
            override val thumbnail: ThumbnailImpl,
            override val shareEndpoint: EndpointImpl?,
            override val monthlyListenerCount: RunsImpl?,
            override val subscriptionButton: ButtonImpl
        ): BrowseResponse.Header.MusicImmersiveHeaderRenderer

        @Serializable
        internal data class MusicHeaderRendererImpl(
            override val title: RunsImpl?
        ): BrowseResponse.Header.MusicHeaderRenderer

        @Serializable
        internal data class MusicVisualHeaderRendererImpl(
            override val title: RunsImpl,
            override val thumbnail: ThumbnailImpl,
            override val foregroundThumbnail: ThumbnailImpl
        ) : BrowseResponse.Header.MusicVisualHeaderRenderer
    }

    @Serializable
    internal data class ResponseReceivedActionImpl(
        override val appendContinuationItemsAction: AppendContinuationItemsActionImpl
    ): BrowseResponse.ResponseReceivedAction {

        @Serializable
        internal data class AppendContinuationItemsActionImpl(
            override val continuationItems: List<MusicPlaylistShelfRendererImpl.ContentImpl> = emptyList()
        ): BrowseResponse.ResponseReceivedAction.AppendContinuationItemsAction
    }

    @Serializable
    internal data class ContinuationContentsImpl(
        override val sectionListContinuation: SectionListRendererImpl?,
        override val musicShelfContinuation: MusicShelfRendererImpl?
    ) : BrowseResponse.ContinuationContents
}