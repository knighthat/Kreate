package app.kreate.gateway.innertube.models

import app.kreate.gateway.innertube.responses.Runs


interface InnertubeSong: InnertubeItem, ContentRating {

    /**
     * Plain text representation of duration, in short format
     */
    val durationText: String?

    /**
     * Contains information about this song's album
     */
    val album: Runs.Run?

    /**
     * List of artists featured in this song
     */
    val artists: List<Runs.Run>

    /**
     * All authors listed in 1 line, with delimiter in between
     */
    val artistsText: String
}