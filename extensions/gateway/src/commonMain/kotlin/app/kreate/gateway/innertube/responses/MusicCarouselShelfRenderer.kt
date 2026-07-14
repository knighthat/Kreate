package app.kreate.gateway.innertube.responses


interface MusicCarouselShelfRenderer {

    val header: Header
    val contents: List<Content>
    val itemSize: String
    val numItemsPerColumn: String?

    interface Header {

        val musicCarouselShelfBasicHeaderRenderer: MusicCarouselShelfBasicHeaderRenderer

        interface MusicCarouselShelfBasicHeaderRenderer {

            val title: Runs
            val strapline: Runs?
            val accessibilityData: Accessibility?
            val headerStyle: String
        }
    }

    interface Content {

        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer?
        val musicTwoRowItemRenderer: MusicTwoRowItemRenderer?
        val musicNavigationButtonRenderer: MusicNavigationButtonRenderer?

        interface MusicNavigationButtonRenderer {

            val buttonText: Runs
            val solid: Solid
            val clickCommand: Endpoint

            interface Solid {

                val leftStripeColor: Long
            }
        }
    }
}