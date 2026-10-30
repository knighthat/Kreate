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
import org.junit.Test

class VoiceSearchTest {

    @Test
    fun namesScoreBySameThenStartThenHolding() {
        assertEquals(3, nameScore("Invincible", "invincible"))
        assertEquals(2, nameScore("Invincible (Live)", "invincible"))
        assertEquals(1, nameScore("Not Invincible", "invincible"))
        assertEquals(0, nameScore("Come On to the Future", "invincible"))
        assertEquals(0, nameScore(null, "invincible"))
    }

    @Test
    fun originsFollowTheOrderAndAddTheMissingOnes() {
        assertEquals(
            listOf(Origin.OPEN_SUBSONIC to true, Origin.LOCAL to false, Origin.YOUTUBE to true),
            voiceSearchOrigins(listOf("OPEN_SUBSONIC", "LOCAL"), setOf("LOCAL")),
        )
    }

    @Test
    fun unknownAndRepeatedOriginsAreLeftOut() {
        assertEquals(
            listOf(Origin.LOCAL to true, Origin.YOUTUBE to true, Origin.OPEN_SUBSONIC to true),
            voiceSearchOrigins(listOf("SPOTIFY", "LOCAL", "LOCAL", "YOUTUBE"), setOf("SPOTIFY")),
        )
    }

    @Test
    fun theNameTheAssistantGaveIsSearchedForItsKind() {
        val request =
            VoiceRequest("invincible by skillet", focus = Tab.SONGS, title = " Invincible ")
        assertEquals(listOf(Tab.SONGS), request.tabs)
        assertEquals("Invincible", request.termFor(Tab.SONGS))
        assertEquals("invincible by skillet", request.termFor(Tab.ALBUMS))
    }

    @Test
    fun withoutFocusEveryKindIsSearched() {
        assertEquals(VoiceRequest.ALL_TABS, VoiceRequest("skillet").tabs)
    }
}
