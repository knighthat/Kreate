package app.kreate.internal.innertube.models

import app.kreate.gateway.innertube.models.InnertubeVideo
import app.kreate.gateway.innertube.responses.MusicResponsiveListItemRenderer
import app.kreate.gateway.innertube.responses.Runs
import app.kreate.gateway.innertube.responses.Thumbnails


internal fun createInnertubeVideoFrom( renderer: MusicResponsiveListItemRenderer ): InnertubeVideo {
    val song = createInnertubeSongFrom( renderer )

    return object : InnertubeVideo {
        override val durationText: String? = song.durationText
        override val album: Runs.Run? = song.album
        override val artists: List<Runs.Run> = song.artists
        override val artistsText: String = song.artistsText
        override val id: String = song.id
        override val name: String = song.name
        override val thumbnails: List<Thumbnails.Thumbnail> = song.thumbnails
        override val isExplicit: Boolean = song.isExplicit
        override val subtitle: Runs? = song.subtitle
    }
}