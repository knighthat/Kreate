package app.kreate.gateway.innertube.responses


interface MusicTwoRowItemRenderer {

    val thumbnailRenderer: Thumbnail
    val aspectRatio: String
    val title: Runs
    val subtitle: Runs
    val navigationEndpoint: Endpoint
    val subtitleBadges: List<Badge>
}