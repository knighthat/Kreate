package app.kreate.gateway.innertube.responses


interface MusicShelfRenderer {

    val title: Runs?
    val contents: List<Content>
    val bottomText: Runs?
    val bottomEndpoint: Endpoint?
    val contentsMultiSelectable: Boolean?
    val continuations: List<Continuation>

    interface Content {

        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer?
    }
}