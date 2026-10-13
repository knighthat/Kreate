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

package me.knighthat.kreate.ui.screen.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.knighthat.kreate.provider.opensubsonic.ServerCheck
import me.knighthat.kreate.provider.opensubsonic.ServerException

/**
 * Where bringing an item of a server into the database stands.
 */
sealed interface SyncState {

    data object Syncing : SyncState

    /** What is stored is up to date, or the server is off and what is stored is all there is. */
    data object Ready : SyncState

    data class Failed(val reason: String) : SyncState
}

/**
 * Brings an item of a server into the database when its screen opens, see [SyncedDetailScreen].
 * Subclasses call [sync] once from their `init`, after their own properties are set.
 */
abstract class SyncDetailViewModel : ViewModel() {

    private val _state = MutableStateFlow<SyncState>(SyncState.Syncing)
    private var job: Job? = null

    val state: StateFlow<SyncState> = _state.asStateFlow()

    /** Fetches the item and stores it, `false` when the server is off. */
    protected abstract suspend fun fetchAndStore(): Boolean

    /** Starts over, also after a failure. */
    fun sync() {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = SyncState.Syncing
            _state.value = try {
                fetchAndStore()
                SyncState.Ready
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SyncState.Failed(e.syncFailure())
            }
        }
    }

    private fun Throwable.syncFailure(): String =
        when (val check = (this as? ServerException)?.check) {
            is ServerCheck.Rejected -> "The server refused: ${check.message.ifEmpty { "error ${check.code}" }}"
            is ServerCheck.Unreachable -> "Can't reach the server: ${check.reason}"
            is ServerCheck.UntrustedCertificate -> "The server's certificate isn't trusted"
            is ServerCheck.CertificateChanged -> "The server's certificate changed, check it in Network settings"
            ServerCheck.NotSubsonic -> "The server sent something unexpected"
            ServerCheck.NotConfigured -> "Sign in to the server in Network settings"
            else -> message ?: "Couldn't load this from the server"
        }
}

/**
 * A detail screen of a server's item: [content] shows once the item is brought up to date from
 * the server. Meanwhile there is a spinner, and when it fails the reason with a way to try again.
 */
@Composable
fun SyncedDetailScreen(
    title: String,
    onBack: () -> Unit,
    viewModel: SyncDetailViewModel,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state == SyncState.Ready) {
        content()
        return
    }

    DetailScaffold(title, onBack, modifier) { innerPadding ->
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
        ) {
            when (val current = state) {
                is SyncState.Failed -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = current.reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    FilledTonalButton(onClick = viewModel::sync) { Text("Try again") }
                }

                else -> CircularProgressIndicator()
            }
        }
    }
}
