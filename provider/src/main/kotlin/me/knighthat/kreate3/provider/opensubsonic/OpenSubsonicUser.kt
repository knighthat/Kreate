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

package me.knighthat.kreate3.provider.opensubsonic

import android.net.Uri

/**
 * The account the app is signed in with on the OpenSubsonic server, as `getUser` says it.
 *
 * @property name the username
 * @property email `null` when the server doesn't say, Navidrome doesn't
 * @property avatar address of the user's picture without the sign in, `null` when the server
 * can't be reached by an address. Like a cover it must be loaded through the provider, see
 * [OpenSubsonicProvider.isCoverUri], and the server may answer with a stock picture.
 * @property roles what the user may do, the `…Role` flags the server set, without the suffix:
 * `admin`, `download`, `share`, `stream`...
 */
data class OpenSubsonicUser(
    val name: String,
    val email: String?,
    val avatar: Uri?,
    val roles: Set<String>,
)
