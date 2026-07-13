package app.kreate.internal.innertube.models

import app.kreate.gateway.innertube.models.InnertubeItem
import app.kreate.gateway.innertube.models.Section
import app.kreate.gateway.innertube.responses.MusicCarouselShelfRenderer
import app.kreate.gateway.innertube.responses.MusicShelfRenderer
import app.kreate.internal.innertube.utils.firstText


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
        item.musicResponsiveListItemRenderer
            ?.let( ::createInnertubeSongFrom )
            ?.also( mutableContents::add )

        item.musicTwoRowItemRenderer
            ?.let( ::createInnertubeItemFrom )
            ?.also( mutableContents::add )
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