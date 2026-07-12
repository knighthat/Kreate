package app.kreate.gateway.innertube.models

import app.kreate.gateway.innertube.responses.Runs


interface InnertubePlaylist: InnertubeItem, Descriptive, Continued {

    val subtitleText: String?

    val songs: List<InnertubeSong>

    val songContinuation: String?

    /**
     * Usually contains:
     *
     * - View count
     * - Total number of songs
     * - Total duration
     * - Or all above
     */
    override val subtitle: Runs?
}