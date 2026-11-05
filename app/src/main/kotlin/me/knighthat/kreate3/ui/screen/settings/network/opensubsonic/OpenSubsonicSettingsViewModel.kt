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

package me.knighthat.kreate3.ui.screen.settings.network.opensubsonic

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import me.knighthat.kreate3.preferences.Preferences
import me.knighthat.kreate3.provider.opensubsonic.CheckState
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicLibrary
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicProvider
import me.knighthat.kreate3.provider.opensubsonic.OpenSubsonicServer
import me.knighthat.kreate3.provider.opensubsonic.ServerCheck

/**
 * The checks of the OpenSubsonic server as the user sets it up, kept by [OpenSubsonicServer]:
 * it pings the server as soon as it is on and has an address, and checks the credentials once
 * they are set and the ping succeeded. Every change of the settings, the proxy too, starts the
 * checks over.
 */
class OpenSubsonicSettingsViewModel(
    private val provider: OpenSubsonicProvider,
    private val library: OpenSubsonicLibrary,
) : ViewModel() {

    /** Whether a server answers at the address. */
    val server: StateFlow<CheckState> = OpenSubsonicServer.server

    /** Whether the server accepts the credentials, [CheckState.Idle] until the ping succeeds. */
    val credentials: StateFlow<CheckState> = OpenSubsonicServer.credentials

    /** Pings the server again, and checks the credentials again if it answers. */
    fun retry() = OpenSubsonicServer.refresh()

    /** Trusts the server's new certificate and checks again. */
    fun trust(change: ServerCheck.CertificateChanged) {
        provider.trust(change)
        retry()
    }

    /**
     * Syncs the starred songs from now on, right away, or stops when [on] is `false`. Turning
     * them off only stops the syncing.
     */
    fun setSongSync(on: Boolean) {
        Preferences.OPEN_SUBSONIC_SYNC_SONGS.update(on)
        if (on) library.refreshNow()
    }

    /** Forgets the trusted certificate, the next one the server presents is trusted. */
    fun forgetCertificate() {
        provider.forgetCertificate()
        retry()
    }
}
