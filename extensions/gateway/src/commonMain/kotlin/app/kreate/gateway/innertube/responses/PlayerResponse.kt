package app.kreate.gateway.innertube.responses


interface PlayerResponse : InnertubeResponse {

    val playabilityStatus: PlayabilityStatus
    val streamingData: StreamingData?
    val videoDetails: VideoDetails?
    val playerConfig: PlayerConfig?
    val microformat: Microformat?

    interface PlayabilityStatus {

        val status: String
        val reason: String?
        val playableInEmbed: Boolean?
        val contextParams: String?
    }

    interface StreamingData {

        val expiresInSeconds: String
        val formats: List<Format>
        val adaptiveFormats: List<Format>
        val serverAbrStreamingUrl: String?

        interface Format {

            /**
             *  Unique ID of this format
             *
             *  Visit [this page](https://gist.github.com/AgentOak/34d47c65b1d28829bb17c24c04a0096f) for details
             */
            val itag: Short
            val url: String?
            val mimeType: String
            val bitrate: Int

            /**
             * Horizontal pixel count
             *
             * **NOTE:** Not available on audio formats
             */
            val width: Short?

            /**
             * Vertical pixel count
             *
             * **NOTE:** Not available on audio formats
             */
            val height: Short?
            val lastModified: String
            val contentLength: String?
            val quality: String

            /**
             * **_F_**rames **_P_**er **_S_**econd
             *
             * **NOTE:** Not available on audio formats
             */
            val fps: Byte?

            /**
             * Video quality tag
             *
             * **NOTE:** Not available on audio formats
             */
            val qualityLabel: String?
            val projectionType: String
            val averageBitrate: Int?
            val highReplication: Boolean?
            val audioQuality: String?
            val approxDurationMs: String
            val audioSampleRate: String?
            val audioChannels: Byte?
            val loudnessDb: Float?
            val signatureCipher: String?
        }
    }

    interface VideoDetails {

        val videoId: String
        val title: String
        val lengthSeconds: String
        val channelId: String
        val isOwnerViewing: Boolean
        val isCrawlable: Boolean
        val thumbnail: Thumbnails
        val allowRatings: Boolean
        val viewCount: String
        val author: String
        val isPrivate: Boolean
        val isUnpluggedCorpus: Boolean
        val musicVideoType: String?
        val isLiveContent: Boolean
    }

    interface PlayerConfig {

        val audioConfig: AudioConfig

        interface AudioConfig {

            val loudnessDb: Float?
            val perceptualLoudnessDb: Float
            val enablePerFormatLoudness: Boolean?
        }
    }
}