package app.kreate.gateway.innertube.models

import app.kreate.gateway.innertube.responses.Continuation


interface Continued {

    /**
     * Contains parameters to get the next iteration of this item.
     *
     * Requires [visitorData] in most cases. If used with login credentials,
     * the same credentials must be included to get continuation
     */
    val continuations: List<Continuation>

    /**
     * Required in most cases to get next iteration of this item
     */
    val visitorData: String?
}