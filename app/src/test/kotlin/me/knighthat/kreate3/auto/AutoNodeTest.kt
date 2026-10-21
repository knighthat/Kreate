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

package me.knighthat.kreate3.auto

import me.knighthat.kreate3.model.Origin
import me.knighthat.kreate3.ui.navigation.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AutoNodeTest {

    private fun assertRoundTrip(node: AutoNode) = assertEquals(node, AutoNode.parse(node.id))

    @Test
    fun theRootAndEveryTabReadBack() {
        assertRoundTrip(AutoNode.Root)
        Tab.entries.forEach { assertRoundTrip(AutoNode.Section(it)) }
    }

    @Test
    fun collectionsOfEveryOriginReadBack() {
        listOf(Tab.ALBUMS, Tab.ARTISTS, Tab.PLAYLISTS).forEach { tab ->
            Origin.entries.forEach { assertRoundTrip(AutoNode.Collection(tab, it, "id-1")) }
        }
    }

    @Test
    fun idsWithSlashesAndSpacesReadBack() {
        val album = AutoNode.Collection(Tab.ALBUMS, Origin.OPEN_SUBSONIC, "a/b c%2F?&=")
        assertRoundTrip(album)
        assertRoundTrip(AutoNode.Song(album, "song/with spaces/ü"))
    }

    @Test
    fun songsKeepTheirList() {
        assertRoundTrip(AutoNode.Song(AutoNode.Section(Tab.SONGS), "123"))
        assertRoundTrip(
            AutoNode.Song(
                AutoNode.Collection(Tab.PLAYLISTS, Origin.YOUTUBE, "PLxyz"),
                "dQw4w9WgXcQ"
            )
        )
    }

    @Test
    fun songIdsOfTheSourcesAreNotNodes() {
        listOf("123", "dQw4w9WgXcQ", "root", "tab/SONGS", "").forEach {
            assertNull(it, AutoNode.parse(it))
        }
    }

    @Test
    fun brokenIdsAreNotNodes() {
        listOf(
            "auto:",
            "auto:root/extra",
            "auto:tab/QUEUE",
            "auto:item/SONGS/LOCAL/1",
            "auto:item/ALBUMS/NOWHERE/1",
            "auto:item/ALBUMS/LOCAL",
            "auto:song/1",
            "auto:song/1/auto:root",
            "auto:song/1/auto:tab/ALBUMS",
            "auto:song/1/auto:song/2/auto:tab/SONGS",
        ).forEach { assertNull(it, AutoNode.parse(it)) }
    }
}
