package app.kreate.gateway.innertube.responses


interface MusicMultiSelectMenuRenderer {

    val title: Title

    interface Title {

        val musicMenuTitleRenderer: Renderer

        interface Renderer {

            val primaryText: Runs
        }
    }
}