package app.kreate.gateway.innertube.responses


interface MusicPlaylistShelfRenderer {

    val playlistId: String?
    val contents: List<Content>
    val collapsedItemCount: Int
    val contentsMultiSelectable: Boolean
    val targetId: String?

    interface Content {

        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer?
        val continuationItemRenderer: ContinuationItemRenderer?

        interface ContinuationItemRenderer {

            val trigger: String
            val continuationEndpoint: Continuation

            interface Continuation {

                val continuationCommand: Command

                interface Command {

                    val token: String
                    val request: String
                }
            }
        }
    }
}