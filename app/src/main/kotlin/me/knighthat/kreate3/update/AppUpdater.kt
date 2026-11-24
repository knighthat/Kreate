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

import android.content.Intent
import kotlinx.coroutines.flow.StateFlow
import me.knighthat.kreate3.ui.screen.settings.about.Changelog

/**
 * Finds, downloads and installs new releases of the app. Only a build that updates itself
 * registers one in Koin (github, `GitHubUpdater`); the others have none, show the update settings
 * as not available and keep the mode off ([AutoUpdateOff]).
 */
interface AppUpdater {

    val state: StateFlow<UpdateState>

    /**
     * Whether the app may install apps, which `AutoUpdateMode.AUTOMATIC` needs. Always `true`
     * before Android 8, which asks the user in the device's settings instead.
     */
    val canInstall: Boolean

    /** The system's page where the user lets the app install apps, `null` when there is none. */
    fun installPermissionIntent(): Intent?

    /**
     * Called each time the app comes on screen: checks when `AutoUpdateMode` isn't `OFF` and the
     * last check is a day old or more, and does what the mode says with what it finds.
     */
    fun checkOnOpen()

    /** Checks now whatever the mode, and shows a new release as [UpdateState.Available]. */
    fun checkNow()

    /** Downloads and installs [release], behind [UpdateState.Downloading] and the states after. */
    fun install(release: AppRelease)

    /** Never offers [release] on its own again; a check from the settings still shows it. */
    fun ignore(release: AppRelease)

    /** The system's confirmation in [UpdateState.Installing] was opened. */
    fun confirmationShown()

    /** Closes what [state] shows, back to [UpdateState.Idle]. */
    fun dismiss()
}

/** What the updater is doing, which the app shows. */
sealed interface UpdateState {

    /** Nothing to show. */
    data object Idle : UpdateState

    /** Asking for the latest release; [manual] when the user asked from the settings. */
    data class Checking(val manual: Boolean) : UpdateState

    /** The user asked and there's no newer release. */
    data object UpToDate : UpdateState

    /** The user asked and the latest release couldn't be found. */
    data object CheckFailed : UpdateState

    /** A newer release the user picks what to do with. */
    data class Available(val release: AppRelease) : UpdateState

    /** Downloading [release]; [progress] from 0 to 1, `null` while the size isn't known. */
    data class Downloading(val release: AppRelease, val progress: Float?) : UpdateState

    /**
     * Handing [release] to the system. [confirmation] is its page asking the user to install,
     * for the screen to open, `null` when there is none (yet) or it was opened.
     */
    data class Installing(val release: AppRelease, val confirmation: Intent? = null) : UpdateState

    /** [release] couldn't be downloaded or installed, for [reason]. */
    data class Failed(val release: AppRelease, val reason: UpdateFailure) : UpdateState
}

enum class UpdateFailure {
    /** The file couldn't be downloaded. */
    DOWNLOAD,

    /** What was downloaded isn't the file the release lists (size or checksum). */
    DAMAGED,

    /** The user turned down the system's confirmation. */
    CANCELED,

    /** The system refused it (another signature, not enough space, …). */
    INSTALL,
}

/**
 * A release newer than the app.
 *
 * @param version without the tag's `v`, e.g. `3.1.0`
 * @param notes read from the release's text by [parseReleaseNotes]
 * @param apkUrl the APK of this build's ABI
 * @param size of the APK in bytes, `0` when not given
 * @param sha256 the APK's checksum in lowercase hex, `null` when not given
 */
data class AppRelease(
    val version: String,
    val notes: Changelog,
    val apkUrl: String,
    val size: Long,
    val sha256: String?,
)

/**
 * A version as tags and `versionName` write it: `v3.1.0`, `3.1.0-beta.2`, `3.1.0+github`. The
 * build after `+` is ignored, a pre-release comes before its release. `null` when it isn't one.
 */
internal data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: List<String>,
) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
            .let { if (it != 0) return it }
        // 3.1.0-beta.1 < 3.1.0
        if (preRelease.isEmpty() || other.preRelease.isEmpty())
            return other.preRelease.size.coerceAtMost(1) - preRelease.size.coerceAtMost(1)
        preRelease.zip(other.preRelease).forEach { (a, b) ->
            val numbers = a.toIntOrNull() to b.toIntOrNull()
            val order = when {
                numbers.first != null && numbers.second != null ->
                    numbers.first!!.compareTo(numbers.second!!)

                numbers.first != null -> -1
                numbers.second != null -> 1
                else -> a.compareTo(b)
            }
            if (order != 0) return order
        }
        return preRelease.size.compareTo(other.preRelease.size)
    }

    companion object {
        private val PATTERN = Regex("""v?(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+.*)?""")

        fun parse(text: String): AppVersion? {
            val match = PATTERN.matchEntire(text.trim()) ?: return null
            val (major, minor, patch, pre) = match.destructured
            return AppVersion(
                major.toInt(),
                minor.toInt(),
                patch.toInt(),
                if (pre.isEmpty()) emptyList() else pre.split('.'),
            )
        }
    }
}

/** The words a heading of the release's text starts with, and the list of [Changelog] it fills. */
private enum class NotesHeading(val word: String) {
    ADDED("added"),
    REMOVED("removed"),
    CHANGED("changed"),
    FIXED("fixed"),
}

/**
 * Reads a release's text, as the release workflow writes it from the github flavor's notes:
 * headings (`### Added`, or `Added:`) each followed by `- ` items. Lines under no known heading are
 * left out; a text with no heading at all goes into [Changelog.changes] line by line, so nothing is
 * hidden.
 */
internal fun parseReleaseNotes(version: String, body: String): Changelog {
    val lists = NotesHeading.entries.associateWith { mutableListOf<String>() }
    var current: NotesHeading? = null
    var sawHeading = false
    body.lineSequence().map(String::trim).filter(String::isNotEmpty).forEach { line ->
        val heading = line.trimStart('#').trim().removeSuffix(":").trim().lowercase()
        val found = NotesHeading.entries.firstOrNull { it.word == heading }
        if (found != null) {
            current = found
            sawHeading = true
        } else {
            val item = line.removePrefix("- ").removePrefix("* ").trim()
            current?.let { lists.getValue(it) += item }
        }
    }
    if (!sawHeading) {
        val lines = body.lines()
            .map { it.trim().removePrefix("- ").removePrefix("* ").trim() }
            .filter(String::isNotEmpty)
        return Changelog(version = version, changes = lines)
    }
    return Changelog(
        version = version,
        adds = lists.getValue(NotesHeading.ADDED),
        removes = lists.getValue(NotesHeading.REMOVED),
        changes = lists.getValue(NotesHeading.CHANGED),
        fixes = lists.getValue(NotesHeading.FIXED),
    )
}
