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

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import me.knighthat.kreate3.notification.Notifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SourceToggleTest {

    private val notifier = Notifier()

    /** What was sent to the source, in order. */
    private val sent = mutableListOf<Boolean>()

    /** The messages posted, as they would show. */
    private val messages = mutableListOf<String>()

    private fun TestScope.toggle(send: suspend (Boolean) -> Unit = { sent += it }): SourceToggle {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            notifier.messages.collect { messages += it.text }
        }
        return SourceToggle(backgroundScope, notifier, send) { _, on ->
            if (on) "Couldn't turn it on" else "Couldn't turn it off"
        }
    }

    @Test
    fun nothingHappensUntilItIsKnown() = runTest {
        val toggle = toggle()

        toggle.toggle()
        runCurrent()

        assertNull(toggle.isOn.value)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun aTapFlipsAtOnceAndIsSent() = runTest {
        val toggle = toggle()
        toggle.known(false)

        toggle.toggle()
        assertEquals(true, toggle.isOn.value)
        runCurrent()
        toggle.toggle()
        runCurrent()

        assertEquals(false, toggle.isOn.value)
        assertEquals(listOf(true, false), sent)
        assertTrue(messages.isEmpty())
    }

    @Test
    fun aTapTheSourceRefusesFlipsBackAndSaysSo() = runTest {
        val toggle = toggle { throw IOException("Unreachable") }
        toggle.known(false)

        toggle.toggle()
        runCurrent()

        assertEquals(false, toggle.isOn.value)
        assertEquals(listOf("Couldn't turn it on"), messages)
    }

    @Test
    fun aFailureDoesNotUndoALaterTap() = runTest {
        val release = CompletableDeferred<Unit>()
        val toggle = toggle { on ->
            sent += on
            if (on) {
                release.await()
                throw IOException("Unreachable")
            }
        }
        toggle.known(false)

        toggle.toggle()
        runCurrent()
        // Taken back while the first tap is on its way
        toggle.toggle()
        release.complete(Unit)
        runCurrent()

        assertEquals(false, toggle.isOn.value)
        // Sent in their order, the second after the first failed
        assertEquals(listOf(true, false), sent)
        assertEquals(listOf("Couldn't turn it on"), messages)
    }
}
