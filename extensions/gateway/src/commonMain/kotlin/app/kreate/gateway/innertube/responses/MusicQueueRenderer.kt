package app.kreate.gateway.innertube.responses


interface MusicQueueRenderer {

    val content: Content?

    interface Content {

        val playlistPanelRenderer: PlaylistPanelRenderer
    }
}