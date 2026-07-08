package app.kreate.gateway.innertube.responses


interface PrimaryResults {

    val results: Results
    // Missing secondaryResults, autoplay

    interface Results {

        val contents: List<Content>

        interface Content {

            val videoPrimaryInfoRenderer: VideoPrimaryInfoRenderer?
            val videoSecondaryInfoRenderer: VideoSecondaryInfoRenderer?
            // Missing compositeVideoPrimaryInfoRenderer, itemSectionRenderer

            interface VideoPrimaryInfoRenderer {

                val title: Runs
                val viewCount: ViewCount
                // Missing videoActions
                val dateText: SimpleText
                val relativeDateText: SimpleText

                interface ViewCount {

                    val videoViewCountRenderer: Renderer

                    interface Renderer {

                        val viewCount: SimpleText
                        val shortViewCount: SimpleText
                        val originalViewCount: String?
                    }
                }
            }

            interface VideoSecondaryInfoRenderer {

                val owner: Owner
                // Missing subscribeButton
                val metadataRowContainer: MetadataRowContainer?
                val showMoreText: SimpleText?
                val showLessText: SimpleText?
                val defaultExpanded: Boolean?
                val descriptionCollapsedLines: Int?
                // Missing showMoreCommand, showLessCommand
                val attributedDescription: AttributedDescription

                interface Owner {

                    val videoOwnerRenderer: Renderer

                    interface Renderer {

                        val thumbnail: Thumbnails
                        val title: Runs
                        val navigationEndpoint: Endpoint
                        val subscriberCountText: SimpleText?
                        val badges: List<Badge>
                    }
                }

                interface MetadataRowContainer {

                    val metadataRowContainerRenderer: Renderer

                    interface Renderer {

                        val collapsedItemCount: Int
                    }
                }

                interface AttributedDescription {

                    val content: String
                    val styleRuns: List<StyleRun>
                    val headerRuns: List<HeaderRun>

                    interface StyleRun {

                        val startIndex: UShort
                        val length: UShort
                        val styleRunExtensions: Extension
                        val fontFamilyName: String

                        interface Extension {

                            val styleRunColorMapExtension: MapExtension

                            interface MapExtension {

                                val colorMap: Map<String, Long>
                            }
                        }
                    }

                    interface HeaderRun {

                        val startIndex: UShort
                        val length: UShort
                        val headerMapping: String
                    }
                }
            }
        }
    }
}