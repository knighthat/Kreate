package app.kreate.gateway.innertube.responses


interface SearchResponse : InnertubeResponse {

    val contents: Contents?
    val continuationContents: ContinuationContents?

    interface Contents {

        val tabbedSearchResultsRenderer: Tabs
    }

    interface ContinuationContents {

        val musicShelfContinuation: MusicShelfRenderer
    }
}