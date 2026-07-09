package app.kreate.internal.innertube.models

import app.kreate.gateway.innertube.models.InnertubeArtist
import app.kreate.gateway.innertube.models.Section
import app.kreate.gateway.innertube.responses.MusicResponsiveListItemRenderer
import app.kreate.gateway.innertube.responses.Thumbnails
import app.kreate.internal.innertube.utils.firstText
import app.kreate.internal.innertube.utils.toThumbnailList


internal fun createInnertubeArtistFrom( renderer: MusicResponsiveListItemRenderer ): InnertubeArtist {
    val id = requireNotNull(
        renderer.navigationEndpoint?.browseEndpoint?.browseId
    ) { "MusicResponsiveListItemRenderer doesn't contain browseId" }
    val columns = renderer.flexColumns.mapNotNull {
        it.musicResponsiveListItemFlexColumnRenderer?.text?.firstText
    }
    require( columns.isNotEmpty() ) { "MusicResponsiveListItemRenderer has no content" }
    val thumbnails = renderer.thumbnail?.toThumbnailList().orEmpty()

    return object : InnertubeArtist {
        override val shortNumSubscribers: String? = null
        override val longNumSubscribers: String? = null
        override val shortNumMonthlyAudience: String? = columns.getOrNull( 1 )
        override val id: String = id
        override val name: String = columns.firstOrNull().orEmpty()
        override val thumbnails: List<Thumbnails.Thumbnail> = thumbnails
        override val description: String? = null
        override val sections: List<Section> = emptyList()
    }
}