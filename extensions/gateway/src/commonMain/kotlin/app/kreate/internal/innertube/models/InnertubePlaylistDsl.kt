package app.kreate.internal.innertube.models

import app.kreate.gateway.innertube.models.InnertubePlaylist
import app.kreate.gateway.innertube.models.InnertubeSong
import app.kreate.gateway.innertube.responses.Continuation
import app.kreate.gateway.innertube.responses.MusicTwoRowItemRenderer
import app.kreate.gateway.innertube.responses.Runs
import app.kreate.gateway.innertube.responses.Thumbnails
import app.kreate.internal.innertube.utils.toThumbnailList


internal fun createInnertubePlaylistFrom( renderer: MusicTwoRowItemRenderer ): InnertubePlaylist {
    val run = renderer.title.runs.firstOrNull()
    val id = run?.navigationEndpoint?.browseEndpoint?.browseId
    requireNotNull( id ) { "MusicTwoRowItemRenderer doesn't contain browseId" }
    val thumbnails = renderer.thumbnailRenderer.toThumbnailList()

    return object : InnertubePlaylist {
        override val subtitle: Runs? = null
        override val subtitleText: String? = null
        override val songs: List<InnertubeSong> = emptyList()
        override val songContinuation: String? = null
        override val id: String = id
        override val name: String = run.text
        override val thumbnails: List<Thumbnails.Thumbnail> = thumbnails
        override val description: String? = null
        override val continuations: List<Continuation> = emptyList()
        override val visitorData: String? = null
    }
}