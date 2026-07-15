package app.kreate.internal.innertube.models

import app.kreate.gateway.innertube.models.ContinuedPlaylist
import app.kreate.gateway.innertube.models.InnertubePodcast
import app.kreate.gateway.innertube.models.InnertubeSong
import app.kreate.gateway.innertube.responses.MusicPlaylistShelfRenderer
import app.kreate.gateway.innertube.responses.MusicShelfRenderer
import app.kreate.gateway.innertube.responses.Runs
import app.kreate.gateway.innertube.responses.Thumbnails
import app.kreate.internal.innertube.utils.firstText
import app.kreate.internal.innertube.utils.toThumbnailList


internal fun createContinuedPlaylistFrom( items: List<MusicPlaylistShelfRenderer.Content> ): ContinuedPlaylist {
    var continuation: String? = null
    val songs = ArrayList<InnertubeSong>(items.size)

    for( item in items ) {
        item.continuationItemRenderer
            ?.continuationEndpoint
            ?.continuationCommand
            ?.token
            ?.also { continuation = it }

        item.musicResponsiveListItemRenderer
            ?.let( ::createInnertubeSongFrom )
            ?.also( songs::add )
    }

    val immutableSongs = songs.toList()
    return object : ContinuedPlaylist {

        override val continuation: String? = continuation
        override val songs: List<InnertubeSong> = immutableSongs
    }
}
internal fun createContinuedPlaylistFrom( renderer: MusicShelfRenderer ): ContinuedPlaylist {
    val continuation = renderer.contents.find { it.continuations.isNotEmpty() }?.continuations?.firstOrNull()?.nextContinuationData?.continuation
    val contents = renderer.contents
        .mapNotNull { it.musicMultiRowListItemRenderer }
        .map { renderer ->
            val id = renderer.onTap.watchEndpoint?.videoId
            requireNotNull( id ) { "MusicMultiRowListItemRenderer doesn't have videoId" }
            val thumbnails = renderer.thumbnail.toThumbnailList()
            val name = renderer.title.firstText
            val subtitle = renderer.subtitle + renderer.playbackProgress.musicPlaybackProgressRenderer.playbackProgressText
            val description = renderer.description

            object : InnertubePodcast.Item {
                override val durationText: String? = null
                override val album: Runs.Run? = null
                override val artists: List<Runs.Run> = emptyList()
                override val artistsText: String = ""
                override val subtitle: Runs = subtitle
                override val id: String = id
                override val name: String = name
                override val thumbnails: List<Thumbnails.Thumbnail> = thumbnails
                override val isExplicit: Boolean = false
                override val description: Runs = description
            }
        }

    return object : ContinuedPlaylist {

        override val continuation: String? = continuation
        override val songs: List<InnertubeSong> = contents
    }
}