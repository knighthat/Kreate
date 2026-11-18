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

package me.knighthat.kreate3.ui.screen.settings.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.knighthat.kreate3.api.PatreonFeature
import me.knighthat.kreate3.api.PatreonSignedOutException
import me.knighthat.kreate3.api.PatreonTierException
import me.knighthat.kreate3.api.RemoteFile
import me.knighthat.kreate3.patreon.PatreonAccount
import me.knighthat.kreate3.patreon.PatreonSession
import me.knighthat.kreate3.patreon.RemoteBackups
import java.io.IOException

private const val TAG = "RemoteBackups"

/** What the Remote tab of the Backup page shows. */
sealed interface RemoteBackupsState {

    data object SignedOut : RemoteBackupsState

    /** The tier doesn't have cloud backup. */
    data object Locked : RemoteBackupsState

    data object Loading : RemoteBackupsState

    /** The list couldn't be had, it may be tried again. */
    data object Failed : RemoteBackupsState

    /** @property files the newest first */
    data class Loaded(val files: List<RemoteFile>) : RemoteBackupsState
}

/**
 * The backups the user keeps online for the Remote tab, created when it opens: [RemoteBackups]
 * asks for them only when its token changed since (one request per page of 1000 files, the server
 * keeps 50 at most), again on [retry] after a failure.
 */
class RemoteBackupsViewModel(
    private val account: PatreonAccount,
    session: PatreonSession,
    private val backups: RemoteBackups,
) : ViewModel() {

    private val _state = MutableStateFlow<RemoteBackupsState>(RemoteBackupsState.Loading)
    private var loading: Job? = null

    val state: StateFlow<RemoteBackupsState> = _state.asStateFlow()

    /** Whether the membership is being asked again, see [checkMembership]. */
    val checking: StateFlow<Boolean> = account.checking

    init {
        viewModelScope.launch {
            // What keeps the list from loading, null when nothing does
            session.member
                .map {
                    when {
                        it == null -> RemoteBackupsState.SignedOut
                        !it.claims.has(PatreonFeature.BACKUPS) -> RemoteBackupsState.Locked
                        else -> null
                    }
                }
                .distinctUntilChanged()
                .collect { gate ->
                    loading?.cancel()
                    if (gate != null)
                        _state.value = gate
                    else
                        load()
                }
        }
    }

    /**
     * Asks for the tier again while it seems to lack cloud backup, the pledge may have just
     * changed: the list loads once the new token has it.
     */
    fun checkMembership() = account.checkMembership()

    /** Asks for the list again after it failed. */
    fun retry() {
        if (_state.value == RemoteBackupsState.Failed) load()
    }

    private fun load() {
        loading?.cancel()
        _state.value = RemoteBackupsState.Loading
        loading = viewModelScope.launch {
            _state.value = try {
                RemoteBackupsState.Loaded(backups.list())
            } catch (e: CancellationException) {
                throw e
            } catch (e: PatreonTierException) {
                Logger.i(tag = TAG) { "The tier has no cloud backup: ${e.patronStatus}" }
                RemoteBackupsState.Locked
            } catch (_: PatreonSignedOutException) {
                RemoteBackupsState.SignedOut
            } catch (e: IOException) {
                Logger.w(e, TAG) { "Couldn't list the online backups" }
                RemoteBackupsState.Failed
            }
        }
    }
}
