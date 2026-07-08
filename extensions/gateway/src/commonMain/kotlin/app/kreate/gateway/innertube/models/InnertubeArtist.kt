package app.kreate.gateway.innertube.models

import app.kreate.annotations.Localized


interface InnertubeArtist: InnertubeItem, Descriptive, MultiContent {

    /**
     * Number of subscribers in **short** format:
     * - 10K
     * - 1M
     *
     *
     * Supports localization
     */
    @get:Localized
    val shortNumSubscribers: String?

    /**
     * Number of subscribers in **long** format:
     * - 19.3M subscribers
     *
     *
     * Supports localization
     */
    @get:Localized
    val longNumSubscribers: String?

    /**
     * Number of monthly listeners in short format:
     * - 324M monthly audience
     *
     *
     * Supports localization
     */
    @get:Localized
    val shortNumMonthlyAudience: String?
}