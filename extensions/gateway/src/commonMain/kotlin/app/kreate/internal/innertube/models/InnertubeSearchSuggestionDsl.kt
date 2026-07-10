package app.kreate.internal.innertube.models

import app.kreate.gateway.innertube.PageType
import app.kreate.gateway.innertube.models.InnertubeAlbum
import app.kreate.gateway.innertube.models.InnertubeArtist
import app.kreate.gateway.innertube.models.InnertubeItem
import app.kreate.gateway.innertube.models.InnertubePlaylist
import app.kreate.gateway.innertube.models.InnertubeSearchSuggestion
import app.kreate.gateway.innertube.models.InnertubeSong
import app.kreate.gateway.innertube.responses.MusicResponsiveListItemRenderer
import app.kreate.gateway.innertube.responses.Runs
import app.kreate.gateway.innertube.responses.Thumbnails
import app.kreate.internal.innertube.utils.containsExplicitBadge
import app.kreate.internal.innertube.utils.firstText
import app.kreate.internal.innertube.utils.pageType
import app.kreate.internal.innertube.utils.toThumbnailList
import kotlin.reflect.KClass


private fun extractPageType( renderer: MusicResponsiveListItemRenderer ): KClass<out InnertubeItem> {
    if( renderer.navigationEndpoint?.watchEndpoint != null
        || renderer.playlistItemData?.videoId != null )
        return InnertubeSong::class

    return when( renderer.navigationEndpoint.pageType ) {
        PageType.ARTIST     -> InnertubeArtist::class
        PageType.ALBUM      -> InnertubeAlbum::class
        PageType.PLAYLIST   -> InnertubePlaylist::class
        // Ignore items with unknown page type (have no parser for it)
        else                -> InnertubeItem::class
    }
}

internal fun createInnertubeSearchSuggestionItemFrom( renderer: MusicResponsiveListItemRenderer ): InnertubeSearchSuggestion.Item? {
    val type = extractPageType( renderer )
    val id = when( type ) {
        InnertubeSong::class -> renderer.navigationEndpoint?.watchEndpoint?.videoId ?: renderer.playlistItemData?.videoId
        InnertubeAlbum::class,
        InnertubeArtist::class,
        InnertubePlaylist::class -> renderer.navigationEndpoint?.browseEndpoint?.browseId
        else -> return null
    }
    requireNotNull( id ) { "MusicResponsiveListItemRenderer doesn't contain id" }
    val firstColumn = requireNotNull(
        renderer.flexColumns.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer
    ) { "MusicResponsiveListItemRenderer.flexColumns doesn't have column for title" }
    val secondColumn = requireNotNull(
        renderer.flexColumns.getOrNull( 1 )?.musicResponsiveListItemFlexColumnRenderer
    ) { "MusicResponsiveListItemRenderer.flexColumns doesn't have column for subtitle" }
    val name = firstColumn.text?.firstText.orEmpty()
    val thumbnails = renderer.thumbnail?.toThumbnailList().orEmpty()
    val isExplicit = renderer.badges.containsExplicitBadge
    val subtitle = secondColumn.text

    return object : InnertubeSearchSuggestion.Item {
        override val subtitle: Runs? = subtitle
        override val id: String = id
        override val name: String = name
        override val thumbnails: List<Thumbnails.Thumbnail> = thumbnails
        override val isExplicit: Boolean = isExplicit
        override val type: KClass<out InnertubeItem> = type
    }
}