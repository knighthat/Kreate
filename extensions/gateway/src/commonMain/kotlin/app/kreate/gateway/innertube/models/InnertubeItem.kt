package app.kreate.gateway.innertube.models

import app.kreate.annotations.Localized


interface InnertubeItem: Visualized {

    /**
     * Unique identifier used to distinguish it from other items in a database
     */
    val id: String

    /**
     * A song's display name - the human-readable title shown to users
     */
    @get:Localized
    val name: String
}