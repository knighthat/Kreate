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

package me.knighthat.kreate3.preferences

import me.knighthat.kreate3.preferences.Secrets.YOUTUBE_COOKIES
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Every secret of the app, like passwords, API keys and account names. Values belong to the
 * active profile.
 *
 * Anything that lets someone sign in as the user, or tells who they are on a service, goes here,
 * never in [Preferences]. Names are stored on disk, never rename a secret.
 */
object Secrets : KoinComponent {

    private val store: SecretStore by inject()

    /** Username on the OpenSubsonic server, used when [Preferences.OPEN_SUBSONIC_AUTH] is `PASSWORD`. */
    val OPEN_SUBSONIC_USERNAME by lazy { store.secret("open_subsonic_username") }

    /** Password of the OpenSubsonic server, used when [Preferences.OPEN_SUBSONIC_AUTH] is `PASSWORD`. */
    val OPEN_SUBSONIC_PASSWORD by lazy { store.secret("open_subsonic_password") }

    /** Replaces the username and password, used when [Preferences.OPEN_SUBSONIC_AUTH] is `API_KEY`. */
    val OPEN_SUBSONIC_API_KEY by lazy { store.secret("open_subsonic_api_key") }

    /**
     * Cookies of the YouTube session the user signed in to, as a `Cookie` header. Empty while
     * nobody is signed in. They are a whole Google session, keep them out of logs and addresses.
     */
    val YOUTUBE_COOKIES by lazy { store.secret("youtube_cookies") }

    /**
     * Page id of the YouTube channel the signed in user acts as, when their Google account has
     * several (brand accounts). Empty for the account's own channel. It only picks a channel, the
     * session itself is [YOUTUBE_COOKIES].
     */
    val YOUTUBE_PAGE_ID by lazy { store.secret("youtube_page_id") }
}
