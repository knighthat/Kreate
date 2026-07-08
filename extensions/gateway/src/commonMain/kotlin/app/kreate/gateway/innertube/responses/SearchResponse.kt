package app.kreate.gateway.innertube.responses


interface SearchResponse : InnertubeResponse {

    val contents: Contents

    interface Contents {

        val tabbedSearchResultsRenderer: Tabs
    }
}