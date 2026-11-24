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

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /** GitHub's `releases/latest` answer, cut to the fields read and two assets. */
    private val answer = """
        {
          "url": "https://api.github.com/repos/knighthat/kreate3/releases/457132826",
          "tag_name": "v3.1.0",
          "draft": false,
          "prerelease": false,
          "assets": [
            {
              "name": "kreate-arm64.apk",
              "size": 7864002,
              "browser_download_url": "https://github.com/knighthat/kreate3/releases/download/v3.1.0/kreate-arm64.apk",
              "digest": "sha256:F63C89503106C13B222C2E8B165A71EF4938C176109192E93A13201F729A6595"
            },
            {
              "name": "kreate-universal.apk",
              "size": 11574060,
              "browser_download_url": "https://github.com/knighthat/kreate3/releases/download/v3.1.0/kreate-universal.apk"
            }
          ],
          "body": "Added:\n- Profiles\n- Downloads\n\nChanged:\n- Rebuilt from the ground up\n"
        }
    """.trimIndent()

    private fun version(text: String) = AppVersion.parse(text)!!

    @Test
    fun versionsCompare() {
        assertTrue(version("v3.1.0") > version("3.0.9+github"))
        assertTrue(version("3.0.10") > version("3.0.9"))
        assertTrue(version("3.1.0") > version("3.1.0-beta.2"))
        assertTrue(version("3.1.0-beta.10") > version("3.1.0-beta.2"))
        assertEquals(0, version("v3.0.0").compareTo(version("3.0.0+github")))
        assertNull(AppVersion.parse("latest"))
    }

    @Test
    fun releaseNewerThanTheAppIsAnUpdate() {
        val release = json.decodeFromString<GitHubRelease>(answer)
        val update = release.updateFor("3.0.0+github", "arm64")!!

        assertEquals("3.1.0", update.version)
        assertEquals(7864002L, update.size)
        assertEquals(
            "f63c89503106c13b222c2e8b165a71ef4938c176109192e93a13201f729a6595",
            update.sha256
        )
        assertEquals(listOf("Profiles", "Downloads"), update.notes.adds)
        assertEquals(listOf("Rebuilt from the ground up"), update.notes.changes)
    }

    @Test
    fun noUpdateWhenNotNewerOrNoApk() {
        val release = json.decodeFromString<GitHubRelease>(answer)

        assertNull(release.updateFor("3.1.0+github", "arm64"))
        assertNull(release.updateFor("3.2.0-beta.1+github", "arm64"))
        assertNull(release.updateFor("3.0.0+github", "x86"))
        // Without a digest the checksum isn't checked
        assertNull(release.updateFor("3.0.0+github", "universal")!!.sha256)
        assertNull(release.copy(prerelease = true).updateFor("3.0.0+github", "arm64"))
    }

    @Test
    fun workflowNotesAreRead() {
        val notes = parseReleaseNotes(
            "3.1.0",
            "### Added\n- One\n- Two\n\n### Fixed\n- Three",
        )

        assertEquals(listOf("One", "Two"), notes.adds)
        assertEquals(listOf("Three"), notes.fixes)
        assertTrue(notes.removes.isEmpty() && notes.changes.isEmpty())
    }

    @Test
    fun notesWithoutHeadingsAreKept() {
        val notes = parseReleaseNotes("3.1.0", "First line\n- Second line")

        assertEquals(listOf("First line", "Second line"), notes.changes)
    }
}
