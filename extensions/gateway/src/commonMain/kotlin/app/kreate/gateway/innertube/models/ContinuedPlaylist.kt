package app.kreate.gateway.innertube.models


interface ContinuedPlaylist {

    /**
     * Unique string to get next iteration of playlist
     *
     * If `null`, means there's no more song
     */
    val continuation: String?

    /**
     * List of this iteration.
     */
    val songs: List<InnertubeSong>
}