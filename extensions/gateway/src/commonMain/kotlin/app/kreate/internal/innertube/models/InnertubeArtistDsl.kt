package app.kreate.internal.innertube.models

import app.kreate.gateway.innertube.models.InnertubeArtist
import app.kreate.gateway.innertube.models.Section
import app.kreate.gateway.innertube.responses.MusicResponsiveListItemRenderer
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