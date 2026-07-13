package app.kreate.internal.innertube.models

import app.kreate.gateway.innertube.PageType
import app.kreate.gateway.innertube.models.InnertubeSong
import app.kreate.gateway.innertube.responses.MusicResponsiveListItemRenderer
import app.kreate.gateway.innertube.responses.NextResponse
import app.kreate.gateway.innertube.responses.Runs
import app.kreate.gateway.innertube.responses.Tabs
import app.kreate.gateway.innertube.responses.Thumbnails
import app.kreate.internal.innertube.utils.containsExplicitBadge
import app.kreate.internal.innertube.utils.extractArtistAndAlbum
import app.kreate.internal.innertube.utils.firstText
import app.kreate.internal.innertube.utils.pageType
import app.kreate.internal.innertube.utils.toThumbnailList


/**
 * Matches:
 * - 0:12
 * - 12:23
 * - 1:02:03
 */
private val DURATION_REGEX = Regex( "^(?:(\\d+):)?(\\d{1,2}):(\\d{2})$" )

private val List<Runs.Run>.duration: String?
    get() = map( Runs.Run::text ).firstOrNull { it.matches( DURATION_REGEX ) }

/**
 * Extract duration text from both columns
 *
 * @return human-readable format of duration, empty string if non found
 */
private fun extractDuration( renderer: MusicResponsiveListItemRenderer ): String? {
    val merged = (renderer.flexColumns + renderer.fixedColumns).mapNotNull {
        it.musicResponsiveListItemFlexColumnRenderer ?: it.musicResponsiveListItemFixedColumnRenderer
    }

    return merged.mapNotNull( MusicResponsiveListItemRenderer.Colum.Renderer::text )
                 .flatMap( Runs::runs )
                 .duration
}

internal fun createInnertubeSongFrom( renderer: MusicResponsiveListItemRenderer ): InnertubeSong {
    val titleRun = renderer.flexColumns.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()
    requireNotNull( titleRun ) { "MusicResponsiveListItemRenderer doesn't have title column" }
    val id = renderer.playlistItemData?.videoId ?: titleRun.navigationEndpoint?.watchEndpoint?.videoId
    requireNotNull( id ) { "MusicResponsiveListItemRenderer doesn't contain videoId" }
    val title = titleRun.text
    // Second column is allowed to be null
    val secondColumn = renderer.flexColumns.getOrNull( 1 )?.musicResponsiveListItemFlexColumnRenderer?.text
    val album = secondColumn?.runs?.firstOrNull { it.navigationEndpoint?.pageType == PageType.ALBUM }
    val artists = secondColumn?.runs?.filter { it.navigationEndpoint?.pageType == PageType.ARTIST }.orEmpty()
    val artistsText = artists.joinToString { it.text }
    val duration = extractDuration( renderer )
    val thumbnails = renderer.thumbnail?.toThumbnailList().orEmpty()
    val isExplicit = renderer.badges.containsExplicitBadge

    return object : InnertubeSong {
        override val durationText: String? = duration
        override val album: Runs.Run? = album
        override val artists: List<Runs.Run> = artists
        override val artistsText: String = artistsText
        override val id: String = id
        override val name: String = title
        override val thumbnails: List<Thumbnails.Thumbnail> = thumbnails
        override val isExplicit: Boolean = isExplicit
        override val subtitle: Runs? = secondColumn
    }
}

internal fun createInnertubeSongsFrom( response: NextResponse ): List<InnertubeSong> {
    val contents = response.contents
        .singleColumnMusicWatchNextResultsRenderer
        ?.tabbedRenderer
        ?.watchNextTabbedResultsRenderer
        ?.tabs
        ?.firstNotNullOfOrNull( Tabs.Tab::tabRenderer )
        ?.content
        ?.musicQueueRenderer
        ?.content
        ?.playlistPanelRenderer
        ?.contents
    require( !contents.isNullOrEmpty() ) { "NextResponse doesn't have any contents" }

    return contents.map {
        val renderer = it.playlistPanelVideoRenderer
        requireNotNull( renderer ) { "PlaylistPanelRenderer.Content doesn't contain song's info" }

        val id = renderer.videoId
        val name = renderer.title.firstText
        val thumbnails = renderer.thumbnail.thumbnails
        val isExplicit = renderer.badges.containsExplicitBadge
        val (album, artists) = renderer.longBylineText.extractArtistAndAlbum()
        val artistsText = renderer.shortBylineText.firstText
        val duration = renderer.lengthText.firstText

        object : InnertubeSong {

            override val durationText: String = duration
            override val album: Runs.Run? = album
            override val artists: List<Runs.Run> = artists
            override val artistsText: String = artistsText
            override val subtitle: Runs = renderer.longBylineText
            override val id: String = id
            override val name: String = name
            override val thumbnails: List<Thumbnails.Thumbnail> = thumbnails
            override val isExplicit: Boolean = isExplicit
        }
    }
}