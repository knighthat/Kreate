package app.kreate.gateway.innertube.models

import app.kreate.annotations.Localized


interface Descriptive {

    /**
     * Brief information
     */
    @get:Localized
    val description: String?
}