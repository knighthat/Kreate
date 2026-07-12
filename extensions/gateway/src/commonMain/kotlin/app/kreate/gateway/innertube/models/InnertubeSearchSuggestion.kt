package app.kreate.gateway.innertube.models

import app.kreate.annotations.Localized
import app.kreate.gateway.innertube.responses.Runs
import kotlin.reflect.KClass


interface InnertubeSearchSuggestion {

    /**
     * This is text-based suggestions (or completion)
     */
    val suggestions: List<Suggestion>

    /**
     * A list of quick-access shortcut items matching the current query.
     *
     * These shortcuts allow users to bypass full search results and navigate
     * directly to frequent or highly relevant destinations associated with the query.
     *
     * To improve performance, items are loosely captured. Meaning, they won't be
     * converted into [InnertubeSong], [InnertubeAlbum] or similar types.
     */
    val items: List<Item>

    /**
     * Represents a single auto-complete or search prediction item in a suggestion list.
     *
     * This interface separates the visual presentation of a suggestion from its functional behavior,
     * allowing for rich text styling (like highlighting the user's typed input) while maintaining
     * a clean string query for backend execution.
     */
    interface Suggestion {

        /**
         * The stylized visual representation of the suggestion.
         *
         * The text is typically split into multiple styled components. Components that
         * match what the user has already typed into the search box should be styled as **bold**,
         * while the predicted or appended parts remain normal.
         *
         * Example: If the user types "app", this might represent "**app**le" or "**app**lication".
         */
        val text: Runs

        /**
         * The complete, unstyled search term represented by this suggestion.
         *
         * This value acts as the functional payload. When a user selects or clicks this suggestion,
         * this full string is populated into the search box and sent to the search service
         * or API to fetch the final results.
         *
         * Example: `"apple"` or `"application"`.
         */
        val query: String
    }

    interface Item : InnertubeItem, ContentRating {

        /**
         * What type this item is representing.
         *
         * [InnertubeItem] is type is unknown
         */
        val type: KClass<out InnertubeItem>

        /**
         * Provides details about the item. In most cases, it tells
         * whether this item is a song, album, artist, or a playlist.
         */
        @get:Localized
        override val subtitle: Runs?

        /**
         * Whether the content of this item is appropriate for all audience.
         *
         * Only applied for [InnertubeSong] and [InnertubeAlbum] versions,
         * `false` otherwise.
         */
        override val isExplicit: Boolean
    }
}