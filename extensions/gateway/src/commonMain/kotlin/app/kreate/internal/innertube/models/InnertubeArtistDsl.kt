package app.kreate.internal.innertube.models

import app.kreate.gateway.innertube.models.InnertubeArtist
import app.kreate.gateway.innertube.models.Section
import app.kreate.gateway.innertube.responses.MusicResponsiveListItemRenderer
import app.kreate.gateway.innertube.responses.MusicTwoRowItemRenderer
import app.kreate.gateway.innertube.responses.PrimaryResults
import app.kreate.gateway.innertube.responses.Runs
import app.kreate.gateway.innertube.responses.Thumbnails
import app.kreate.internal.innertube.utils.firstText
import app.kreate.internal.innertube.utils.toThumbnailList


internal fun createInnertubeArtistFrom( renderer: MusicResponsiveListItemRenderer ): InnertubeArtist {
    val id = requireNotNull(
        renderer.navigationEndpoint?.browseEndpoint?.browseId
    ) { "MusicResponsiveListItemRenderer doesn't contain browseId" }
    val name = requireNotNull(
        renderer.flexColumns.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text?.firstText.orEmpty()
    ) { "MusicResponsiveListItemRenderer doesn't have name column" }
    // Second column (subtitle) is allowed to be null
    val subtitle = renderer.flexColumns.getOrNull( 1 )?.musicResponsiveListItemFlexColumnRenderer?.text
    val thumbnails = renderer.thumbnail?.toThumbnailList().orEmpty()

    return object : InnertubeArtist {
        override val shortNumSubscribers: String? = null
        override val longNumSubscribers: String? = null
        override val shortNumMonthlyAudience: String? = null
        override val id: String = id
        override val name: String = name
        override val thumbnails: List<Thumbnails.Thumbnail> = thumbnails
        override val description: String? = null
        override val sections: List<Section> = emptyList()
        override val subtitle: Runs? = subtitle
    }
}

internal fun createInnertubeArtistFrom( renderer: PrimaryResults.Results.Content.VideoSecondaryInfoRenderer.Owner.Renderer ): InnertubeArtist {
    val id = requireNotNull(
        renderer.navigationEndpoint.browseEndpoint?.browseId
    ) { "Owner doesn't contain browseId" }
    val name = renderer.title.firstText
    val thumbnails = renderer.thumbnail.thumbnails
    val longNumSubscribers = renderer.subscriberCountText?.simpleText

    return object : InnertubeArtist {
        override val shortNumSubscribers: String? = null
        override val longNumSubscribers: String? = longNumSubscribers
        override val shortNumMonthlyAudience: String? = null
        override val subtitle: Runs? = null
        override val id: String = id
        override val name: String = name
        override val thumbnails: List<Thumbnails.Thumbnail> = thumbnails
        override val description: String? = null
        override val sections: List<Section> = emptyList()
    }
}

internal fun createInnertubeArtistFrom( renderer: MusicTwoRowItemRenderer ): InnertubeArtist {
    val run = renderer.title.runs.firstOrNull()
    requireNotNull( run ) { "MusicTwoRowItemRenderer doesn't have title" }
    val browseId = run.navigationEndpoint?.browseEndpoint?.browseId
    requireNotNull( browseId ) { "MusicTwoRowItemRenderer doesn't contain browseId" }
    val name = run.text
    val thumbnails = renderer.thumbnailRenderer.toThumbnailList()
    val subtitle = renderer.subtitle

    return object : InnertubeArtist {
        override val shortNumSubscribers: String? = null
        override val longNumSubscribers: String? = null
        override val shortNumMonthlyAudience: String = subtitle.firstText
        override val subtitle: Runs = subtitle
        override val id: String = browseId
        override val name: String = name
        override val thumbnails: List<Thumbnails.Thumbnail> = thumbnails
        override val description: String? = null
        override val sections: List<Section> = emptyList()
    }
}