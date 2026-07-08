package app.kreate.gateway.innertube.models


interface ContentRating {

    /**
     * Whether content is appropriate for all audience
     *
     * **NOTE:** returns `false` if unknown
     */
    val isExplicit: Boolean
}