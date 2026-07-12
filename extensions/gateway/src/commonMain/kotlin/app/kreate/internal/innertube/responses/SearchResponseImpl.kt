package app.kreate.internal.innertube.responses

import app.kreate.gateway.innertube.responses.SearchResponse
import kotlinx.serialization.Serializable


@Serializable
internal data class SearchResponseImpl(
    override val responseContext: InnertubeResponseImpl.ContextImpl,
    override val contents: ContentsImpl?,
    override val continuationContents: ContinuationContentsImpl?
): SearchResponse {

    @Serializable
    internal data class ContentsImpl(
        override val tabbedSearchResultsRenderer: TabsImpl
    ): SearchResponse.Contents

    @Serializable
    internal data class ContinuationContentsImpl(
        override val musicShelfContinuation: MusicShelfRendererImpl
    ) : SearchResponse.ContinuationContents
}