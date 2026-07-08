package app.kreate.gateway.innertube.responses


interface Thumbnails {

    val thumbnails: List<Thumbnail>

    interface Thumbnail {

        val url: String
        val width: Short
        val height: Short
    }
}