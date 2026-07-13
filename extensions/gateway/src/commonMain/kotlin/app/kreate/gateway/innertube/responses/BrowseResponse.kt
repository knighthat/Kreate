package app.kreate.gateway.innertube.responses


interface BrowseResponse : InnertubeResponse {

    val contents: Contents?
    val header: Header?
    val maxAgeStoreSeconds: Int?
    val microformat: Microformat?
    val background: Thumbnail?
    val onResponseReceivedActions: List<ResponseReceivedAction>
    val continuationContents: ContinuationContents?

    interface Contents {

        val singleColumnBrowseResultsRenderer: Tabs?
        val twoColumnBrowseResultsRenderer: TwoColumnBrowseResultsRenderer?
        val sectionListRenderer: SectionListRenderer?

        interface TwoColumnBrowseResultsRenderer {

            val tabs: List<Tabs.Tab>
            val secondaryContents: SecondaryContents?

            interface SecondaryContents {

                val sectionListRenderer: SectionListRenderer
            }
        }
    }

    interface Header {

        val musicImmersiveHeaderRenderer: MusicImmersiveHeaderRenderer?
        val musicHeaderRenderer: MusicHeaderRenderer?
        val musicVisualHeaderRenderer: MusicVisualHeaderRenderer?

        interface MusicImmersiveHeaderRenderer {

            val title: Runs
            val description: Runs?
            val thumbnail: Thumbnail
            val shareEndpoint: Endpoint?
            val monthlyListenerCount: Runs?
            val subscriptionButton: Button
        }

        interface MusicHeaderRenderer {

            val title: Runs?
        }

        interface MusicVisualHeaderRenderer {

            val title: Runs
            val thumbnail: Thumbnail
            val foregroundThumbnail: Thumbnail
        }
    }

    interface ResponseReceivedAction {

        val appendContinuationItemsAction: AppendContinuationItemsAction

        interface AppendContinuationItemsAction {

            val continuationItems: List<MusicPlaylistShelfRenderer.Content>
        }
    }

    interface ContinuationContents {

        val sectionListContinuation: SectionListRenderer?
        val musicShelfContinuation: MusicShelfRenderer?
    }
}