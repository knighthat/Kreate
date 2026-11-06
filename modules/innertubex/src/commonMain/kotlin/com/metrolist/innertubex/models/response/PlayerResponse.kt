package com.metrolist.innertubex.models.response

import com.metrolist.innertubex.models.Thumbnails
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
@OptIn(ExperimentalSerializationApi::class)
data class PlayerResponse(
    val playabilityStatus: PlayabilityStatus,
    val streamingData: StreamingData? = null,
    val videoDetails: VideoDetails? = null,
    val playbackTracking: PlaybackTracking? = null,
    val playerConfig: PlayerConfig? = null,
) {
    @Serializable
    data class PlayabilityStatus(
        val status: String,
        val reason: String? = null,
    )

    @Serializable
    data class StreamingData(
        val formats: List<Format>? = null,
        val adaptiveFormats: List<Format> = emptyList(),
        val expiresInSeconds: Int? = null,
        val hlsManifestUrl: String? = null,
        val dashManifestUrl: String? = null,
        val serverAbrStreamingUrl: String? = null,
    ) {
        @Serializable
        data class Format(
            val itag: Int = -1,
            val url: String? = null,
            val mimeType: String = "",
            val bitrate: Int = 0,
            val averageBitrate: Int? = null,
            val width: Int? = null,
            val height: Int? = null,
            val contentLength: Long? = null,
            val quality: String? = null,
            val audioQuality: String? = null,
            val approxDurationMs: String? = null,
            val audioSampleRate: Int? = null,
            val audioChannels: Int? = null,
            val loudnessDb: Double? = null,
            val lastModified: String? = null,
            val xtags: String? = null,
            val audioTrack: AudioTrack? = null,
            val isDrc: Boolean = false,
            @JsonNames("signature_cipher")
            val signatureCipher: String? = null,
            val cipher: String? = null,
        ) {
            val isAudio: Boolean
                get() = width == null

            @Serializable
            data class AudioTrack(
                val id: String? = null,
                val displayName: String? = null,
                val audioIsDefault: Boolean? = null,
            )
        }
    }

    @Serializable
    data class PlayerConfig(
        val mediaCommonConfig: MediaCommonConfig? = null,
    ) {
        var audioConfig: AudioConfig? = null
            private set

        @Serializable
        data class AudioConfig(
            val loudnessDb: Double? = null,
            val perceptualLoudnessDb: Double? = null,
        )

        @Serializable
        data class MediaCommonConfig(
            val mediaUstreamerRequestConfig: MediaUstreamerRequestConfig? = null,
        ) {
            @Serializable
            data class MediaUstreamerRequestConfig(
                val videoPlaybackUstreamerConfig: String? = null,
            )
        }
    }

    @Serializable
    data class VideoDetails(
        val videoId: String = "",
        val title: String? = null,
        val author: String? = null,
        val channelId: String? = null,
        val lengthSeconds: String? = null,
        val musicVideoType: String? = null,
        val viewCount: String? = null,
        val thumbnail: Thumbnails? = null,
        val isLiveContent: Boolean? = null,
    )

    @Serializable
    data class PlaybackTracking(
        val videostatsPlaybackUrl: VideostatsUrl? = null,
        val videostatsWatchtimeUrl: VideostatsUrl? = null,
        val atrUrl: VideostatsUrl? = null,
        val videostatsScheduledFlushWalltimeSeconds: List<Int>? = null,
        val videostatsDefaultFlushIntervalSeconds: Int? = null,
    ) {
        @Serializable
        data class VideostatsUrl(
            val baseUrl: String,
        )
    }
}
