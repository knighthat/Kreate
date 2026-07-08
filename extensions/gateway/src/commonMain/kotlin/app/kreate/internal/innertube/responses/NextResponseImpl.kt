package app.kreate.internal.innertube.responses

import app.kreate.gateway.innertube.responses.NextResponse
import kotlinx.serialization.Serializable


@Serializable
internal class NextResponseImpl(
    override val contents: ContentsImpl,
    override val currentVideoEndpoint: EndpointImpl,
    override val responseContext: InnertubeResponseImpl.ContextImpl
): NextResponse {

    @Serializable
    internal data class ContentsImpl(
        override val singleColumnMusicWatchNextResultsRenderer: SingleColumnMusicWatchNextResultsRendererImpl?,
        override val twoColumnWatchNextResults: TwoColumnWatchNextResultsImpl?
    ): NextResponse.Contents {

        @Serializable
        internal data class SingleColumnMusicWatchNextResultsRendererImpl(
            override val tabbedRenderer: TabbedRendererImpl
        ): NextResponse.Contents.SingleColumnMusicWatchNextResultsRenderer {

            @Serializable
            internal data class TabbedRendererImpl(
                override val watchNextTabbedResultsRenderer: TabsImpl
            ): NextResponse.Contents.SingleColumnMusicWatchNextResultsRenderer.TabbedRenderer
        }

        @Serializable
        internal data class TwoColumnWatchNextResultsImpl(
            override val results: PrimaryResultsImpl
        ): NextResponse.Contents.TwoColumnWatchNextResults
    }
}