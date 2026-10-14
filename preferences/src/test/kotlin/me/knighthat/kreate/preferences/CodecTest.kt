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

package me.knighthat.kreate.preferences

import me.knighthat.kreate.constant.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CodecTest {

    @Test
    fun enumsAreStoredByName() {
        val codec = enumCodec<ThemeMode>()

        ThemeMode.entries.forEach {
            assertEquals(it.name, codec.encode(it))
            assertEquals(it, codec.decode(it.name))
        }
    }

    @Test
    fun anUnknownNameIsUnreadable() {
        assertNull(enumCodec<ThemeMode>().decode("REMOVED_LONG_AGO"))
    }

    @Test
    fun aValueOfAnotherTypeIsUnreadable() {
        assertNull(enumCodec<ThemeMode>().decode(3))
        assertNull(codec<Int, Int>({ it }, { it }).decode("3"))
    }
}
