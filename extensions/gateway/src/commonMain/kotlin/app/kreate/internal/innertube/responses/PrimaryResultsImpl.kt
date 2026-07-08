package app.kreate.internal.innertube.responses

import app.kreate.gateway.innertube.responses.PrimaryResults
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient


@Serializable
internal data class PrimaryResultsImpl(
    override val results: ResultsImpl
): PrimaryResults {

    @Serializable
    internal data class ResultsImpl(
        override val contents: List<ContentImpl> = emptyList()
    ): PrimaryResults.Results {

        @Serializable
        internal data class ContentImpl(
            override val videoPrimaryInfoRenderer: VideoPrimaryInfoRendererImpl?,
            override val videoSecondaryInfoRenderer: VideoSecondaryInfoRendererImpl?
        ): PrimaryResults.Results.Content {

            @Serializable
            internal data class VideoPrimaryInfoRendererImpl(
                override val title: RunsImpl,
                override val viewCount: ViewCountImpl,
                override val dateText: SimpleTextImpl,
                override val relativeDateText: SimpleTextImpl
            ): PrimaryResults.Results.Content.VideoPrimaryInfoRenderer {

                @Serializable
                internal data class ViewCountImpl(
                    override val videoViewCountRenderer: RendererImpl
                ): PrimaryResults.Results.Content.VideoPrimaryInfoRenderer.ViewCount {

                    @Serializable
                    internal data class RendererImpl(
                        override val viewCount: SimpleTextImpl,
                        override val shortViewCount: SimpleTextImpl,
                        override val originalViewCount: String?
                    ): PrimaryResults.Results.Content.VideoPrimaryInfoRenderer.ViewCount.Renderer
                }
            }

            @Serializable
            internal data class VideoSecondaryInfoRendererImpl(
                override val owner: OwnerImpl,
                override val metadataRowContainer: MetadataRowContainerImpl?,
                override val showMoreText: SimpleTextImpl?,
                override val showLessText: SimpleTextImpl?,
                override val defaultExpanded: Boolean?,
                override val descriptionCollapsedLines: Int?,
                override val attributedDescription: AttributedDescriptionImpl
            ): PrimaryResults.Results.Content.VideoSecondaryInfoRenderer {

                @Serializable
                internal data class OwnerImpl(
                    override val videoOwnerRenderer: RendererImpl
                ): PrimaryResults.Results.Content.VideoSecondaryInfoRenderer.Owner {

                    @Serializable
                    internal data class RendererImpl(
                        override val thumbnail: ThumbnailsImpl,
                        override val title: RunsImpl,
                        override val navigationEndpoint: EndpointImpl,
                        override val subscriberCountText: SimpleTextImpl?,
                        override val badges: List<BadgeImpl> = emptyList()
                    ): PrimaryResults.Results.Content.VideoSecondaryInfoRenderer.Owner.Renderer
                }

                @Serializable
                internal data class MetadataRowContainerImpl(
                    override val metadataRowContainerRenderer: RendererImpl
                ): PrimaryResults.Results.Content.VideoSecondaryInfoRenderer.MetadataRowContainer {

                    @Serializable
                    internal data class RendererImpl(
                        override val collapsedItemCount: Int
                    ): PrimaryResults.Results.Content.VideoSecondaryInfoRenderer.MetadataRowContainer.Renderer
                }

                @Serializable
                internal data class AttributedDescriptionImpl(
                    override val content: String,
                    override val styleRuns: List<StyleRunImpl> = emptyList(),
                    override val headerRuns: List<HeaderRunImpl> = emptyList()
                ): PrimaryResults.Results.Content.VideoSecondaryInfoRenderer.AttributedDescription {

                    @Serializable
                    internal data class StyleRunImpl(
                        override val startIndex: UShort,
                        override val length: UShort,
                        override val styleRunExtensions: ExtensionImpl,
                        override val fontFamilyName: String
                    ): PrimaryResults.Results.Content.VideoSecondaryInfoRenderer.AttributedDescription.StyleRun {

                        @Serializable
                        internal data class ExtensionImpl(
                            override val styleRunColorMapExtension: MapExtensionImpl
                        ): PrimaryResults.Results.Content.VideoSecondaryInfoRenderer.AttributedDescription.StyleRun.Extension {

                            @Serializable
                            internal data class MapExtensionImpl(
                                @SerialName("colorMap") val params: List<Param> = emptyList()
                            ): PrimaryResults.Results.Content.VideoSecondaryInfoRenderer.AttributedDescription.StyleRun.Extension.MapExtension {

                                @Transient
                                override val colorMap: Map<String, Long> = params.associate { it.key to it.value }

                                @Serializable
                                internal data class Param(val key: String, val value: Long)
                            }
                        }
                    }

                    @Serializable
                    internal data class HeaderRunImpl(
                        override val startIndex: UShort,
                        override val length: UShort,
                        override val headerMapping: String
                    ): PrimaryResults.Results.Content.VideoSecondaryInfoRenderer.AttributedDescription.HeaderRun
                }
            }
        }
    }
}