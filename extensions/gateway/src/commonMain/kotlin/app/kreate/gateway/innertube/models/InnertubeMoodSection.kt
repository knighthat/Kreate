package app.kreate.gateway.innertube.models

import app.kreate.annotations.Localized
import app.kreate.gateway.innertube.responses.Endpoint
import app.kreate.gateway.innertube.responses.Runs


interface InnertubeMoodSection {

    /**
     * Can be empty
     */
    @get:Localized
    val title: String?

    /**
     * Used solely for accessibility purposes.
     *
     * For example: if [title] was "2013", then
     * this value should be "two thousands and thirteen"
     */
    @get:Localized
    val accessibilityLabel: String?

    /**
     * When this field is not a `null` value,
     * it means there are more items to check out
     * than what [contents] presents.
     */
    val browseId: String?

    /**
     * Send along with [browseId] to get the next iteration.
     */
    val params: String?

    val contents: List<Card>

    interface Card {

        val title: Runs

        val color: Long

        val endpoint: Endpoint.Browse
    }
}