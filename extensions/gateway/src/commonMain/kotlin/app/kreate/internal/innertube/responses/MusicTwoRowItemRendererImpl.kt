package app.kreate.internal.innertube.responses

import app.kreate.gateway.innertube.responses.MusicTwoRowItemRenderer
import kotlinx.serialization.Serializable


@Serializable
internal data class MusicTwoRowItemRendererImpl(
    override val thumbnailRenderer: ThumbnailImpl,
    override val aspectRatio: String,
    override val title: RunsImpl,
    override val subtitle: RunsImpl,
    override val navigationEndpoint: EndpointImpl,
    override val subtitleBadges: List<BadgeImpl> = emptyList()
): MusicTwoRowItemRenderer