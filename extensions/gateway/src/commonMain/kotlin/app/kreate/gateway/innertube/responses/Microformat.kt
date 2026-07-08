package app.kreate.gateway.innertube.responses


interface Microformat {

    val microformatDataRenderer: Data?
    val playerMicroformatRenderer: Player?

    interface Data {

        val urlCanonical: String?
        val title: String?
        val description: String?
        val thumbnail: Thumbnails?
        val siteName: String?
        val appName: String?
        val ogType: String?
        val twitterCardType: String?
        val twitterSiteHandle: String?
        val schemaDotOrgType: String?
        val noindex: Boolean?
        val unlisted: Boolean?
        val paid: Boolean?
        val familySafe: Boolean?
        val tags: List<String>
        val availableCountries: List<String>
        val pageOwnerDetails: PageOwnerDetails?
        val videoDetails: VideoDetails?
        val viewCount: String?
        val publishDate: String?
        val category: String?
        val uploadDate: String?

        interface PageOwnerDetails {

            val name: String
            val externalChannelId: String
            val youtubeProfileUrl: String
        }

        interface VideoDetails {

            val externalVideoId: String
            val durationSeconds: String
            val durationIso8601: String
        }
    }

    interface Player {

        val thumbnail: Thumbnails?
        val embed: Embed
        val title: SimpleText?
        val description: SimpleText?
        val lengthSeconds: String
        val ownerProfileUrl: String
        val externalChannelId: String
        val isFamilySafe: Boolean
        val availableCountries: List<String>
        val isUnlisted: Boolean
        val hasYpcMetadata: Boolean
        val viewCount: String
        val category: String
        val publishDate: String
        val ownerChannelName: String
        val uploadDate: String
        val isShortsEligible: Boolean
        val externalVideoId: String
        val likeCount: String
        val canonicalUrl: String

        interface Embed {
            val iframeUrl: String
            val width: Int
            val height: Int
        }
    }
}