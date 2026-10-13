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

package me.knighthat.kreate.preferences

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Every secret of the app, like passwords and API keys. Values belong to the active profile.
 *
 * Anything that lets someone sign in as the user goes here, never in [Preferences]. Names
 * are stored on disk, never rename a secret.
 */
object Secrets : KoinComponent {

    private val store: SecretStore by inject()

    /** Password of the OpenSubsonic server, used when [Preferences.OPEN_SUBSONIC_AUTH] is `PASSWORD`. */
    val OPEN_SUBSONIC_PASSWORD by lazy { store.secret("open_subsonic_password") }

    /** Replaces the username and password, used when [Preferences.OPEN_SUBSONIC_AUTH] is `API_KEY`. */
    val OPEN_SUBSONIC_API_KEY by lazy { store.secret("open_subsonic_api_key") }
}
