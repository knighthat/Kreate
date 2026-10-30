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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import me.knighthat.kreate3.provider.opensubsonic.ServerCheck
import me.knighthat.kreate3.provider.opensubsonic.ServerException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class LiveDetailViewModelTest {

    /** The main thread and the fetch's, so every step happens right away. */
    private val dispatcher = UnconfinedTestDispatcher()

    /** An item of songs, their ids between commas. */
    private inner class Detail(
        disliked: Flow<Set<String>> = flowOf(emptySet()),
        var fetch: suspend () -> String?,
    ) : LiveDetailViewModel<String>(RuntimeEnvironment.getApplication(), disliked, dispatcher) {
        override val notFound = "Not here"
        override suspend fun fetch(): String? = fetch.invoke()
        override fun String.withoutSongs(disliked: Set<String>) =
            split(",").filterNot { it in disliked }.joinToString(",")
    }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun failureOf(error: Exception): LiveState<String> =
        Detail { throw error }.apply { load() }.state.value

    @Test
    fun itIsLoadingUntilTheFetchIsDone() {
        val done = CompletableDeferred<String>()
        val detail = Detail { done.await() }

        assertEquals(LiveState.Loading, detail.state.value)
        detail.load()
        assertEquals(LiveState.Loading, detail.state.value)

        done.complete("Album")
        assertEquals(LiveState.Ready("Album"), detail.state.value)
    }

    @Test
    fun nothingFoundSaysSo() {
        val detail = Detail { null }.apply { load() }

        assertEquals(LiveState.Failed("Not here"), detail.state.value)
    }

    @Test
    fun eachServerProblemIsExplained() {
        assertEquals(
            LiveState.Failed("OpenSubsonic API refused: Not found"),
            failureOf(ServerException(ServerCheck.Rejected(70, "Not found")))
        )
        assertEquals(
            LiveState.Failed("OpenSubsonic API refused: error 40"),
            failureOf(ServerException(ServerCheck.Rejected(40, "")))
        )
        assertEquals(
            LiveState.Failed("Can't reach OpenSubsonic API: timeout"),
            failureOf(ServerException(ServerCheck.Unreachable("timeout")))
        )
        assertEquals(
            LiveState.Failed("OpenSubsonic API's certificate isn't trusted"),
            failureOf(ServerException(ServerCheck.UntrustedCertificate("no anchor")))
        )
        assertEquals(
            LiveState.Failed("OpenSubsonic API's certificate changed, check it in Network settings"),
            failureOf(ServerException(ServerCheck.CertificateChanged("host", "NEW", "OLD")))
        )
        assertEquals(
            LiveState.Failed("OpenSubsonic API sent something unexpected"),
            failureOf(ServerException(ServerCheck.NotSubsonic))
        )
        assertEquals(
            LiveState.Failed("Sign in to OpenSubsonic API in Network settings"),
            failureOf(ServerException(ServerCheck.NotConfigured))
        )
    }

    @Test
    fun aFailedLoadCanBeTriedAgain() {
        val detail = Detail { throw ServerException(ServerCheck.NotSubsonic) }
        detail.load()

        detail.fetch = { "Album" }
        detail.load()

        assertEquals(LiveState.Ready("Album"), detail.state.value)
    }

    @Test
    fun songsDislikedWhileItShowsLeaveIt() {
        val disliked = MutableStateFlow(setOf("a"))
        val detail = Detail(disliked) { "a,b,c" }.apply { load() }
        assertEquals(LiveState.Ready("b,c"), detail.state.value)

        disliked.value = setOf("a", "c")
        assertEquals(LiveState.Ready("b"), detail.state.value)

        // Taken back, it shows again
        disliked.value = emptySet()
        assertEquals(LiveState.Ready("a,b,c"), detail.state.value)
    }

    @Test
    fun aNewLoadReplacesTheOneRunning() {
        val first = CompletableDeferred<String>()
        val detail = Detail { first.await() }
        detail.load()

        val second = CompletableDeferred<String>()
        detail.fetch = { second.await() }
        detail.load()
        // The first one was cancelled, it can't end the second
        first.complete("Too late")
        assertEquals(LiveState.Loading, detail.state.value)

        second.complete("Album")
        assertEquals(LiveState.Ready("Album"), detail.state.value)
    }
}
