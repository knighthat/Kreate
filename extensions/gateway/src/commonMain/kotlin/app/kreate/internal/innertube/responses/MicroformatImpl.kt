package app.kreate.internal.innertube.responses

import app.kreate.gateway.innertube.responses.Microformat
import kotlinx.serialization.Serializable


@Serializable
internal data class MicroformatImpl(
    override val microformatDataRenderer: DataImpl?,
    override val playerMicroformatRenderer: PlayerImpl?
): Microformat {

    @Serializable
    internal data class DataImpl(
        override val urlCanonical: String?,
        override val title: String?,
        override val description: String?,
        override val thumbnail: ThumbnailsImpl?,
        override val siteName: String?,
        override val appName: String?,
        override val ogType: String?,
        override val twitterCardType: String?,
        override val twitterSiteHandle: String?,
        override val schemaDotOrgType: String?,
        override val noindex: Boolean?,
        override val unlisted: Boolean?,
        override val paid: Boolean?,
        override val familySafe: Boolean?,
        override val tags: List<String> = emptyList(),
        override val availableCountries: List<String> = emptyList(),
        override val pageOwnerDetails: PageOwnerDetailsImpl?,
        override val videoDetails: VideoDetailsImpl?,
        override val viewCount: String?,
        override val publishDate: String?,
        override val category: String?,
        override val uploadDate: String?
    ): Microformat.Data {

        @Serializable
        internal data class PageOwnerDetailsImpl(
            override val name: String,
            override val externalChannelId: String,
            override val youtubeProfileUrl: String
        ): Microformat.Data.PageOwnerDetails

        @Serializable
        internal data class VideoDetailsImpl(
            override val externalVideoId: String,
            override val durationSeconds: String,
            override val durationIso8601: String
        ): Microformat.Data.VideoDetails
    }

    @Serializable
    internal data class PlayerImpl(
        override val thumbnail: ThumbnailsImpl?,
        override val embed: EmbedImpl,
        override val title: SimpleTextImpl?,
        override val description: SimpleTextImpl?,
        override val lengthSeconds: String,
        override val ownerProfileUrl: String,
        override val externalChannelId: String,
        override val isFamilySafe: Boolean,
        override val availableCountries: List<String> = emptyList(),
        override val isUnlisted: Boolean,
        override val hasYpcMetadata: Boolean,
        override val viewCount: String,
        override val category: String,
        override val publishDate: String,
        override val ownerChannelName: String,
        override val uploadDate: String,
        override val isShortsEligible: Boolean,
        override val externalVideoId: String,
        override val likeCount: String,
        override val canonicalUrl: String
    ) : Microformat.Player {

        @Serializable
        internal data class EmbedImpl(
            override val iframeUrl: String,
            override val width: Int,
            override val height: Int
        ): Microformat.Player.Embed
    }
}
