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

package me.knighthat.kreate3.provider.opensubsonic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerInfoTest {

    private fun info(serverVersion: String?) =
        ServerInfo("1.16.1", "navidrome", serverVersion, isOpenSubsonic = true)

    @Test
    fun `version drops the build tag`() {
        assertEquals("0.64.2", info("0.64.2 (10114574)").version)
    }

    @Test
    fun `version keeps a version without a tag`() {
        assertEquals("0.55.0", info("0.55.0").version)
    }

    @Test
    fun `version keeps what comes before a pre-release suffix`() {
        assertEquals("0.64.2-rc1", info("0.64.2-rc1 (abc123)").version)
    }

    @Test
    fun `version is null when the server says nothing`() {
        assertNull(info(null).version)
        assertNull(info("  ").version)
        assertNull(info("(10114574)").version)
    }

    @Test
    fun `serverVersion stays as the server said it`() {
        assertEquals("0.64.2 (10114574)", info("0.64.2 (10114574)").serverVersion)
    }
}
