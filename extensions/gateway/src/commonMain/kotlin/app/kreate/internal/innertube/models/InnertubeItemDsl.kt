package app.kreate.internal.innertube.models

import app.kreate.gateway.innertube.PageType
import app.kreate.gateway.innertube.models.InnertubeItem
import app.kreate.gateway.innertube.responses.MusicResponsiveListItemRenderer
import app.kreate.gateway.innertube.responses.MusicTwoRowItemRenderer
import app.kreate.internal.innertube.utils.pageType


internal fun createInnertubeItemFrom( renderer: MusicResponsiveListItemRenderer ): InnertubeItem? {
    if( renderer.navigationEndpoint?.watchEndpoint != null
        || renderer.playlistItemData?.videoId != null )
        return createInnertubeSongFrom( renderer )

    return when( renderer.navigationEndpoint.pageType ) {
        PageType.ARTIST     -> createInnertubeArtistFrom( renderer )
        PageType.ALBUM      -> createInnertubeAlbumFrom( renderer )
        PageType.PLAYLIST   -> createInnertubePlaylistFrom( renderer )
        // Ignore items with unknown page type (have no parser for it)
        else                -> null
    }
}

internal fun createInnertubeItemFrom( renderer: MusicTwoRowItemRenderer ): InnertubeItem? {
    return when( renderer.navigationEndpoint.pageType ) {
        PageType.ARTIST     -> createInnertubeArtistFrom( renderer )
        PageType.ALBUM      -> createInnertubeAlbumFrom( renderer )
        PageType.PLAYLIST   -> createInnertubePlaylistFrom( renderer )
        // Ignore items with unknown page type (have no parser for it)
        else                -> null
    }
}