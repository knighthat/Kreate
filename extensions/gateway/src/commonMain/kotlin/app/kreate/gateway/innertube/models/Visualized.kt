package app.kreate.gateway.innertube.models

import app.kreate.gateway.innertube.responses.Thumbnails


interface Visualized {

    /**
     * A set of thumbnails, variable in sizes
     */
    val thumbnails: List<Thumbnails.Thumbnail>
}