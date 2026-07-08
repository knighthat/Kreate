package app.kreate.internal.innertube.responses

import app.kreate.gateway.innertube.responses.SearchSuggestionsResponse
import kotlinx.serialization.Serializable


@Serializable
internal data class SearchSuggestionsResponseImpl(
    override val contents: List<ContentImpl> = emptyList(),
    override val responseContext: InnertubeResponseImpl.ContextImpl
): SearchSuggestionsResponse {

    @Serializable
    internal data class ContentImpl(
        override val searchSuggestionsSectionRenderer: RendererImpl
    ): SearchSuggestionsResponse.Content {

        @Serializable
        internal data class RendererImpl(
            override val contents: List<ContentImpl> = emptyList()
        ): SearchSuggestionsResponse.Content.Renderer {

            @Serializable
            internal data class ContentImpl(
                override val musicResponsiveListItemRenderer: MusicResponsiveListItemRendererImpl?,
                override val searchSuggestionRenderer: RendererImpl?
            ): SearchSuggestionsResponse.Content.Renderer.Content {

                @Serializable
                internal data class RendererImpl(
                    override val suggestion: RunsImpl,
                    override val navigationEndpoint: EndpointImpl
                ): SearchSuggestionsResponse.Content.Renderer.Content.Renderer
            }
        }
    }
}