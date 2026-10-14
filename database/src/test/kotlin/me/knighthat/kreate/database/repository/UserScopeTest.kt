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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import me.knighthat.kreate.database.ActiveUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Collections

class UserScopeTest {

    private val id = MutableStateFlow<String?>(null)
    private val scope = UserScope(object : ActiveUser {
        override val id = this@UserScopeTest.id
    })

    private fun eventually(check: () -> Boolean) = runBlocking {
        withTimeout(5_000) { while (!check()) delay(10) }
    }

    @Test
    fun queriesRestartForEachUser() = runBlocking {
        val seen = Collections.synchronizedList(mutableListOf<String>())
        val job = launch(Dispatchers.Default) {
            scope.observe("nobody") { flowOf("rows of $it") }.collect { seen += it }
        }

        eventually { seen.lastOrNull() == "nobody" }
        id.value = "a"
        eventually { seen.lastOrNull() == "rows of a" }
        id.value = "b"
        eventually { seen.lastOrNull() == "rows of b" }
        id.value = null
        eventually { seen.lastOrNull() == "nobody" }

        job.cancel()
    }

    @Test
    fun awaitingTheIdWaitsForAUser() = runBlocking {
        val awaited = async(Dispatchers.Default) { scope.awaitId() }
        delay(100)
        assertFalse(awaited.isCompleted)

        id.value = "a"

        assertEquals("a", withTimeout(5_000) { awaited.await() })
    }

    @Test
    fun writesNeedAUser() {
        assertThrows(IllegalStateException::class.java) { scope.requireId() }

        id.value = "a"
        assertEquals("a", scope.requireId())
    }
}
