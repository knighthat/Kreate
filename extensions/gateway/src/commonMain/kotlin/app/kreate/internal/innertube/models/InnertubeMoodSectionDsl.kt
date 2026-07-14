package app.kreate.internal.innertube.models

import app.kreate.gateway.innertube.models.InnertubeMoodSection
import app.kreate.gateway.innertube.responses.Endpoint
import app.kreate.gateway.innertube.responses.MusicCarouselShelfRenderer
import app.kreate.gateway.innertube.responses.Runs
import app.kreate.internal.innertube.utils.firstText


private fun createInnertubeMoodSectionCardFrom( renderer: MusicCarouselShelfRenderer.Content.MusicNavigationButtonRenderer ): InnertubeMoodSection.Card =
    object : InnertubeMoodSection.Card {

        override val title: Runs = renderer.buttonText
        override val color: Long = renderer.solid.leftStripeColor
        override val endpoint: Endpoint.Browse = renderer.clickCommand.browseEndpoint!!
    }

internal fun createInnertubeMoodSectionFrom( renderer: MusicCarouselShelfRenderer ): InnertubeMoodSection {
    val header = renderer.header.musicCarouselShelfBasicHeaderRenderer
    val title = header.title.firstText
    val accessibilityLabel = header.title.accessibility?.accessibilityData?.label
    val browseEndpoint = header.title.runs.firstOrNull()?.navigationEndpoint?.browseEndpoint
    val contents = renderer.contents.mapNotNull { it.musicNavigationButtonRenderer }.map( :: createInnertubeMoodSectionCardFrom )

    return object : InnertubeMoodSection {

        override val title: String = title
        override val accessibilityLabel: String? = accessibilityLabel
        override val browseId: String? = browseEndpoint?.browseId
        override val params: String? = browseEndpoint?.params
        override val contents: List<InnertubeMoodSection.Card> = contents
    }
}