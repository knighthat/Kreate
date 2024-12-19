package me.knighthat.updater

import android.text.format.Formatter
import it.fast4x.rimusic.appContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class GithubRelease(
    val id: UInt,
    @SerialName("tag_name") val tagName: String,
    val name: String,
    @SerialName("assets") val builds: List<Build>
) {

    @Serializable
    data class Build(
        val id: UInt,
        val url: String,
        val name: String,
        val size: UInt,
        @SerialName("created_at") val createdAt: String,
        @SerialName("browser_download_url") val downloadUrl: String
    ) {
        val buildTime: Instant by lazy {
            Instant.parse( createdAt )
        }

        val readableSize: String
            // Don't remember with lazy because format mat be changed
            // when [appContext] is changed.
            get() = Formatter.formatShortFileSize( appContext(), this.size.toLong() )
    }
}