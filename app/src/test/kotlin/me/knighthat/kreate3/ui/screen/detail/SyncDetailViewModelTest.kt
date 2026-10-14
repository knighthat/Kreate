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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import me.knighthat.kreate3.provider.opensubsonic.ServerCheck
import me.knighthat.kreate3.provider.opensubsonic.ServerException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SyncDetailViewModelTest {

    private class Detail(var fetch: suspend () -> Boolean) : SyncDetailViewModel() {
        override suspend fun fetchAndStore(): Boolean = fetch()
    }

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun failureOf(error: Exception): SyncState =
        Detail { throw error }.apply { sync() }.state.value

    @Test
    fun itIsSyncingUntilTheFetchIsDone() {
        val done = CompletableDeferred<Boolean>()
        val detail = Detail { done.await() }

        assertEquals(SyncState.Syncing, detail.state.value)
        detail.sync()
        assertEquals(SyncState.Syncing, detail.state.value)

        done.complete(true)
        assertEquals(SyncState.Ready, detail.state.value)
    }

    @Test
    fun eachServerProblemIsExplained() {
        assertEquals(
            SyncState.Failed("The server refused: Not found"),
            failureOf(ServerException(ServerCheck.Rejected(70, "Not found")))
        )
        assertEquals(
            SyncState.Failed("The server refused: error 40"),
            failureOf(ServerException(ServerCheck.Rejected(40, "")))
        )
        assertEquals(
            SyncState.Failed("Can't reach the server: timeout"),
            failureOf(ServerException(ServerCheck.Unreachable("timeout")))
        )
        assertEquals(
            SyncState.Failed("The server's certificate isn't trusted"),
            failureOf(ServerException(ServerCheck.UntrustedCertificate("no anchor")))
        )
        assertEquals(
            SyncState.Failed("The server's certificate changed, check it in Network settings"),
            failureOf(ServerException(ServerCheck.CertificateChanged("host", "NEW", "OLD")))
        )
        assertEquals(
            SyncState.Failed("The server sent something unexpected"),
            failureOf(ServerException(ServerCheck.NotSubsonic))
        )
        assertEquals(
            SyncState.Failed("Sign in to the server in Network settings"),
            failureOf(ServerException(ServerCheck.NotConfigured))
        )
    }

    @Test
    fun otherProblemsShowTheirMessage() {
        assertEquals(SyncState.Failed("Disk full"), failureOf(IllegalStateException("Disk full")))
        assertEquals(
            SyncState.Failed("Couldn't load this from the server"),
            failureOf(IllegalStateException())
        )
    }

    @Test
    fun aFailedSyncCanBeTriedAgain() {
        val detail = Detail { throw ServerException(ServerCheck.NotSubsonic) }
        detail.sync()

        detail.fetch = { true }
        detail.sync()

        assertEquals(SyncState.Ready, detail.state.value)
    }

    @Test
    fun aNewSyncReplacesTheOneRunning() {
        val first = CompletableDeferred<Boolean>()
        val detail = Detail { first.await() }
        detail.sync()

        val second = CompletableDeferred<Boolean>()
        detail.fetch = { second.await() }
        detail.sync()
        // The first one was cancelled, it can't end the second
        first.completeExceptionally(IllegalStateException("Too late"))
        assertEquals(SyncState.Syncing, detail.state.value)

        second.complete(true)
        assertEquals(SyncState.Ready, detail.state.value)
    }
}
