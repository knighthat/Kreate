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

package me.knighthat.kreate3.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun instantIsStoredAsEpochMillis() {
        val instant = Instant.fromEpochMilliseconds(1_700_000_123_456)

        assertEquals(1_700_000_123_456, converters.fromInstant(instant))
        assertEquals(instant, converters.toInstant(1_700_000_123_456))
    }

    @Test
    fun durationIsStoredAsWholeSeconds() {
        assertEquals(215L, converters.fromDuration(215_900.milliseconds))
        assertEquals(215.seconds, converters.toDuration(215))
    }

    @Test
    fun namesRoundTripThroughJson() {
        val names = listOf("Artist, with comma", "名前", "\"quoted\"")

        val json = converters.fromNames(names)

        assertEquals(names, converters.toNames(json))
    }

    @Test
    fun unreadableNamesBecomeNull() {
        assertNull(converters.toNames("not json"))
        assertNull(converters.toNames("{\"a\":1}"))
    }

    @Test
    fun nullStaysNull() {
        assertNull(converters.fromInstant(null))
        assertNull(converters.toInstant(null))
        assertNull(converters.fromDuration(null))
        assertNull(converters.toDuration(null))
        assertNull(converters.fromNames(null))
        assertNull(converters.toNames(null))
    }
}
