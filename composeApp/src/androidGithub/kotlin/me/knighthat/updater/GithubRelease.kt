package me.knighthat.updater

import android.text.format.Formatter
import it.fast4x.rimusic.appContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


@Serializable
data class GithubRelease(
    val id: UInt,

    val name: String,

    val prerelease: Boolean,

    @SerialName("tag_name")
    val tagName: String,

    @SerialName("assets")
    val builds: List<Build>,

    @OptIn(ExperimentalTime::class)
    @SerialName("published_at")
    val publishedAt: Instant
) {

    @Serializable
    data class Build(
        val id: UInt,

        val url: String,

        val name: String,

        val size: UInt,

        val digest: String?,

        @SerialName("created_at")
        val createdAt: String,

        @SerialName("browser_download_url")
        val downloadUrl: String
    ) {

        val readableSize: String
            // Don't remember with lazy because format mat be changed
            // when [appContext] is changed.
            get() = Formatter.formatShortFileSize( appContext(), this.size.toLong() )
    }
}