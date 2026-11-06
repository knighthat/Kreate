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

package me.knighthat.kreate3.download

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyDownloadsTest {

    @Test
    fun readsTheIdsOfTheOldApp() {
        assertEquals(
            listOf("dQw4w9WgXcQ", "kJQP7kiw5Fk"),
            parseLegacyDownloadIds("""["dQw4w9WgXcQ", "kJQP7kiw5Fk", "dQw4w9WgXcQ"]"""),
        )
    }

    @Test
    fun skipsWhatIsNotAnId() {
        assertEquals(
            listOf("9bZkp7q19f0"),
            parseLegacyDownloadIds("""["", "short", "has spaces!!", 12, null, {"a":1}, "9bZkp7q19f0"]"""),
        )
    }

    @Test
    fun readsNothingFromTextThatIsNotAList() {
        assertEquals(emptyList<String>(), parseLegacyDownloadIds("not json"))
        assertEquals(emptyList<String>(), parseLegacyDownloadIds("""{"ids":["dQw4w9WgXcQ"]}"""))
        assertEquals(emptyList<String>(), parseLegacyDownloadIds(""))
    }
}
