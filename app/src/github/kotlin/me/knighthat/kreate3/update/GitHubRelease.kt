/*
 * Kreate
 * Copyright (C) 2026 KnightHat
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.knighthat.kreate3.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The parts of GitHub's `releases/latest` answer the updater reads. */
@Serializable
internal data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
internal data class GitHubAsset(
    val name: String,
    val size: Long = 0,
    @SerialName("browser_download_url") val downloadUrl: String,
    /** `sha256:<hex>`, missing on releases made before GitHub added it. */
    val digest: String? = null,
)

/** The APK GitHub releases attach for each ABI flavor, like `kreate-arm64.apk`. */
internal fun apkName(abi: String): String = "kreate-$abi.apk"

/**
 * [release] as an update of [installed] (a `versionName`) for the [abi] build, `null` when it
 * isn't newer, is a draft or pre-release, or has no APK for [abi].
 */
internal fun GitHubRelease.updateFor(installed: String, abi: String): AppRelease? {
    if (draft || prerelease) return null
    val latest = AppVersion.parse(tagName) ?: return null
    val current = AppVersion.parse(installed) ?: return null
    if (latest <= current) return null

    val apk = assets.firstOrNull { it.name == apkName(abi) } ?: return null
    val version = tagName.trim().removePrefix("v")
    return AppRelease(
        version = version,
        notes = parseReleaseNotes(version, body.orEmpty()),
        apkUrl = apk.downloadUrl,
        size = apk.size,
        sha256 = apk.digest
            ?.takeIf { it.startsWith("sha256:") }
            ?.removePrefix("sha256:")
            ?.lowercase(),
    )
}
