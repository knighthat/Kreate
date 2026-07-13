package app.kreate.gateway.innertube.responses


interface MusicMultiSelectMenuRenderer {

    val title: Title
    val options: List<Option>

    interface Title {

        val musicMenuTitleRenderer: Renderer

        interface Renderer {

            val primaryText: Runs
        }
    }

    interface Option {

        val musicMultiSelectMenuItemRenderer: ItemRenderer?
        val musicMenuItemDividerRenderer: DividerRenderer?

        interface ItemRenderer {

            val title: Runs
            val formItemEntityKey: String
            val selectedAccessibility: Accessibility
            val deselectedAccessibility: Accessibility
        }

        interface DividerRenderer {

            val hack: Boolean
        }
    }
}