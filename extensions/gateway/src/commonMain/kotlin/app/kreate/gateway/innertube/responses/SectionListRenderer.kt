package app.kreate.gateway.innertube.responses


interface SectionListRenderer {

    val contents: List<Content>
    val continuations: List<Continuation>

    interface Content {

        val musicDescriptionShelfRenderer: MusicDescriptionShelfRenderer?
        val musicTastebuilderShelfRenderer: MusicTastebuilderShelfRenderer?
        val musicResponsiveHeaderRenderer: MusicResponsiveHeaderRenderer?
        val musicEditablePlaylistDetailHeaderRenderer: MusicEditablePlaylistDetailHeaderRenderer?
        val musicShelfRenderer: MusicShelfRenderer?
        val musicCarouselShelfRenderer: MusicCarouselShelfRenderer?
        val gridRenderer: GridRenderer?
        val musicPlaylistShelfRenderer: MusicPlaylistShelfRenderer?
        val musicCardShelfRenderer: MusicCardShelfRenderer?

        interface MusicDescriptionShelfRenderer {

            val header: Runs?
            val subheader: Runs?
            val description: Runs
            val shelfStyle: String?
            val maxCollapsedLines: Int?
            val maxExpandedLines: Int?
            val footer: Runs?
        }

        interface MusicTastebuilderShelfRenderer {

            val thumbnail: Thumbnail
            val primaryText: Runs
            val secondaryText: Runs
            val isVisible: Boolean?

            interface Thumbnail {

                val musicTastebuilderShelfThumbnailRenderer: Thumbnail.Renderer
            }
        }

        interface MusicResponsiveHeaderRenderer {

            val thumbnail: Thumbnail
            val title: Runs
            val subtitle: Runs
            val description: Description?
            val straplineTextOne: Runs?
            val straplineThumbnail: Thumbnail?
            val subtitleBadge: List<Badge>
            val secondSubtitle: Runs?

            interface Description {

                val musicDescriptionShelfRenderer: MusicDescriptionShelfRenderer
            }
        }


        interface MusicEditablePlaylistDetailHeaderRenderer {
            val header: Header

            interface Header {

                val musicResponsiveHeaderRenderer: MusicResponsiveHeaderRenderer
            }
        }

        interface GridRenderer {

            val items: List<Item>
            val header: Header?

            interface Item {

                val musicTwoRowItemRenderer: MusicTwoRowItemRenderer?
            }

            interface Header {

                val gridHeaderRenderer: Renderer

                interface Renderer {

                    val title: Runs
                }
            }
        }
    }
}