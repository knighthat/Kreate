package com.metrolist.innertubex.models

import kotlinx.serialization.Serializable

@Serializable
data class MusicResponsiveListItemRenderer(
    val flexColumns: List<FlexColumn>,
    val thumbnail: ThumbnailRenderer? = null,
    val playlistItemData: PlaylistItemData? = null,
    val navigationEndpoint: NavigationEndpoint? = null,
) {
    @Serializable
    data class FlexColumn(
        val musicResponsiveListItemFlexColumnRenderer: MusicResponsiveListItemFlexColumnRenderer,
    ) {
        @Serializable
        data class MusicResponsiveListItemFlexColumnRenderer(
            val text: Runs? = null,
        )
    }

    @Serializable
    data class PlaylistItemData(
        val videoId: String,
        val playlistSetVideoId: String? = null,
    )
}

@Serializable
data class ThumbnailRenderer(
    val musicThumbnailRenderer: MusicThumbnailRenderer? = null,
) {
    @Serializable
    data class MusicThumbnailRenderer(
        val thumbnail: Thumbnails,
    )
}
