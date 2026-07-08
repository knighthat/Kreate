package app.kreate.gateway.innertube.responses


interface NextResponse : InnertubeResponse {

    val contents: Contents
    val currentVideoEndpoint: Endpoint

    interface Contents {

        val singleColumnMusicWatchNextResultsRenderer: SingleColumnMusicWatchNextResultsRenderer?
        val twoColumnWatchNextResults: TwoColumnWatchNextResults?

        interface SingleColumnMusicWatchNextResultsRenderer {

            val tabbedRenderer: TabbedRenderer

            interface TabbedRenderer {

                val watchNextTabbedResultsRenderer: Tabs
            }
        }

        interface TwoColumnWatchNextResults {

            val results: PrimaryResults
        }
    }
}