package app.kreate.internal.innertube.responses

import app.kreate.gateway.innertube.responses.Thumbnail
import kotlinx.serialization.Serializable


@Serializable
internal data class ThumbnailImpl(
    override val musicThumbnailRenderer: RendererImpl
): Thumbnail {

    @Serializable
    internal data class RendererImpl(
        override val thumbnail: ThumbnailsImpl,
        override val thumbnailCrop: String?,
        override val thumbnailScale: String?
    ): Thumbnail.Renderer
}