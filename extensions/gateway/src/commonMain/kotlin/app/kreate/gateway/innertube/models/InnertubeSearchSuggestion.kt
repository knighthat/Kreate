package app.kreate.gateway.innertube.models

import app.kreate.gateway.innertube.responses.Runs


interface InnertubeSearchSuggestion {

    /**
     * This is text-based suggestions (or completion)
     */
    val suggestions: List<Suggestion>

    /**
     * Suggested items that could be matches for input
     */
    val items: List<InnertubeItem>

    interface Suggestion {

        val text: Runs

        val query: String
    }
}