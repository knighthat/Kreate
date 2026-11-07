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

package me.knighthat.kreate3.ui.screen.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the edit screens count as a change and as something that can be applied. */
class EditItemStateTest {

    private val original = ItemDraft("Afterglow", "Neon Coast\nThe Tides")

    private fun state(draft: ItemDraft, saved: ItemDraft = original) =
        EditItemState(original, saved, draft)

    @Test
    fun noChangeThatShowsIsNoChange() {
        assertFalse(state(original).dirty)
        // Spaces around the name and empty lines aren't kept anyway
        assertFalse(
            state(
                original.copy(
                    name = " Afterglow ",
                    artists = "Neon Coast\n\n The Tides "
                )
            ).dirty
        )
        assertTrue(state(original.copy(name = "Afterglow 2")).dirty)
        assertTrue(state(original.copy(artists = "Neon Coast")).dirty)
    }

    @Test
    fun changeIsMeasuredAgainstWhatIsSaved() {
        val saved = original.copy(name = "Mine")
        assertFalse(state(saved, saved).dirty)
        assertTrue(state(original, saved).dirty)
    }

    @Test
    fun onlyAnItemWithoutANameCanKeepItEmpty() {
        assertFalse(state(original.copy(name = " ")).valid)
        val unnamed = ItemDraft()
        assertTrue(EditItemState(unnamed, unnamed, unnamed).valid)
        assertEquals(listOf("Neon Coast", "The Tides"), original.artistList)
    }
}
