package app.kreate.internal.innertube.models

import app.kreate.gateway.innertube.models.InnertubeAlbum
import app.kreate.gateway.innertube.models.InnertubeSong
import app.kreate.gateway.innertube.models.Section
import app.kreate.gateway.innertube.responses.MusicResponsiveListItemRenderer
import app.kreate.gateway.innertube.responses.MusicTwoRowItemRenderer
import app.kreate.gateway.innertube.responses.Runs
import app.kreate.gateway.innertube.responses.Thumbnails
import app.kreate.internal.innertube.utils.containsExplicitBadge
import app.kreate.internal.innertube.utils.extractArtistAndAlbum
import app.kreate.internal.innertube.utils.firstText
import app.kreate.internal.innertube.utils.toThumbnailList


private val YEAR_REGEX = Regex("\\d{4}")

private val Runs.year: Int
    get() = runs.firstOrNull { it.text.matches(YEAR_REGEX) }?.text?.toInt() ?: -1

internal fun createInnertubeAlbumFrom( renderer: MusicTwoRowItemRenderer ): InnertubeAlbum {
    val run = renderer.title.runs.firstOrNull()
    val id = run?.navigationEndpoint?.browseEndpoint?.browseId
    requireNotNull( id ) { "MusicTwoRowItemRenderer doesn't contain browseId" }
    val thumbnails = renderer.thumbnailRenderer.toThumbnailList()
    val subtitle = renderer.subtitle
    val isExplicit = renderer.subtitleBadges.containsExplicitBadge

    return object : InnertubeAlbum {
        override val artists: List<Runs.Run> = subtitle.extractArtistAndAlbum().artists
        override val year: Int = subtitle.year
        override val urlCanonical: String? = null
        override val subtitle: Runs = subtitle
        override val songs: List<InnertubeSong> = emptyList()
        override val id: String = id
        override val name: String = run.text
        override val thumbnails: List<Thumbnails.Thumbnail> = thumbnails
        override val isExplicit: Boolean = isExplicit
        override val description: String? = null
        override val sections: List<Section> = emptyList()
    }
}

internal fun createInnertubeAlbumFrom( renderer: MusicResponsiveListItemRenderer ): InnertubeAlbum {
    val id = requireNotNull(
        renderer.navigationEndpoint?.browseEndpoint?.browseId
    ) { "MusicResponsiveListItemRenderer doesn't contain browseId" }
    val firstColumn = requireNotNull(
        renderer.flexColumns.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer
    ) { "MusicResponsiveListItemRenderer.flexColumns doesn't have column for title" }
    val secondColumn = requireNotNull(
        renderer.flexColumns.getOrNull( 1 )?.musicResponsiveListItemFlexColumnRenderer
    ) { "MusicResponsiveListItemRenderer.flexColumns doesn't have column for artist(s)" }
    val name = firstColumn.text?.firstText.orEmpty()
    val thumbnails = renderer.thumbnail?.toThumbnailList().orEmpty()
    val artists = secondColumn.text?.extractArtistAndAlbum()?.artists.orEmpty()
    val year = secondColumn.text?.year ?: -1
    val isExplicit = renderer.badges.containsExplicitBadge
    val subtitle = secondColumn.text

    return object : InnertubeAlbum {
        override val artists: List<Runs.Run> = artists
        override val year: Int = year
        override val urlCanonical: String? = null
        override val subtitle: Runs? = subtitle
        override val songs: List<InnertubeSong> = emptyList()
        override val id: String = id
        override val name: String = name
        override val thumbnails: List<Thumbnails.Thumbnail> = thumbnails
        override val isExplicit: Boolean = isExplicit
        override val description: String? = null
        override val sections: List<Section> = emptyList()
    }
}