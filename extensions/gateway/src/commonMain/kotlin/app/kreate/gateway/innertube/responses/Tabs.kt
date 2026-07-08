package app.kreate.gateway.innertube.responses


interface Tabs {

    val tabs: List<Tab>

    interface Tab {

        val tabRenderer: Renderer

        interface Renderer {

            val endpoint: Endpoint?
            val title: String?
            val selected: Boolean?
            val content: Content?
            val tabIdentifier: String?

            interface Content {

                val sectionListRenderer: SectionListRenderer?
                val musicQueueRenderer: MusicQueueRenderer?
            }
        }
    }
}