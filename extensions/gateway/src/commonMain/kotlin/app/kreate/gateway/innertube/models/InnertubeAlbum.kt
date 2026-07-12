package app.kreate.gateway.innertube.models

import app.kreate.annotations.Localized
import app.kreate.gateway.innertube.responses.Runs


interface InnertubeAlbum: InnertubeItem, ContentRating, Descriptive, MultiContent {

    /**
     * Artists featured in this album
     */
    val artists: List<Runs.Run>

    /**
     * The year in which this album was released, -1 if unknown
     */
    val year: Int

    /**
     * This is sharable url of album
     */
    val urlCanonical: String?

    val songs: List<InnertubeSong>

    /**
     * Usually contains:
     *
     * - Total number of songs
     * - Total duration
     * - Or both
     */
    @get:Localized
    override val subtitle: Runs?
}