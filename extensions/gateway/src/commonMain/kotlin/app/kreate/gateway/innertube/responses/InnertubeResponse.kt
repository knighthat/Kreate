package app.kreate.gateway.innertube.responses


interface InnertubeResponse {

    val responseContext: Context

    interface Context {

        /**
         * Some request in certain context requires this value
         * to be passed along.
         *
         * For example, when fetching for remaining songs of
         * an online playlist, [Client.Context] must contain
         * this [visitorData] with `continuation` string in
         * order to get the correct list.
         *
         * Otherwise, all YouTube returns are songs belong
         * to different playlists.
         */
        val visitorData: String?
        val serviceTrackingParams: List<ServiceTracking>

        interface ServiceTracking {

            val params: Map<String, String>
        }
    }
}