package app.kreate.internal.innertube.responses

import app.kreate.gateway.innertube.responses.Thumbnails
import kotlinx.serialization.Serializable


@Serializable
internal data class ThumbnailsImpl(
    override val thumbnails: List<ThumbnailImpl> = emptyList()
): Thumbnails {

    @Serializable
    internal data class ThumbnailImpl(
        override val url: String,
        override val width: Short,
        override val height: Short
    ): Thumbnails.Thumbnail
}