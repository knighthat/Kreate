package app.kreate.gateway.innertube.responses


interface MusicShelfRenderer {

    val title: Runs?
    val contents: List<Content>
    val bottomText: Runs?
    val bottomEndpoint: Endpoint?
    val contentsMultiSelectable: Boolean?
    val subheaders: List<Subheader>
    val continuations: List<Continuation>

    interface Content {

        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer?
    }

    interface Subheader {

        val musicSideAlignedItemRenderer: Renderer

        interface Renderer {

            val startItems: List<Item>

            interface Item {

                val musicSortFilterButtonRenderer: MusicSortFilterButtonRenderer

                interface MusicSortFilterButtonRenderer {

                    val title: Runs
                    val menu: Menu
                    val accessibility: Accessibility

                    interface Menu {

                        val musicMultiSelectMenuRenderer: MusicMultiSelectMenuRenderer
                    }
                }
            }
        }
    }
}