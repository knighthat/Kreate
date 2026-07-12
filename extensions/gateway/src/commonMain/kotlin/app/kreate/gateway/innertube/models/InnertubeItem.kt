package app.kreate.gateway.innertube.models

import app.kreate.annotations.Localized
import app.kreate.gateway.innertube.responses.Runs


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

    /**
     * A wide range of subtexts.
     *
     * ### Songs / Videos
     *
     * - Artist(s)
     * - Album
     * - Views
     *
     * ### Albums
     *
     * - Artist
     * - Release year
     * - Total number of songs
     * - Total duration
     *
     * ### Artists
     *
     * - Subscribers
     * - Monthly views
     *
     * ### Playlists / Podcasts
     *
     * - Owner
     * - Views
     *
     * ### Search suggestion/result
     *
     * - Item type
     * - Attributes from aforementioned types
     */
    @get:Localized
    val subtitle: Runs?
}