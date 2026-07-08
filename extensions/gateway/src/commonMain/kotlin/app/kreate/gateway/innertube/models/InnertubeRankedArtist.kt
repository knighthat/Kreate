package app.kreate.gateway.innertube.models


interface InnertubeRankedArtist: InnertubeArtist {

    companion object {

        const val RANK_UP_ICON = "ARROW_DROP_UP"
        const val RANK_DOWN_ICON = "ARROW_DROP_DOWN"
        const val RANK_NEUTRAL = "ARROW_CHART_NEUTRAL"
    }

    val rank: String

    val iconType: String?
}