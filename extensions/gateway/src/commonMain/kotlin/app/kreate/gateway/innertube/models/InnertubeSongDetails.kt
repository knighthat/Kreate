package app.kreate.gateway.innertube.models

import app.kreate.annotations.Localized


interface InnertubeSongDetails {

    @get:Localized
    val title: String

    val viewCount: ViewCount

    @get:Localized
    val releaseDate: String

    @get:Localized
    val relativeReleaseDate: String

    @get:Localized
    val description: String

    val artist: InnertubeArtist

    interface ViewCount {

        @get:Localized
        val short: String

        @get:Localized
        val long: String
    }
}