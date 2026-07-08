package app.kreate.gateway.innertube.responses

import app.kreate.gateway.innertube.models.Icon


interface MusicResponsiveListItemRenderer {

    val thumbnail: Thumbnail?
    val flexColumns: List<Colum>
    val fixedColumns: List<Colum>
    val badges: List<Badge>
    val playlistItemData: PlaylistItemData?
    val flexColumnDisplayStyle: String?
    val navigationEndpoint: Endpoint?
    val itemHeight: String?
    val index: Runs?
    val customIndexColumn: CustomIndexColumn?

    interface Colum {

        val musicResponsiveListItemFlexColumnRenderer: Renderer?
        val musicResponsiveListItemFixedColumnRenderer: Renderer?

        interface Renderer {

            val text: Runs?
            val displayPriority: String
            val size: String?
        }
    }

    interface PlaylistItemData {

        val playlistSetVideoId: String?
        val videoId: String?
    }

    interface CustomIndexColumn {

        val musicCustomIndexColumnRenderer: Renderer

        interface Renderer {

            val text: Runs
            val icon: Icon?
            val accessibilityData: Accessibility
        }
    }
}