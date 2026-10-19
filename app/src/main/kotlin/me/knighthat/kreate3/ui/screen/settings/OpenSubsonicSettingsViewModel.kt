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

package me.knighthat.kreate3.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import me.knighthat.kreate3.constant.OpenSubsonicAuth
import me.knighthat.kreate3.constant.SyncMode
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.preferences.Secrets
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicLibrary
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.opensubsonic.ServerCheck

/**
 * Where a check of the server stands.
 */
sealed interface CheckState {

    /** Nothing to check: the server is off, or what the check needs is not set. */
    data object Idle : CheckState

    data object Checking : CheckState

    data class Done(val result: ServerCheck) : CheckState
}

/**
 * Checks the OpenSubsonic server as the user sets it up. The server is pinged as soon as it is
 * on and has an address, and the credentials are checked once they are set and the ping
 * succeeded. Every change of the settings starts the checks over.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OpenSubsonicSettingsViewModel(
    private val provider: OpenSubsonicProvider,
    private val library: OpenSubsonicLibrary,
) : ViewModel() {

    private val attempt = MutableStateFlow(0)

    /** Whether a server answers at the address. */
    val server: StateFlow<CheckState> =
        combine(
            Preferences.OPEN_SUBSONIC_ENABLED.state,
            Preferences.OPEN_SUBSONIC_URL.state,
            Preferences.OPEN_SUBSONIC_ALLOW_SELF_SIGNED.state,
            attempt,
        ) { enabled, url, _, _ -> enabled && url.isNotBlank() }
            .transformLatest { ready ->
                if (!ready) emit(CheckState.Idle)
                else {
                    emit(CheckState.Checking)
                    emit(CheckState.Done(provider.ping()))
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CheckState.Idle)

    /** Whether the server accepts the credentials, [CheckState.Idle] until the ping succeeds. */
    val credentials: StateFlow<CheckState> =
        combine(server, credentialsSet()) { server, set ->
            // A new ping goes through Checking first, so every successful one checks again
            ((server as? CheckState.Done)?.result is ServerCheck.Success) to set
        }
            .distinctUntilChanged()
            .transformLatest { (reachable, set) ->
                if (!reachable || set == null) emit(CheckState.Idle)
                else {
                    emit(CheckState.Checking)
                    emit(CheckState.Done(provider.checkCredentials()))
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CheckState.Idle)

    /** Pings the server again, and checks the credentials again if it answers. */
    fun retry() = attempt.update { it + 1 }

    /** Trusts the server's new certificate and checks again. */
    fun trust(change: ServerCheck.CertificateChanged) {
        provider.trust(change)
        retry()
    }

    /**
     * Syncs the starred songs as [mode] says from now on, right away unless it is
     * [SyncMode.OFF]. Turning them off only stops the syncing.
     */
    fun setSongSync(mode: SyncMode) {
        Preferences.OPEN_SUBSONIC_SYNC_SONGS.update(mode)
        if (mode != SyncMode.OFF) library.refreshNow()
    }

    /** Forgets the trusted certificate, the next one the server presents is trusted. */
    fun forgetCertificate() {
        provider.forgetCertificate()
        retry()
    }

    /**
     * The credentials of the chosen method, `null` while any of them is missing. A change of
     * them is a new value, which starts a new check.
     */
    private fun credentialsSet(): Flow<List<String>?> =
        combine(
            Preferences.OPEN_SUBSONIC_AUTH.state,
            Secrets.OPEN_SUBSONIC_USERNAME.state,
            Secrets.OPEN_SUBSONIC_PASSWORD.state,
            Secrets.OPEN_SUBSONIC_API_KEY.state,
        ) { auth, username, password, apiKey ->
            when (auth) {
                OpenSubsonicAuth.PASSWORD -> listOf(username, password)
                OpenSubsonicAuth.API_KEY -> listOf(apiKey)
            }.takeIf { values -> values.none(String::isEmpty) }
        }
}
