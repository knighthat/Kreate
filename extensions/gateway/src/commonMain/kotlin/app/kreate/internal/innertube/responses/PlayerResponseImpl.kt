package app.kreate.internal.innertube.responses

import app.kreate.gateway.innertube.responses.PlayerResponse
import kotlinx.serialization.Serializable


@Serializable
internal data class PlayerResponseImpl(
    override val playabilityStatus: PlayabilityStatusImpl,
    override val streamingData: StreamingDataImpl?,
    override val videoDetails: VideoDetailsImpl?,
    override val playerConfig: PlayerConfigImpl?,
    override val microformat: MicroformatImpl?,
    override val responseContext: InnertubeResponseImpl.ContextImpl
): PlayerResponse {

    @Serializable
    internal data class PlayabilityStatusImpl(
        override val status: String,
        override val reason: String?,
        override val playableInEmbed: Boolean?,
        override val contextParams: String?
    ): PlayerResponse.PlayabilityStatus

    @Serializable
    internal data class StreamingDataImpl(
        override val expiresInSeconds: String,
        override val formats: List<FormatImpl> = emptyList(),
        override val adaptiveFormats: List<FormatImpl> = emptyList(),
        override val serverAbrStreamingUrl: String?
    ): PlayerResponse.StreamingData {

        @Serializable
        internal data class FormatImpl(
            override val itag: Short,
            override val url: String?,
            override val mimeType: String,
            override val bitrate: Int,
            override val width: Short?,
            override val height: Short?,
            override val lastModified: String,
            override val contentLength: String?,
            override val quality: String,
            override val fps: Byte?,
            override val qualityLabel: String?,
            override val projectionType: String,
            override val averageBitrate: Int?,
            override val highReplication: Boolean?,
            override val audioQuality: String?,
            override val approxDurationMs: String,
            override val audioSampleRate: String?,
            override val audioChannels: Byte?,
            override val loudnessDb: Float?,
            override val signatureCipher: String?
        ): PlayerResponse.StreamingData.Format
    }

    @Serializable
    internal data class VideoDetailsImpl(
        override val videoId: String,
        override val title: String,
        override val lengthSeconds: String,
        override val channelId: String,
        override val isOwnerViewing: Boolean,
        override val isCrawlable: Boolean,
        override val thumbnail: ThumbnailsImpl,
        override val allowRatings: Boolean,
        override val viewCount: String,
        override val author: String,
        override val isPrivate: Boolean,
        override val isUnpluggedCorpus: Boolean,
        override val musicVideoType: String?,
        override val isLiveContent: Boolean
    ): PlayerResponse.VideoDetails

    @Serializable
    internal data class PlayerConfigImpl(
        override val audioConfig: AudioConfigImpl
    ): PlayerResponse.PlayerConfig {

        @Serializable
        internal data class AudioConfigImpl(
            override val loudnessDb: Float?,
            override val perceptualLoudnessDb: Float,
            override val enablePerFormatLoudness: Boolean?
        ): PlayerResponse.PlayerConfig.AudioConfig
    }
}