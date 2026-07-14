package app.kreate.internal.innertube.models

import app.kreate.gateway.innertube.models.InnertubePodcast
import app.kreate.gateway.innertube.models.InnertubeSong
import app.kreate.gateway.innertube.responses.Continuation
import app.kreate.gateway.innertube.responses.MusicResponsiveListItemRenderer
import app.kreate.gateway.innertube.responses.Runs
import app.kreate.gateway.innertube.responses.Thumbnails
import app.kreate.internal.innertube.utils.firstText
import app.kreate.internal.innertube.utils.toThumbnailList


internal fun createInnertubePodcastFrom( renderer: MusicResponsiveListItemRenderer ): InnertubePodcast {
    val id = renderer.navigationEndpoint?.browseEndpoint?.browseId
    requireNotNull( id ) { "MusicResponsiveListItemRenderer doesn't contain browseId" }
    val thumbnails = renderer.thumbnail?.toThumbnailList().orEmpty()
    val name = renderer.flexColumns.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text?.firstText
    requireNotNull( name ) { "MusicResponsiveListItemRenderer doesn't have a name" }
    val subtitle = renderer.flexColumns.getOrNull( 1 )?.musicResponsiveListItemFlexColumnRenderer?.text

    return object : InnertubePodcast {

        override val subtitle: Runs? = subtitle
        override val subtitleText: String? = subtitle?.joinToString( "" )
        override val songs: List<InnertubeSong> = emptyList()
        override val songContinuation: String? = null
        override val id: String = id
        override val name: String = name
        override val thumbnails: List<Thumbnails.Thumbnail> = thumbnails
        override val description: String? = null
        override val continuations: List<Continuation> = emptyList()
        override val visitorData: String? = null
    }
}