package app.kreate.internal.innertube.models

import app.kreate.gateway.innertube.PageType
import app.kreate.gateway.innertube.models.InnertubeItem
import app.kreate.gateway.innertube.models.Section
import app.kreate.gateway.innertube.responses.MusicCarouselShelfRenderer
import app.kreate.gateway.innertube.responses.MusicShelfRenderer
import app.kreate.internal.innertube.utils.firstText
import app.kreate.internal.innertube.utils.pageType


internal fun createSectionFrom( renderer: MusicCarouselShelfRenderer ): Section {
    val header = renderer.header.musicCarouselShelfBasicHeaderRenderer
    val accessibilityLabel = header.accessibilityData?.accessibilityData?.label
    val title = header.title.firstText
    val browseEndpoint = header.title.runs.firstOrNull()?.navigationEndpoint?.browseEndpoint
    val browseId = browseEndpoint?.browseId
    val params = browseEndpoint?.params
    //<editor-fold defaultstate="collapsed" desc="Contents">
    val mutableContents = mutableListOf<InnertubeItem>()
    for( item in renderer.contents ) {
        val mrlir = item.musicResponsiveListItemRenderer
        if( mrlir != null ) {
            val song = createInnertubeSongFrom( mrlir )
            mutableContents.add( song )

            continue
        }

        val mtwir = item.musicTwoRowItemRenderer ?: continue
        val item: InnertubeItem = when( mtwir.navigationEndpoint.pageType ) {
            PageType.ARTIST     -> createInnertubeArtistFrom( mtwir )
            PageType.ALBUM      -> createInnertubeAlbumFrom( mtwir )
            PageType.PLAYLIST   -> createInnertubePlaylistFrom( mtwir )
            // Ignore items with unknown page type (have no parser for it)
            else                -> continue
        }
        mutableContents.add( item )
    }
    val contents = mutableContents.toList()
    //</editor-fold>

    return object : Section {
        override val title: String = title
        override val accessibilityLabel: String? = accessibilityLabel
        override val browseId: String? = browseId
        override val params: String? = params
        override val contents: List<InnertubeItem> = contents
    }
}

internal fun createSectionFrom( renderer: MusicShelfRenderer ): Section {
    val title = renderer.title?.firstText
    val content = renderer.contents.mapNotNull { it.musicResponsiveListItemRenderer }.map( ::createInnertubeSongFrom )
    val browseEndpoint = renderer.bottomEndpoint?.browseEndpoint
    val browseId = browseEndpoint?.browseId
    val params = browseEndpoint?.params

    return object : Section {
        override val title: String? = title
        override val accessibilityLabel: String? = null
        override val browseId: String? = browseId
        override val params: String? = params
        override val contents: List<InnertubeItem> = content
    }
}