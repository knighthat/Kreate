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

package me.knighthat.kreate3.patreon

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import me.knighthat.kreate3.api.PatreonFeature
import me.knighthat.kreate3.api.PatreonFiles
import me.knighthat.kreate3.api.RemoteFile
import me.knighthat.kreate3.preferences.GlobalPreferences
import me.knighthat.kreate3.preferences.Secrets

/**
 * The backups kept online, asked only when the Remote tab of the Backup page opens and kept in
 * memory with the token they were asked with: they're asked again only once the token changed
 * (a refresh, another sign in), or after a failure.
 */
class RemoteBackups internal constructor(
    private val account: PatreonAccount,
    session: PatreonSession,
    private val files: PatreonFiles,
    scope: CoroutineScope,
) {

    private class Listed(val token: String, val files: List<RemoteFile>)

    @Volatile
    private var listed: Listed? = null

    /**
     * Whether cloud backup is on: signed in, the tier has [PatreonFeature.BACKUPS] and
     * [GlobalPreferences.CLOUD_BACKUP] is on.
     */
    val active: StateFlow<Boolean> =
        combine(session.member, GlobalPreferences.CLOUD_BACKUP.state) { member, on ->
            on && member?.claims?.has(PatreonFeature.BACKUPS) == true
        }.stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * The files, the newest first: those kept when the token is still the one they were asked
     * with, else asked again (see [PatreonAccount.withToken] for what it throws).
     */
    suspend fun list(): List<RemoteFile> {
        val token = Secrets.PATREON_TOKEN.current()
        listed?.takeIf { it.token == token }?.let { return it.files }

        return account.withToken { used ->
            files.list(used)
                .sortedByDescending { it.lastModified }
                .also { listed = Listed(used, it) }
        }
    }
}
