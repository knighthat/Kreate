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

package me.knighthat.kreate.database.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchPatternTest {

    @Test
    fun theQueryIsTrimmedAndWrapped() {
        assertEquals("%queen%", containsPattern("  queen "))
    }

    @Test
    fun wildcardsAreEscaped() {
        assertEquals("%100\\%%", containsPattern("100%"))
        assertEquals("%a\\_b%", containsPattern("a_b"))
    }

    @Test
    fun theEscapeCharacterIsEscapedFirst() {
        // Escaped before the wildcards, or the backslash added for `%` would be doubled too
        assertEquals("%a\\\\\\%%", containsPattern("a\\%"))
    }

    @Test
    fun aPrefixHasOnlyTheTrailingWildcard() {
        assertEquals("qu%", prefixPattern(" qu"))
        assertEquals("\\%off%", prefixPattern("%off"))
    }

    @Test
    fun anEmptyQueryMatchesEverything() {
        assertEquals("%%", containsPattern(""))
        assertEquals("%", prefixPattern(""))
    }
}
