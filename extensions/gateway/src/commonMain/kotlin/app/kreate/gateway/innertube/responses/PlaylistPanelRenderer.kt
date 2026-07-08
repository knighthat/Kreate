package app.kreate.gateway.innertube.responses


interface PlaylistPanelRenderer {

    val contents: List<Content>
    val playlistId: String?
    val isInfinite: Boolean
    val continuations: List<Continuation>
    val numItemsToShow: Short?

    interface Content {

        val playlistPanelVideoRenderer: VideoRenderer?

        interface VideoRenderer {

            val title: Runs
            val longBylineText: Runs
            val thumbnail: Thumbnails
            val lengthText: Runs
            val selected: Boolean
            val navigationEndpoint: Endpoint
            val videoId: String
            val shortBylineText: Runs
            val badges: List<Badge>
            val playlistSetVideoId: String?
            val canReorder: Boolean
            val queueNavigationEndpoint: Endpoint?
        }
    }
}