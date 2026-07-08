package app.kreate.gateway.innertube.models

import app.kreate.gateway.innertube.responses.Runs


interface InnertubePlaylist: InnertubeItem, Descriptive, Continued {

    /**
     * Usually contains:
     *
     * - View count
     * - Total number of songs
     * - Total duration
     * - Or all above
     */
    val subtitle: Runs?

    val subtitleText: String?

    val songs: List<InnertubeSong>

    val songContinuation: String?
}